package org.influxdb.impl;

import java.util.regex.Pattern;

/**
 * Functions for parameter validation.
 *
 * @author Simon Legner
 */
public final class Preconditions {

  private static final String DURATION_REGULAR_EXPRESSION = "(\\d+[wdmhs])+|inf";

  private Preconditions() {
  }

  /**
   * Enforces that the string is {@linkplain String#isEmpty() not empty}.
   * @param string the string to test
   * @param name variable name for reporting
   * @return {@code string}
   * @throws IllegalArgumentException if the string is empty
   */
  public static String checkNonEmptyString(final String string, final String name) throws IllegalArgumentException {
    if (string == null || string.isEmpty()) {
      throw new IllegalArgumentException("Expecting a non-empty string for " + name);
    }
    return string;
  }

  /**
   * Enforces that the number is larger than 0.
   * @param number the number to test
   * @param name variable name for reporting
   * @throws IllegalArgumentException if the number is less or equal to 0
   */
  public static void checkPositiveNumber(final Number number, final String name) throws IllegalArgumentException {
    if (number == null || number.doubleValue() <= 0) {
      throw new IllegalArgumentException("Expecting a positive number for " + name);
    }
  }

  /**
   * Enforces that the number is not negative.
   * @param number the number to test
   * @param name variable name for reporting
   * @throws IllegalArgumentException if the number is less or equal to 0
   */
  public static void checkNotNegativeNumber(final Number number, final String name) throws IllegalArgumentException {
    if (number == null || number.doubleValue() < 0) {
      throw new IllegalArgumentException("Expecting a positive or zero number for " + name);
    }
  }
  /**
   * Enforces that the duration is a valid influxDB duration.
   * @param duration the duration to test
   * @param name variable name for reporting
   * @throws IllegalArgumentException if the given duration is not valid.
   */
  public static void checkDuration(final String duration, final String name) throws IllegalArgumentException {
    if (!duration.matches(DURATION_REGULAR_EXPRESSION)) {
      throw new IllegalArgumentException("Invalid InfluxDB duration: " + duration
         + " for " + name);
    }
  }
}
