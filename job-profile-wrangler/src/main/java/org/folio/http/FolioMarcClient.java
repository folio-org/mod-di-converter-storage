package org.folio.http;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
  private static final String CURRENT_OFFSET_FILENAME = ".currentOffset";
  private static final int MAX_RECORDS = 100000; // Cap at 100,000 records

  private final String baseUrl;
  private final String token;
  private final OkHttpClient httpClient;
  private final String repositoryPath;

  // Cache to store retrieved records
  private final List<Record> recordCache = new ArrayList<>();
  private int currentRecordIndex = 0;
  private int currentOffset = 0;  // Absolute position in source records
  private int totalRecords = 0;
  private boolean endOfRecords = false;

  /**
   * Creates a new client to retrieve MARC records from FOLIO.
   *
   * @param baseUrl The base URL of the FOLIO instance
   * @param token The authorization token
   */
  public FolioMarcClient(String baseUrl, String token) {
    this(baseUrl, token, null);
  }

  /**
   * Creates a new client to retrieve MARC records from FOLIO with repository path.
   *
   * @param baseUrl The base URL of the FOLIO instance
   * @param token The authorization token
   * @param repositoryPath Path to repository for storing currentOffset
   */
  public FolioMarcClient(String baseUrl, String token, String repositoryPath) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.token = token;
    this.repositoryPath = repositoryPath;

    this.httpClient = new OkHttpClient.Builder()
      .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
      .readTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
      .writeTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
      .build();

    // Load the current offset from repository if available
    if (repositoryPath != null) {
      loadCurrentOffset();
    }
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
      // Before fetching, make sure currentOffset reflects our position
      currentOffset = currentOffset - currentRecordIndex + recordCache.size();
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

    // Get the next record from the cache
    Record record = recordCache.get(currentRecordIndex);

    // Increment both the index in the cache and the absolute position
    currentRecordIndex++;
    currentOffset++;

    // Save offset after each record if we have a repository path
    if (repositoryPath != null) {
      saveCurrentOffset();
    }

    // If we've gone through the current batch, prepare for the next one
    if (currentRecordIndex >= recordCache.size()) {
      currentRecordIndex = 0;

      // If we've reached the maximum, start back at the beginning
      if (currentOffset >= MAX_RECORDS) {
        LOGGER.info("Reached maximum offset of {}, resetting to 0", MAX_RECORDS);
        currentOffset = 0;
        if (repositoryPath != null) {
          saveCurrentOffset();
        }
      }
    }

    return record;
  }

  /**
   * Fetches a batch of records from FOLIO using offset-based pagination.
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
      .addQueryParameter("deleted", "false")
      .addQueryParameter("orderBy", "id,ASC")
      .build();

    Request request = new Request.Builder()
      .url(url)
      .header("Accept", "application/json")
      .header(OKAPI_TOKEN_HEADER, token)
      .get()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Failed to fetch MARC records: " + response.code() + " - " + response.message());
      }

      ResponseBody body = response.body();
      if (body == null) {
        throw new IOException("Empty response body");
      }

      JsonNode jsonResponse = OBJECT_MAPPER.readTree(body.string());
      processSourceRecordsResponse(jsonResponse);

      // Update pagination variables
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

    com.fasterxml.jackson.databind.node.ArrayNode sourceRecords =
      (com.fasterxml.jackson.databind.node.ArrayNode) jsonResponse.get("sourceRecords");

    // Clear the cache before adding new records
    recordCache.clear();

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

    LOGGER.info("Added {} MARC records to cache, current offset: {}", recordCache.size(), currentOffset);
    currentRecordIndex = 0; // Reset the index to start from the beginning of the new batch
  }

  /**
   * Loads the current offset from the repository.
   */
  private void loadCurrentOffset() {
    Path currentOffsetFile = Paths.get(repositoryPath, CURRENT_OFFSET_FILENAME);
    if (Files.exists(currentOffsetFile)) {
      try {
        String offsetStr = Files.readString(currentOffsetFile).trim();
        this.currentOffset = Integer.parseInt(offsetStr);
        LOGGER.info("Loaded currentOffset: {} from {}", currentOffset, currentOffsetFile);
      } catch (IOException | NumberFormatException e) {
        LOGGER.warn("Failed to read currentOffset file: {}", e.getMessage());
        this.currentOffset = 0;
      }
    }
  }

  /**
   * Saves the current offset to the repository.
   */
  private void saveCurrentOffset() {
    if (repositoryPath == null) {
      return;
    }

    Path currentOffsetFile = Paths.get(repositoryPath, CURRENT_OFFSET_FILENAME);
    try {
      Files.writeString(currentOffsetFile, String.valueOf(currentOffset));
      LOGGER.debug("Saved currentOffset: {} to {}", currentOffset, currentOffsetFile);
    } catch (IOException e) {
      LOGGER.warn("Failed to write currentOffset file: {}", e.getMessage());
    }
  }

  /**
   * Resets the pagination variables. Instead of starting from the beginning,
   * we cap at the maximum number of records to consider.
   */
  private void resetPagination() {
    // If we've reached the end, start over from offset 0
    if (currentOffset >= MAX_RECORDS) {
      currentOffset = 0;
    }

    currentRecordIndex = 0;
    totalRecords = 0;
    endOfRecords = false;
    recordCache.clear();

    if (repositoryPath != null) {
      saveCurrentOffset();
    }
  }

  /**
   * Retrieves a specific MARC record by ID from FOLIO.
   *
   * @param recordId The record ID
   * @return Optional containing the MARC record, or empty if not found
   */
  public Optional<Record> getRecordById(String recordId) {
    try {
      HttpUrl url = HttpUrl.parse(baseUrl + "/source-storage/source-records/" + recordId)
        .newBuilder()
        .addQueryParameter("idType", "RECORD")
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
   * Closes the client and releases resources.
   */
  public void close() {
    // Save offset one last time before closing
    if (repositoryPath != null) {
      saveCurrentOffset();
    }

    httpClient.dispatcher().executorService().shutdown();
    httpClient.connectionPool().evictAll();
  }
}
