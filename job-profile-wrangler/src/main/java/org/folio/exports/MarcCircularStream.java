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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
  private int currentRecordPosition = 0;

  /**
   * Creates a circular stream using a local MARC file.
   *
   * @param marcFilePath Path to the MARC file
   * @throws IOException If there's an error reading the file
   */
  public MarcCircularStream(String marcFilePath) throws IOException {
    this.marcFilePath = marcFilePath;
    this.usingFolio = false;
  }

  /**
   * Creates a circular stream using a local MARC file with repository support.
   *
   * @param marcFilePath Path to the MARC file
   * @param repositoryPath Path to the repository for storing offsets
   * @throws IOException If there's an error reading the file
   */
  public MarcCircularStream(String marcFilePath, String repositoryPath) throws IOException {
    this.marcFilePath = marcFilePath;
    this.repositoryPath = repositoryPath;
    this.usingFolio = false;

    // Initialize the MARC reader
    inputStream = new FileInputStream(marcFilePath);
    reader = new MarcStreamReader(inputStream);

    // Load the current position from the repository if available
    loadCurrentPosition();
  }

  /**
   * Creates a circular stream using a FOLIO instance API.
   * Added boolean parameter to differentiate from the local file constructor.
   *
   * @param baseUrl The base URL of the FOLIO instance
   * @param token The authorization token
   * @param useFolio Boolean flag (not used for logic, just to differentiate signature)
   */
  public MarcCircularStream(String baseUrl, String token, boolean useFolio) {
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

    // Save the current position if repository path is provided
    if (repositoryPath != null) {
      Path currentOffsetFile = Paths.get(repositoryPath, ".currentMarcOffset");
      try {
        Files.writeString(currentOffsetFile, "0"); // Reset to beginning
        LOGGER.info("Reset MARC file offset to beginning of file: {}", marcFilePath);
      } catch (IOException e) {
        LOGGER.warn("Failed to write currentOffset file: {}", e.getMessage());
      }
    }

    // Reset the position counter
    currentRecordPosition = 0;
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
      Record record = nextFileRecord();
      currentRecordPosition++;

      // Save position if we have a repository path
      if (repositoryPath != null) {
        saveCurrentPosition();
      }

      return record;
    }
  }

  private void saveCurrentPosition() {
    if (repositoryPath == null) {
      return;
    }

    Path currentOffsetFile = Paths.get(repositoryPath, ".currentMarcOffset");
    try {
      Files.writeString(currentOffsetFile, String.valueOf(currentRecordPosition));
      LOGGER.debug("Saved currentMarcOffset: {} to {}", currentRecordPosition, currentOffsetFile);
    } catch (IOException e) {
      LOGGER.warn("Failed to write currentMarcOffset file: {}", e.getMessage());
    }
  }

  private void loadCurrentPosition() {
    if (repositoryPath == null) {
      return;
    }

    Path currentOffsetFile = Paths.get(repositoryPath, ".currentMarcOffset");
    if (Files.exists(currentOffsetFile)) {
      try {
        String offsetStr = Files.readString(currentOffsetFile).trim();
        int savedPosition = Integer.parseInt(offsetStr);

        // Skip ahead to the saved position
        for (int i = 0; i < savedPosition && reader.hasNext(); i++) {
          reader.next();
          currentRecordPosition++;
        }

        LOGGER.info("Resumed from position {} in MARC file", currentRecordPosition);
      } catch (IOException | NumberFormatException e) {
        LOGGER.warn("Failed to read currentMarcOffset file: {}", e.getMessage());
      }
    }
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
   * Checks if this stream is using FOLIO as its source.
   *
   * @return true if using FOLIO, false if using a local file
   */
  public boolean isUsingFolio() {
    return usingFolio;
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
