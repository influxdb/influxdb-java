package org.influxdb.dto;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.influxdb.BuilderException;
import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.annotation.TimeColumn;
import org.influxdb.dto.utils.CheckTags;
import org.influxdb.impl.Preconditions;

/**
 * Representation of a InfluxDB database Point.
 *
 * @author stefan.majer [at] gmail.com
 *
 */
public class Point {
  private String measurement;
  private Map<String, String> tags;
  private Number time;
  private TimeUnit precision = TimeUnit.NANOSECONDS;
  private Map<String, Object> fields;
  private static final int MAX_FRACTION_DIGITS = 340;
  private static final ThreadLocal<NumberFormat> NUMBER_FORMATTER =
          ThreadLocal.withInitial(() -> {
            NumberFormat numberFormat = NumberFormat.getInstance(Locale.ENGLISH);
            numberFormat.setMaximumFractionDigits(MAX_FRACTION_DIGITS);
            numberFormat.setGroupingUsed(false);
            numberFormat.setMinimumFractionDigits(1);
            return numberFormat;
          });

  private static final int DEFAULT_STRING_BUILDER_SIZE = 1024;
  private static final ThreadLocal<StringBuilder> CACHED_STRINGBUILDERS =
          ThreadLocal.withInitial(() -> new StringBuilder(DEFAULT_STRING_BUILDER_SIZE));

  Point() {
  }

  /**
   * Create a new Point Build build to create a new Point in a fluent manner.
   *
   * @param measurement
   *            the name of the measurement.
   * @return the Builder to be able to add further Builder calls.
   */

  public static Builder measurement(final String measurement) {
    return new Builder(measurement);
  }

  /**
   * Create a new Point Build build to create a new Point in a fluent manner from a POJO.
   *
   * @param clazz Class of the POJO
   * @return the Builder instance
   */

  public static Builder measurementByPOJO(final Class<?> clazz) {
    Objects.requireNonNull(clazz, "clazz");
    throwExceptionIfMissingAnnotation(clazz, Measurement.class);
    String measurementName = findMeasurementName(clazz);
    return new Builder(measurementName);
  }

  private static void throwExceptionIfMissingAnnotation(final Class<?> clazz,
      final Class<? extends Annotation> expectedClass) {
    if (!clazz.isAnnotationPresent(expectedClass)) {
      throw new IllegalArgumentException("Class " + clazz.getName() + " is not annotated with @"
          + Measurement.class.getSimpleName());
    }
  }

  /**
   * Builder for a new Point.
   *
   * @author stefan.majer [at] gmail.com
   *
   */
  public static final class Builder {
    private static final BigInteger NANOSECONDS_PER_SECOND = BigInteger.valueOf(1000000000L);
    private final String measurement;
    private final Map<String, String> tags = new TreeMap<>();
    private Number time;
    private TimeUnit precision;
    private final Map<String, Object> fields = new TreeMap<>();

    /**
     * @param measurement
     */
    Builder(final String measurement) {
      this.measurement = measurement;
    }

    /**
     * Add a tag to this point.
     *
     * @param tagName
     *            the tag name
     * @param value
     *            the tag value
     * @return the Builder instance.
     */
    public Builder tag(final String tagName, final String value) {
      Objects.requireNonNull(tagName, "tagName");
      Objects.requireNonNull(value, "value");
      if (CheckTags.isLegalFullCheck(tagName, value)) {
        tags.put(tagName, value);
      }
      return this;
    }

    /**
     * Add a Map of tags to add to this point.
     *
     * @param tagsToAdd
     *            the Map of tags to add
     * @return the Builder instance.
     */
    public Builder tag(final Map<String, String> tagsToAdd) {
      for (Entry<String, String> tag : tagsToAdd.entrySet()) {
        if (CheckTags.isLegalFullCheck(tag.getKey(), tag.getValue())) {
          tags.put(tag.getKey(), tag.getValue());
        }
      }
      return this;
    }

