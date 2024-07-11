package org.folio.recordgen;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class MARC008Generator {
  private static final String MATERIAL_TYPES = "abcdefghijklmnopqrstuvwxyz";
  private static final String LANGUAGES = "abcdefghijklmnopqrstuvwxyz";
  private static final String COUNTRIES = "abcdefghijklmnopqrstuvwxyz";

  private static String generateRandomMARCDate() {
    LocalDate randomDate = LocalDate.now().minusDays(new Random().nextInt(365 * 50));
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");
    return randomDate.format(formatter);
  }

  private static String generateRandomDateType() {
    String[] dateTypes = {"s", "c", "d", "e", "m", "n", "u"};
    return dateTypes[new Random().nextInt(dateTypes.length)];
  }

  private static String generateRandomYear() {
    int year = 1500 + new Random().nextInt(524); // Generates years between 1500 and 2023
    return String.format("%04d", year);
  }

  private static char generateRandomCode(String codes) {
    return codes.charAt(new Random().nextInt(codes.length()));
  }

  public static String generateRandom008() {
    StringBuilder marc008 = new StringBuilder();
    marc008.append(generateRandomMARCDate()); // 6 characters for date
    marc008.append(generateRandomDateType()); // 1 character for date type
    marc008.append(generateRandomYear()); // 4 characters for date 1
    marc008.append(generateRandomYear()); // 4 characters for date 2
    marc008.append(generateRandomCode(COUNTRIES)); // 3 characters for country
    marc008.append(generateRandomCode(LANGUAGES)); // 1 character for language
    marc008.append(generateRandomCode(MATERIAL_TYPES)); // 1 character for material type
    marc008.append(" 0 a0"); // 5 fixed characters
    marc008.append(generateRandomCode(LANGUAGES)); // 3 characters for language of cataloging
    marc008.append(generateRandomCode("abcdefghi")); // 1 character for successive/latest entry
    return marc008.toString();
  }
}
