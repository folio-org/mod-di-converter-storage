package org.folio.exports;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.MatchExpression;
import org.folio.rest.jaxrs.model.MatchDetail;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;

import java.util.List;

/**
 * Handles match expressions and extracts values from MARC records.
 */
public class MatchExpressionHandler {
  private static final Logger LOGGER = LogManager.getLogger(MatchExpressionHandler.class);

  private final MarcCircularStream marcStream;

  public MatchExpressionHandler(MarcCircularStream marcStream) {
    this.marcStream = marcStream;
  }

  /**
   * Extracts a value from a match detail.
   * Handles different types of match expressions including static values and values from records.
   *
   * @param matchDetail    The match detail containing the expression
   * @param existingRecord
   * @return The extracted value
   */
  public String getValueFromMatchDetailOrExistingRecord(MatchDetail matchDetail, Record existingRecord) {
    if (matchDetail == null) {
      return generateRandomValue();
    }

    MatchExpression incomingExpression = matchDetail.getIncomingMatchExpression();
    if (incomingExpression == null) {
      return generateRandomValue();
    }

    // Handle different match expression types
    if (incomingExpression.getDataValueType() == null) {
      return generateRandomValue();
    }

    switch (incomingExpression.getDataValueType().value()) {
      case "VALUE_FROM_RECORD":
        return extractValueFromRecord(existingRecord, incomingExpression);
      case "STATIC_VALUE":
        return handleStaticValue(incomingExpression);
      default:
        return generateRandomValue();
    }
  }

  /**
   * Handles a static value match expression.
   *
   * @param expression The match expression
   * @return The static value
   */
  private String handleStaticValue(MatchExpression expression) {
    if (expression.getStaticValueDetails() != null) {
      return expression.getStaticValueDetails().toString();
    }
    return generateRandomValue();
  }

  /**
   * Extracts a value from a MARC record based on the match expression.
   *
   * @param record The MARC record
   * @param expression The match expression
   * @return The extracted value
   */
  private String extractValueFromRecord(Record record, MatchExpression expression) {
    List<org.folio.rest.jaxrs.model.Field> fields = expression.getFields();
    if (fields == null || fields.isEmpty()) {
      return generateRandomValue();
    }

    // Extract field tag, indicators, and subfield from the match expression
    String fieldTag = null;
    String indicator1 = null;
    String indicator2 = null;
    String subfieldCode = null;

    for (org.folio.rest.jaxrs.model.Field field : fields) {
      String label = field.getLabel();
      String value = field.getValue();

      if (label == null || value == null) {
        continue;
      }

      switch (label) {
        case "field":
          fieldTag = value;
          break;
        case "indicator1":
          indicator1 = value;
          break;
        case "indicator2":
          indicator2 = value;
          break;
        case "recordSubfield":
          subfieldCode = value;
          break;
      }
    }

    if (fieldTag == null) {
      return generateRandomValue();
    }

    // Extract the value from the record
    try {
      // Handle control fields (001-009)
      if (isControlField(fieldTag)) {
        ControlField controlField = (ControlField) record.getVariableField(fieldTag);
        if (controlField != null) {
          return controlField.getData();
        }
      }
      // Handle data fields with subfields and optional indicators
      else if (subfieldCode != null && !subfieldCode.isEmpty()) {
        // Create final copies of the variables for use in lambdas
        final String finalFieldTag = fieldTag;
        final String finalIndicator1 = indicator1;
        final String finalIndicator2 = indicator2;

        // Get all matching data fields
        List<DataField> matchingFields = record.getDataFields()
          .stream()
          .filter(df -> df.getTag().equals(finalFieldTag))
          .filter(df -> finalIndicator1 == null || String.valueOf(df.getIndicator1()).equals(finalIndicator1))
          .filter(df -> finalIndicator2 == null || String.valueOf(df.getIndicator2()).equals(finalIndicator2))
          .toList();

        // If we found matching fields, extract the subfield data
        if (!matchingFields.isEmpty()) {
          DataField dataField = matchingFields.get(0); // Use the first matching field
          Subfield subfield = dataField.getSubfield(subfieldCode.charAt(0));
          if (subfield != null) {
            return subfield.getData();
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Error extracting value from record: {}", e.getMessage());
    }

    return generateAppropriateValue(expression);
  }

  /**
   * Generates an appropriate value based on the match expression type.
   * Attempts to create a value that would make sense for the field.
   *
   * @param expression The match expression
   * @return A generated value
   */
  private String generateAppropriateValue(MatchExpression expression) {
    // Extract field tag from the expression
    String fieldTag = null;
    List<org.folio.rest.jaxrs.model.Field> fields = expression.getFields();

    if (fields != null) {
      for (org.folio.rest.jaxrs.model.Field field : fields) {
        if ("field".equals(field.getLabel())) {
          fieldTag = field.getValue();
          break;
        }
      }
    }

    // Generate appropriate test values based on common MARC fields
    if (fieldTag != null) {
      switch (fieldTag) {
        case "001":
          return "oc" + randomNumeric(9); // Control number
        case "020":
          return "978" + randomNumeric(10); // ISBN
        case "022":
          return randomNumeric(4) + "-" + randomNumeric(4); // ISSN
        case "245":
          return "Test Title " + randomAlphanumeric(20); // Title
        case "100":
          return "Author, Test " + randomAlpha(10); // Author
        case "260":
        case "264":
          return "Test Publisher, " + (2000 + Integer.parseInt(randomNumeric(2))); // Publication info
        case "300":
          return randomNumeric(3) + " p. ; " + randomNumeric(2) + " cm."; // Physical description
        case "650":
          return "Test Subject -- Subdivision"; // Subject term
        default:
          return generateRandomValue();
      }
    }

    return generateRandomValue();
  }

  /**
   * Checks if a field is a control field (001-009).
   *
   * @param field The field tag
   * @return true if it's a control field
   */
  private boolean isControlField(String field) {
    try {
      int fieldNum = Integer.parseInt(field);
      return fieldNum >= 1 && fieldNum <= 9;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Generates a random value for use when no specific value can be determined.
   *
   * @return A random string
   */
  private String generateRandomValue() {
    return "TestValue-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
  }

  /**
   * Generates a random numeric string of the specified length.
   *
   * @param length The length of the string
   * @return A random numeric string
   */
  private String randomNumeric(int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      sb.append((int) (Math.random() * 10));
    }
    return sb.toString();
  }

  /**
   * Generates a random alphabetic string of the specified length.
   *
   * @param length The length of the string
   * @return A random alphabetic string
   */
  private String randomAlpha(int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      char c = (char) ('a' + (int) (Math.random() * 26));
      sb.append(c);
    }
    return sb.toString();
  }

  /**
   * Generates a random alphanumeric string of the specified length.
   *
   * @param length The length of the string
   * @return A random alphanumeric string
   */
  private String randomAlphanumeric(int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      if (Math.random() < 0.7) {
        char c = (char) ('a' + (int) (Math.random() * 26));
        sb.append(c);
      } else {
        sb.append((int) (Math.random() * 10));
      }
    }
    return sb.toString();
  }
}