    /**
     * Add a field to this point.
     *
     * @param field
     *            the field name
     * @param value
     *            the value of this field
     * @return the Builder instance.
     */
    @SuppressWarnings("checkstyle:finalparameters")
    @Deprecated
    public Builder field(final String field, Object value) {
      if (value instanceof Number) {
        if (value instanceof Byte) {
          value = ((Byte) value).doubleValue();
        } else if (value instanceof Short) {
          value = ((Short) value).doubleValue();
        } else if (value instanceof Integer) {
          value = ((Integer) value).doubleValue();
        } else if (value instanceof Long) {
          value = ((Long) value).doubleValue();
        } else if (value instanceof BigInteger) {
          value = ((BigInteger) value).doubleValue();
        }
      }
      fields.put(field, value);
      return this;
    }

    public Builder addField(final String field, final boolean value) {
      fields.put(field, value);
      return this;
    }

    public Builder addField(final String field, final long value) {
      fields.put(field, value);
      return this;
    }

    public Builder addField(final String field, final double value) {
      fields.put(field, value);
      return this;
    }

    public Builder addField(final String field, final int value) {
      fields.put(field, value);
      return this;
    }

    public Builder addField(final String field, final float value) {
      fields.put(field, value);
      return this;
    }

    public Builder addField(final String field, final short value) {
      fields.put(field, value);
      return this;
    }

    public Builder addField(final String field, final Number value) {
      fields.put(field, value);
      return this;
    }

    public Builder addField(final String field, final String value) {
      Objects.requireNonNull(value, "value");

      fields.put(field, value);
      return this;
    }

    /**
     * Add a Map of fields to this point.
     *
     * @param fieldsToAdd
     *            the fields to add
     * @return the Builder instance.
     */
    public Builder fields(final Map<String, Object> fieldsToAdd) {
      this.fields.putAll(fieldsToAdd);
      return this;
    }

    /**
     * Add a time to this point.
     *
     * @param timeToSet      the time for this point
     * @param precisionToSet the TimeUnit
     * @return the Builder instance.
     */
    public Builder time(final Number timeToSet, final TimeUnit precisionToSet) {
      Objects.requireNonNull(timeToSet, "timeToSet");
      Objects.requireNonNull(precisionToSet, "precisionToSet");
      this.time = timeToSet;
      this.precision = precisionToSet;
      return this;
    }

    /**
     * Add a time to this point as long.
     * only kept for binary compatibility with previous releases.
     *
     * @param timeToSet      the time for this point as long
     * @param precisionToSet the TimeUnit
     * @return the Builder instance.
     */
    public Builder time(final long timeToSet, final TimeUnit precisionToSet) {
      return time((Number) timeToSet, precisionToSet);
    }

    /**
     * Add a time to this point as Long.
     * only kept for binary compatibility with previous releases.
     *
     * @param timeToSet      the time for this point as Long
     * @param precisionToSet the TimeUnit
     * @return the Builder instance.
     */
    public Builder time(final Long timeToSet, final TimeUnit precisionToSet) {
      return time((Number) timeToSet, precisionToSet);
    }

    /**
     * Does this builder contain any fields?
     *
     * @return true, if the builder contains any fields, false otherwise.
     */
    public boolean hasFields() {
      return !fields.isEmpty();
    }

    /**
     * Adds field map from object by reflection using {@link org.influxdb.annotation.Column}
     * annotation.
     *
     * @param pojo POJO Object with annotation {@link org.influxdb.annotation.Column} on fields
     * @return the Builder instance
     */
    public Builder addFieldsFromPOJO(final Object pojo) {

      Class<? extends Object> clazz = pojo.getClass();
      while (clazz != null) {

        for (Field field : clazz.getDeclaredFields()) {

          Column column = field.getAnnotation(Column.class);

          if (column == null) {
            continue;
          }

          field.setAccessible(true);
          String fieldName = column.name();
          addFieldByAttribute(pojo, field, column, fieldName);
        }
      clazz = clazz.getSuperclass();
    }

      if (this.fields.isEmpty()) {
        throw new BuilderException("Class " + pojo.getClass().getName()
            + " has no @" + Column.class.getSimpleName() + " annotation");
      }

      return this;
    }

