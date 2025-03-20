package org.folio.exports;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.http.FolioMarcClient;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.Record;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Provides a continuous stream of MARC records, either from a local file
 * or from a FOLIO instance.
 */
public class MarcCircularStream implements AutoCloseable {
  private static final Logger LOGGER = LogManager.getLogger(MarcCircularStream.class);

  private MarcReader reader;
  private String marcFilePath;
  private InputStream inputStream;
  private FolioMarcClient folioClient;
  private boolean usingFolio;
  private String repositoryPath;

  /**
   * Creates a circular stream using a local MARC file.
   *
   * @param marcFilePath Path to the MARC file
   * @throws IOException If there's an error reading the file
   */
  public MarcCircularStream(String marcFilePath) throws IOException {
    this.marcFilePath = marcFilePath;
    this.usingFolio = false;
    resetFileStream();
  }

  /**
   * Creates a circular stream using a FOLIO instance API.
   *
   * @param baseUrl The base URL of the FOLIO instance
   * @param token The authorization token
   */
  public MarcCircularStream(String baseUrl, String token) {
    this(baseUrl, token, null);
  }

  /**
   * Creates a circular stream using a FOLIO instance API with repository path.
   *
   * @param baseUrl The base URL of the FOLIO instance
   * @param token The authorization token
   * @param repositoryPath Path to repository for storing currentId
   */
  public MarcCircularStream(String baseUrl, String token, String repositoryPath) {
    this.repositoryPath = repositoryPath;
    this.folioClient = new FolioMarcClient(baseUrl, token, repositoryPath);
    this.usingFolio = true;
  }

  /**
   * Retrieves the next MARC record.
   * If no more records, automatically restarts from the beginning.
   *
   * @return Next MARC record
   * @throws IOException If there's an error reading records
   * @throws NoSuchElementException If no records are available
   */
  public Record nextRecord() throws IOException, NoSuchElementException {
    if (usingFolio) {
      return folioClient.getNextRecord();
    } else {
      return nextFileRecord();
    }
  }

  /**
   * Gets the next record from a file-based stream.
   */
  private Record nextFileRecord() throws IOException {
    // If no more records, reset the stream
    if (!reader.hasNext()) {
      resetFileStream();
    }

    // Check if we still have no records after reset
    if (!reader.hasNext()) {
      throw new NoSuchElementException("MARC file is empty");
    }

    // Return the next record
    return reader.next();
  }

  /**
   * Resets the file stream to the beginning of the file.
   *
   * @throws IOException If there's an error reopening the file
   */
  private void resetFileStream() throws IOException {
    // Close existing stream if open
    if (inputStream != null) {
      try {
        inputStream.close();
      } catch (IOException e) {
        LOGGER.warn("Error closing input stream: {}", e.getMessage());
      }
    }

    // Reopen the input stream
    inputStream = new FileInputStream(marcFilePath);

    // Create a new MARC reader
    reader = new MarcStreamReader(inputStream);
    LOGGER.info("Reset MARC file stream to beginning of file: {}", marcFilePath);
  }

  /**
   * Retrieves a specific MARC record by ID from FOLIO.
   * Only works when using FOLIO mode.
   *
   * @param recordId The record ID
   * @return Optional containing the MARC record, or empty if not found
   * @throws IllegalStateException If not in FOLIO mode
   */
  public Optional<Record> getRecordById(String recordId) {
    if (!usingFolio) {
      throw new IllegalStateException("Record retrieval by ID is only supported in FOLIO mode");
    }
    return folioClient.getRecordById(recordId);
  }

  /**
   * Closes the stream and releases resources.
   *
   * @throws IOException If there's an error closing the stream
   */
  @Override
  public void close() throws IOException {
    if (usingFolio && folioClient != null) {
      folioClient.close();
    } else if (inputStream != null) {
      inputStream.close();
    }
  }
}
