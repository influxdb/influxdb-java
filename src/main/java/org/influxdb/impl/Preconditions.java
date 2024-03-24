package org.influxdb.impl;

import java.net.URL;
import java.net.MalformedURLException;

/**
 * Functions for parameter validation.
 *
 * @author Simon Legner
 */
public final class Preconditions {

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
    if (!duration.matches("(\\d+[wdmhs])+|inf")) {
      throw new IllegalArgumentException("Invalid InfluxDB duration: " + duration
         + " for " + name);
    }
  }

  /**
   * Check url is legal
   * @param url the server url
   * @throws IllegalArgumentException if the port of url is miss
   */
  public static void checkHasPort(final String url) throws IllegalArgumentException{
    String colon = ":";
    if (!url.contains(colon) || url.endsWith(colon)) {
      throw new IllegalArgumentException(String.format("The url [%s] port cannot be null", url));
    }
    try {
      URL urlObj = new URL(url);
      if (-1 == urlObj.getPort()) {
        throw new IllegalArgumentException(String.format("The url [%s] port cannot be null", url));
      }
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