    private void addFieldByAttribute(final Object pojo, final Field field, final Column column,
        final String fieldName) {
      try {
        Object fieldValue = field.get(pojo);

        TimeColumn tc = field.getAnnotation(TimeColumn.class);
        if (tc != null && Instant.class.isAssignableFrom(field.getType())) {
          Optional.ofNullable((Instant) fieldValue).ifPresent(instant -> {
            TimeUnit timeUnit = tc.timeUnit();
            if (timeUnit == TimeUnit.NANOSECONDS || timeUnit == TimeUnit.MICROSECONDS) {
              this.time = BigInteger.valueOf(instant.getEpochSecond())
                                    .multiply(NANOSECONDS_PER_SECOND)
                                    .add(BigInteger.valueOf(instant.getNano()))
                                    .divide(BigInteger.valueOf(TimeUnit.NANOSECONDS.convert(1, timeUnit)));
            } else {
              this.time = TimeUnit.MILLISECONDS.convert(instant.toEpochMilli(), timeUnit);
              this.precision = timeUnit;
            }
            this.precision = timeUnit;
          });
          return;
        }

        if (column.tag()) {
          if (fieldValue != null) {
            this.tags.put(fieldName, (String) fieldValue);
          }
        } else {
          if (fieldValue != null) {
            this.fields.put(fieldName, fieldValue);
          }
        }

      } catch (IllegalArgumentException | IllegalAccessException e) {
        // Can not happen since we use metadata got from the object
        throw new BuilderException(
            "Field " + fieldName + " could not found on class " + pojo.getClass().getSimpleName());
      }
    }

    /**
     * Create a new Point.
     *
     * @return the newly created Point.
     */
    public Point build() {
      Preconditions.checkNonEmptyString(this.measurement, "measurement");
      Preconditions.checkPositiveNumber(this.fields.size(), "fields size");
      Point point = new Point();
      point.setFields(this.fields);
      point.setMeasurement(this.measurement);
      if (this.time != null) {
          point.setTime(this.time);
          point.setPrecision(this.precision);
      }
      point.setTags(this.tags);
      return point;
    }
  }

  /**
   * @param measurement
   *            the measurement to set
   */
  void setMeasurement(final String measurement) {
    this.measurement = measurement;
  }

  /**
   * @param time
   *            the time to set
   */
  void setTime(final Number time) {
    this.time = time;
  }

  /**
   * @param tags
   *            the tags to set
   */
  void setTags(final Map<String, String> tags) {
    this.tags = tags;
  }

  /**
   * @return the tags
   */
  Map<String, String> getTags() {
    return this.tags;
  }

  /**
   * @param precision
   *            the precision to set
   */
  void setPrecision(final TimeUnit precision) {
    this.precision = precision;
  }

  /**
   * @return the fields
   */
  Map<String, Object> getFields() {
    return this.fields;
  }

