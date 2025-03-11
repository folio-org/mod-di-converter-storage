package org.folio.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.marc4j.MarcJsonReader;
import org.marc4j.marc.Record;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.folio.Constants.OBJECT_MAPPER;
import static org.folio.Constants.OKAPI_TOKEN_HEADER;

/**
 * Client for retrieving MARC records from a FOLIO instance.
 */
public class FolioMarcClient {
  private static final Logger LOGGER = LogManager.getLogger(FolioMarcClient.class);
  private static final int PAGE_SIZE = 50;
  private static final int CONNECTION_TIMEOUT = 30;

  private final String baseUrl;
  private final String token;
  private final OkHttpClient httpClient;

  // Cache to store retrieved records
  private final List<Record> recordCache = new ArrayList<>();
  private int currentRecordIndex = 0;
  private int totalRecords = 0;
  private int currentOffset = 0;
  private boolean endOfRecords = false;

  /**
   * Creates a new client to retrieve MARC records from FOLIO.
   *
   * @param baseUrl The base URL of the FOLIO instance
   * @param token The authorization token
   */
  public FolioMarcClient(String baseUrl, String token) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.token = token;

    this.httpClient = new OkHttpClient.Builder()
      .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
      .readTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
      .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
      .build();
  }

  /**
   * Gets the next MARC record from FOLIO.
   * Retrieves a batch of records if the cache is empty.
   *
   * @return The next MARC record
   * @throws IOException If an error occurs during the API call
   */
  public synchronized Record getNextRecord() throws IOException {
    // If we've reached the end of the cache, fetch more records
    if (currentRecordIndex >= recordCache.size() && !endOfRecords) {
      fetchMoreRecords();
    }

    // If the cache is still empty, we've exhausted all records - start over
    if (recordCache.isEmpty()) {
      resetPagination();
      fetchMoreRecords();

      // If still empty, there are no records in the system
      if (recordCache.isEmpty()) {
        throw new IOException("No MARC records found in FOLIO instance");
      }
    }

    // Get the next record from the cache, wrapping around if necessary
    Record record = recordCache.get(currentRecordIndex % recordCache.size());
    currentRecordIndex++;

    // If we've gone through all records, reset to start
    if (currentRecordIndex >= totalRecords && totalRecords > 0) {
      resetPagination();
    }

    return record;
  }

  /**
   * Fetches a batch of records from FOLIO and adds them to the cache.
   *
   * @throws IOException If an error occurs during the API call
   */
  private void fetchMoreRecords() throws IOException {
    LOGGER.info("Fetching MARC records from FOLIO, offset: {}, limit: {}", currentOffset, PAGE_SIZE);

    HttpUrl url = HttpUrl.parse(baseUrl + "/source-storage/source-records")
      .newBuilder()
      .addQueryParameter("offset", String.valueOf(currentOffset))
      .addQueryParameter("limit", String.valueOf(PAGE_SIZE))
      .addQueryParameter("recordType", "MARC_BIB")
      .build();

    Request request = new Request.Builder()
      .url(url)
      .header("Accept", "application/json")
      .header(OKAPI_TOKEN_HEADER, token)
      .get()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to fetch MARC records: " + response.code());
      }

      ResponseBody body = response.body();
      if (body == null) {
        throw new IOException("Empty response body");
      }

      JsonNode jsonResponse = OBJECT_MAPPER.readTree(body.string());
      processSourceRecordsResponse(jsonResponse);

      // Update pagination variables
      currentOffset += PAGE_SIZE;
      if (jsonResponse.has("totalRecords")) {
        totalRecords = jsonResponse.get("totalRecords").asInt();
        if (currentOffset >= totalRecords) {
          endOfRecords = true;
        }
      }
    }
  }

  /**
   * Processes the source-records API response and extracts MARC records.
   *
   * @param jsonResponse The API response as a JsonNode
   * @throws IOException If an error occurs during processing
   */
  private void processSourceRecordsResponse(JsonNode jsonResponse) throws IOException {
    if (!jsonResponse.has("sourceRecords") || !jsonResponse.get("sourceRecords").isArray()) {
      LOGGER.warn("Invalid response format - no sourceRecords array found");
      return;
    }

    ArrayNode sourceRecords = (ArrayNode) jsonResponse.get("sourceRecords");
    for (JsonNode sourceRecord : sourceRecords) {
      if (sourceRecord.has("parsedRecord") &&
        sourceRecord.get("parsedRecord").has("content")) {

        // Extract the MARC record JSON
        JsonNode marcJson = sourceRecord.get("parsedRecord").get("content");
        String marcJsonString = OBJECT_MAPPER.writeValueAsString(marcJson);

        // Use MarcJsonReader to convert JSON to MARC record
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(marcJsonString.getBytes())) {
          MarcJsonReader reader = new MarcJsonReader(inputStream);
          if (reader.hasNext()) {
            Record record = reader.next();
            recordCache.add(record);
          }
        } catch (Exception e) {
          LOGGER.warn("Failed to parse MARC record: {}", e.getMessage());
        }
      }
    }

    LOGGER.info("Added {} MARC records to cache", sourceRecords.size());
  }

  /**
   * Retrieves a specific MARC record by ID from FOLIO.
   *
   * @param recordId The record ID
   * @return Optional containing the MARC record, or empty if not found
   */
  public Optional<Record> getRecordById(String recordId) {
    try {
      HttpUrl url = HttpUrl.parse(baseUrl + "/source-storage/records/" + recordId)
        .newBuilder()
        .build();

      Request request = new Request.Builder()
        .url(url)
        .header("Accept", "application/json")
        .header(OKAPI_TOKEN_HEADER, token)
        .get()
        .build();

      try (Response response = httpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          LOGGER.warn("Failed to fetch MARC record by ID {}: {}", recordId, response.code());
          return Optional.empty();
        }

        ResponseBody body = response.body();
        if (body == null) {
          return Optional.empty();
        }

        JsonNode jsonResponse = OBJECT_MAPPER.readTree(body.string());

        if (jsonResponse.has("parsedRecord") &&
          jsonResponse.get("parsedRecord").has("content")) {

          // Extract the MARC record JSON
          JsonNode marcJson = jsonResponse.get("parsedRecord").get("content");
          String marcJsonString = OBJECT_MAPPER.writeValueAsString(marcJson);

          // Use MarcJsonReader to convert JSON to MARC record
          try (ByteArrayInputStream inputStream = new ByteArrayInputStream(marcJsonString.getBytes())) {
            MarcJsonReader reader = new MarcJsonReader(inputStream);
            if (reader.hasNext()) {
              return Optional.of(reader.next());
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error retrieving record by ID {}: {}", recordId, e.getMessage());
    }

    return Optional.empty();
  }

  /**
   * Resets the pagination variables to start from the beginning.
   */
  private void resetPagination() {
    currentOffset = 0;
    currentRecordIndex = 0;
    endOfRecords = false;
    recordCache.clear();
  }

  /**
   * Closes the client and releases resources.
   */
  public void close() {
    httpClient.dispatcher().executorService().shutdown();
    httpClient.connectionPool().evictAll();
  }
}
