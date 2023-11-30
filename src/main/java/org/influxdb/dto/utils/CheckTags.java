package org.influxdb.dto.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class intended for internal use. Checks the format of tag names and values to see if they
 * conform to a regular expression.
 *
 * @author Brenton Poke
 */
public final class CheckTags {
  static final String NAMEREGEX = "[^\r\n]+";
  static final Pattern NAMEPATTERN = Pattern.compile(NAMEREGEX);

  private CheckTags() { }
  /**
   * Check a single tag's name according to the corresponding regular expression.
   *
   * @param name the tag name
   * @return Boolean indicating that the tag name is legal
   */
  public static Boolean isTagNameLegal(final String name) {
    final Matcher matcher = NAMEPATTERN.matcher(name);
    return matcher.matches();
  }
  /**
   * Check a single tag's name according to the corresponding regular expression.
   *
   * @param value the tag value
   * @return Boolean indicating that the tag value is legal
   */
  public static Boolean isLegalFullCheck(final String name, final String value) {
    return !name.isEmpty() && !value.isEmpty() && CheckTags.isTagNameLegal(name);
  }
}