  /**
   * @param fields
   *            the fields to set
   */
  void setFields(final Map<String, Object> fields) {
    this.fields = fields;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Point point = (Point) o;
    return Objects.equals(measurement, point.measurement)
            && Objects.equals(tags, point.tags)
            && Objects.equals(time, point.time)
            && precision == point.precision
            && Objects.equals(fields, point.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(measurement, tags, time, precision, fields);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Point [name=");
    builder.append(this.measurement);
    if (this.time != null) {
      builder.append(", time=");
      builder.append(this.time);
    }
    builder.append(", tags=");
    builder.append(this.tags);
    if (this.precision != null) {
      builder.append(", precision=");
      builder.append(this.precision);
    }
    builder.append(", fields=");
    builder.append(this.fields);
    builder.append("]");
    return builder.toString();
  }

  /**
   * Calculate the lineprotocol entry for a single Point.
   * <p>
   * NaN and infinity values are silently dropped as they are unsupported:
   * https://github.com/influxdata/influxdb/issues/4089
   *
   * @see <a href="https://docs.influxdata.com/influxdb/v1.7/write_protocols/line_protocol_reference/">
   *     InfluxDB line protocol reference</a>
   *
   * @return the String without newLine, empty when there are no fields to write
   */
  public String lineProtocol() {
    return lineProtocol(null);
  }

  /**
   * Calculate the lineprotocol entry for a single point, using a specific {@link TimeUnit} for the timestamp.
   * <p>
   * NaN and infinity values are silently dropped as they are unsupported:
   * https://github.com/influxdata/influxdb/issues/4089
   *
   * @see <a href="https://docs.influxdata.com/influxdb/v1.7/write_protocols/line_protocol_reference/">
   *     InfluxDB line protocol reference</a>
   *
   * @param precision the time precision unit for this point
   * @return the String without newLine, empty when there are no fields to write
   */
  public String lineProtocol(final TimeUnit precision) {

    // setLength(0) is used for reusing cached StringBuilder instance per thread
    // it reduces GC activity and performs better then new StringBuilder()
    StringBuilder sb = CACHED_STRINGBUILDERS.get();
    sb.setLength(0);

    escapeKey(sb, measurement);
    concatenatedTags(sb);
    int writtenFields = concatenatedFields(sb);
    if (writtenFields == 0) {
      return "";
    }
    formatedTime(sb, precision);

    return sb.toString();
  }

  private void concatenatedTags(final StringBuilder sb) {
    for (Entry<String, String> tag : this.tags.entrySet()) {
      sb.append(',');
      escapeKey(sb, tag.getKey());
      sb.append('=');
      escapeKey(sb, tag.getValue());
    }
    sb.append(' ');
  }

  private int concatenatedFields(final StringBuilder sb) {
    int fieldCount = 0;
    for (Entry<String, Object> field : this.fields.entrySet()) {
      Object value = field.getValue();
      if (value == null || isNotFinite(value)) {
        continue;
      }
      escapeKey(sb, field.getKey());
      sb.append('=');
      if (value instanceof Number) {
        if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
          sb.append(NUMBER_FORMATTER.get().format(value));
        } else {
          sb.append(value).append('i');
        }
      } else if (value instanceof String) {
        String stringValue = (String) value;
        sb.append('"');
        escapeField(sb, stringValue);
        sb.append('"');
      } else {
        sb.append(value);
      }

      sb.append(',');

      fieldCount++;
    }

    // efficiently chop off the trailing comma
    int lengthMinusOne = sb.length() - 1;
    if (sb.charAt(lengthMinusOne) == ',') {
      sb.setLength(lengthMinusOne);
    }

    return fieldCount;
  }

  static void escapeKey(final StringBuilder sb, final String key) {
    for (int i = 0; i < key.length(); i++) {
      switch (key.charAt(i)) {
        case ' ':
        case ',':
        case '=':
          sb.append('\\');
        default:
          sb.append(key.charAt(i));
      }
    }
  }

  static void escapeField(final StringBuilder sb, final String field) {
    for (int i = 0; i < field.length(); i++) {
      switch (field.charAt(i)) {
        case '\\':
        case '\"':
          sb.append('\\');
        default:
          sb.append(field.charAt(i));
      }
    }
  }

  private static boolean isNotFinite(final Object value) {
    return value instanceof Double && !Double.isFinite((Double) value)
            || value instanceof Float && !Float.isFinite((Float) value);
  }

  private void formatedTime(final StringBuilder sb, final TimeUnit precision) {
    if (this.time == null) {
      return;
    }
    TimeUnit converterPrecision = precision;

    if (converterPrecision == null) {
      converterPrecision = TimeUnit.NANOSECONDS;
    }
    if (this.time instanceof BigInteger) {
      BigInteger time = (BigInteger) this.time;
      long conversionFactor = converterPrecision.convert(1, this.precision);
      if (conversionFactor >= 1) {
        time = time.multiply(BigInteger.valueOf(conversionFactor));
      } else {
        conversionFactor = this.precision.convert(1, converterPrecision);
        time = time.divide(BigInteger.valueOf(conversionFactor));
      }
      sb.append(" ").append(time);
    } else if (this.time instanceof BigDecimal) {
      BigDecimal time = (BigDecimal) this.time;
      long conversionFactor = converterPrecision.convert(1, this.precision);
      if (conversionFactor >= 1) {
        time = time.multiply(BigDecimal.valueOf(conversionFactor));
      } else {
        conversionFactor = this.precision.convert(1, converterPrecision);
        time = time.divide(BigDecimal.valueOf(conversionFactor), RoundingMode.HALF_UP);
      }
      sb.append(" ").append(time.toBigInteger());
    } else {
      sb.append(" ").append(converterPrecision.convert(this.time.longValue(), this.precision));
    }
  }


  private static String findMeasurementName(final Class<?> clazz) {
    return clazz.getAnnotation(Measurement.class).name();
  }
}
