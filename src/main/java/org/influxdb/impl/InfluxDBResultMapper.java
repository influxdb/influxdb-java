/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 azeti Networks AG (<info@azeti.net>)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.influxdb.impl;

import org.influxdb.InfluxDBMapperException;
import org.influxdb.annotation.Column;
import org.influxdb.annotation.Exclude;
import org.influxdb.annotation.Measurement;
import org.influxdb.dto.QueryResult;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Main class responsible for mapping a QueryResult to a POJO.
 *
 * @author fmachado
 */
public class InfluxDBResultMapper {

  /**
   * Data structure used to cache classes used as measurements.
   */
  private static class ClassInfo {
    ConcurrentMap<String, Field> fieldMap;
    ConcurrentMap<Field, TypeMapper> typeMappers;
  }
  private static final
    ConcurrentMap<String, ClassInfo> CLASS_INFO_CACHE = new ConcurrentHashMap<>();

  private static final int FRACTION_MIN_WIDTH = 0;
  private static final int FRACTION_MAX_WIDTH = 9;
  private static final boolean ADD_DECIMAL_POINT = true;

  /**
   * When a query is executed without {@link TimeUnit}, InfluxDB returns the <code>time</code>
   * column as a RFC3339 date.
   */
  private static final DateTimeFormatter RFC3339_FORMATTER = new DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
    .appendFraction(ChronoField.NANO_OF_SECOND, FRACTION_MIN_WIDTH, FRACTION_MAX_WIDTH, ADD_DECIMAL_POINT)
    .appendZoneOrOffsetId()
    .toFormatter();

  /**
   * <p>
   * Process a {@link QueryResult} object returned by the InfluxDB client inspecting the internal
   * data structure and creating the respective object instances based on the Class passed as
   * parameter.
   * </p>
   *
   * @param queryResult the InfluxDB result object
   * @param clazz the Class that will be used to hold your measurement data
   * @param <T> the target type
   *
   * @return a {@link List} of objects from the same Class passed as parameter and sorted on the
   * same order as received from InfluxDB.
   *
   * @throws InfluxDBMapperException If {@link QueryResult} parameter contain errors,
   * <code>clazz</code> parameter is not annotated with &#64;Measurement or it was not
   * possible to define the values of your POJO (e.g. due to an unsupported field type).
   */
  public <T> List<T> toPOJO(final QueryResult queryResult, final Class<T> clazz) throws InfluxDBMapperException {
    return toPOJO(queryResult, clazz, TimeUnit.MILLISECONDS);
  }

  /**
   * <p>
   * Process a {@link QueryResult} object returned by the InfluxDB client inspecting the internal
   * data structure and creating the respective object instances based on the Class passed as
   * parameter.
   * </p>
   *
   * @param queryResult the InfluxDB result object
   * @param clazz the Class that will be used to hold your measurement data
   * @param precision the time precision of results
   * @param <T> the target type
   *
   * @return a {@link List} of objects from the same Class passed as parameter and sorted on the
   * same order as received from InfluxDB.
   *
   * @throws InfluxDBMapperException If {@link QueryResult} parameter contain errors,
   * <code>clazz</code> parameter is not annotated with &#64;Measurement or it was not
   * possible to define the values of your POJO (e.g. due to an unsupported field type).
   */
  public <T> List<T> toPOJO(final QueryResult queryResult, final Class<T> clazz,
                            final TimeUnit precision) throws InfluxDBMapperException {
    throwExceptionIfMissingAnnotation(clazz);
    String measurementName = getMeasurementName(clazz);
    return this.toPOJO(queryResult, clazz, measurementName, precision);
  }

  /**
   * <p>
   * Process a {@link QueryResult} object returned by the InfluxDB client inspecting the internal
   * data structure and creating the respective object instances based on the Class passed as
   * parameter.
   * </p>
   *
   * @param queryResult the InfluxDB result object
   * @param clazz the Class that will be used to hold your measurement data
   * @param <T> the target type
   * @param measurementName name of the Measurement
   * @param precision the time precision of results
   *
   * @return a {@link List} of objects from the same Class passed as parameter and sorted on the
   * same order as received from InfluxDB.
   *
   * @throws InfluxDBMapperException If {@link QueryResult} parameter contain errors,
   * <code>clazz</code> parameter is not annotated with &#64;Measurement or it was not
   * possible to define the values of your POJO (e.g. due to an unsupported field type).
   */
  public <T> List<T> toPOJO(final QueryResult queryResult, final Class<T> clazz, final String measurementName,
                            final TimeUnit precision)
      throws InfluxDBMapperException {

    Objects.requireNonNull(measurementName, "measurementName");
    Objects.requireNonNull(queryResult, "queryResult");
    Objects.requireNonNull(clazz, "clazz");

    throwExceptionIfResultWithError(queryResult);
    cacheMeasurementClass(clazz);

    List<T> result = new LinkedList<T>();

    queryResult.getResults().stream()
      .filter(internalResult -> Objects.nonNull(internalResult) && Objects.nonNull(internalResult.getSeries()))
      .forEach(internalResult -> {
        internalResult.getSeries().stream()
          .filter(series -> series.getName().equals(measurementName))
          .forEachOrdered(series -> {
            parseSeriesAs(series, clazz, result, precision);
          });
        });

    return result;
  }

  void throwExceptionIfMissingAnnotation(final Class<?> clazz) {
    if (!clazz.isAnnotationPresent(Measurement.class)) {
      throw new IllegalArgumentException(
        "Class " + clazz.getName() + " is not annotated with @" + Measurement.class.getSimpleName());
    }
  }

  void throwExceptionIfResultWithError(final QueryResult queryResult) {
    if (queryResult.getError() != null) {
      throw new InfluxDBMapperException("InfluxDB returned an error: " + queryResult.getError());
    }

    queryResult.getResults().forEach(seriesResult -> {
      if (seriesResult.getError() != null) {
        throw new InfluxDBMapperException("InfluxDB returned an error with Series: " + seriesResult.getError());
      }
    });
  }

  void cacheMeasurementClass(final Class<?>... classVarAgrs) {
    for (Class<?> clazz : classVarAgrs) {
      if (CLASS_INFO_CACHE.containsKey(clazz.getName())) {
        continue;
      }
      ConcurrentMap<String, Field> fieldMap = new ConcurrentHashMap<>();
      ConcurrentMap<Field, TypeMapper> typeMappers = new ConcurrentHashMap<>();

      Measurement measurement = clazz.getAnnotation(Measurement.class);
      boolean allFields = measurement != null && measurement.allFields();

      Class<?> c = clazz;
      TypeMapper typeMapper = TypeMapper.empty();
      while (c != null) {
        for (Field field : c.getDeclaredFields()) {
          Column colAnnotation = field.getAnnotation(Column.class);
          if (colAnnotation == null && !(allFields
                  && !field.isAnnotationPresent(Exclude.class) && !Modifier.isStatic(field.getModifiers()))) {
            continue;
          }

          fieldMap.put(getFieldName(field, colAnnotation), field);
          typeMappers.put(field, typeMapper);
        }

        Class<?> superclass = c.getSuperclass();
        Type genericSuperclass = c.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
          typeMapper = TypeMapper.of((ParameterizedType) genericSuperclass, superclass);
        } else {
          typeMapper = TypeMapper.empty();
        }

        c = superclass;
      }

      ClassInfo classInfo = new ClassInfo();
      classInfo.fieldMap = fieldMap;
      classInfo.typeMappers = typeMappers;
      CLASS_INFO_CACHE.putIfAbsent(clazz.getName(), classInfo);
    }
  }

  private static String getFieldName(final Field field, final Column colAnnotation) {
    if (colAnnotation != null && !colAnnotation.name().isEmpty()) {
      return colAnnotation.name();
    }

    return field.getName();
  }

  String getMeasurementName(final Class<?> clazz) {
    return ((Measurement) clazz.getAnnotation(Measurement.class)).name();
  }

  String getRetentionPolicy(final Class<?> clazz) {
    return ((Measurement) clazz.getAnnotation(Measurement.class)).retentionPolicy();
  }

  <T> List<T> parseSeriesAs(final QueryResult.Series series, final Class<T> clazz, final List<T> result) {
    return parseSeriesAs(series, clazz, result, TimeUnit.MILLISECONDS);
  }

  <T> List<T> parseSeriesAs(final QueryResult.Series series, final Class<T> clazz, final List<T> result,
                            final TimeUnit precision) {
    int columnSize = series.getColumns().size();

    ClassInfo classInfo = CLASS_INFO_CACHE.get(clazz.getName());
    try {
      T object = null;
      for (List<Object> row : series.getValues()) {
        for (int i = 0; i < columnSize; i++) {
          Field correspondingField = classInfo.fieldMap.get(series.getColumns().get(i)/*InfluxDB columnName*/);
          if (correspondingField != null) {
            if (object == null) {
              object = clazz.newInstance();
            }
            setFieldValue(object, correspondingField, row.get(i), precision,
                    classInfo.typeMappers.get(correspondingField));
          }
        }
        // When the "GROUP BY" clause is used, "tags" are returned as Map<String,String> and
        // accordingly with InfluxDB documentation
        // https://docs.influxdata.com/influxdb/v1.2/concepts/glossary/#tag-value
        // "tag" values are always String.
        if (series.getTags() != null && !series.getTags().isEmpty()) {
          for (Entry<String, String> entry : series.getTags().entrySet()) {
            Field correspondingField = classInfo.fieldMap.get(entry.getKey()/*InfluxDB columnName*/);
            if (correspondingField != null) {
              // I don't think it is possible to reach here without a valid "object"
              setFieldValue(object, correspondingField, entry.getValue(), precision,
                      classInfo.typeMappers.get(correspondingField));
            }
          }
        }
        if (object != null) {
          result.add(object);
          object = null;
        }
      }
    } catch (InstantiationException | IllegalAccessException e) {
      throw new InfluxDBMapperException(e);
    }
    return result;
  }

  /**
   * InfluxDB client returns any number as Double.
   * See <a href="https://github.com/influxdata/influxdb-java/issues/153#issuecomment-259681987">...</a>
   * for more information.
   *
   */
  private static <T> void setFieldValue(final T object, final Field field, final Object value, final TimeUnit precision,
                                        final TypeMapper typeMapper)
    throws IllegalArgumentException, IllegalAccessException {
    if (value == null) {
      return;
    }
    Type fieldType = typeMapper.resolve(field.getGenericType());
    if (!field.isAccessible()) {
      field.setAccessible(true);
    }
    field.set(object, adaptValue((Class<?>) fieldType, value, precision, field.getName(), object.getClass().getName()));
  }

  private static Object adaptValue(final Class<?> fieldType, final Object value, final TimeUnit precision,
                                   final String fieldName, final String className) {
    try {
      if (String.class.isAssignableFrom(fieldType)) {
        return String.valueOf(value);
      }
      if (Instant.class.isAssignableFrom(fieldType)) {
        if (value instanceof String) {
          return Instant.from(RFC3339_FORMATTER.parse(String.valueOf(value)));
        }
        if (value instanceof Long) {
          return Instant.ofEpochMilli(toMillis((long) value, precision));
        }
        if (value instanceof Double) {
          return Instant.ofEpochMilli(toMillis(((Double) value).longValue(), precision));
        }
        if (value instanceof Integer) {
          return Instant.ofEpochMilli(toMillis(((Integer) value).longValue(), precision));
        }
        throw new InfluxDBMapperException("Unsupported type " + fieldType + " for field " + fieldName);
      }
      if (Double.class.isAssignableFrom(fieldType) || double.class.isAssignableFrom(fieldType)) {
        return value;
      }
      if (Long.class.isAssignableFrom(fieldType) || long.class.isAssignableFrom(fieldType)) {
        return ((Double) value).longValue();
      }
      if (Integer.class.isAssignableFrom(fieldType) || int.class.isAssignableFrom(fieldType)) {
        return ((Double) value).intValue();
      }
      if (Boolean.class.isAssignableFrom(fieldType) || boolean.class.isAssignableFrom(fieldType)) {
        return Boolean.valueOf(String.valueOf(value));
      }
      if (Enum.class.isAssignableFrom(fieldType)) {
        //noinspection unchecked
        return Enum.valueOf((Class<Enum>) fieldType, String.valueOf(value));
      }
    } catch (ClassCastException e) {
      String msg = "Class '%s' field '%s' was defined with a different field type and caused a ClassCastException. "
        + "The correct type is '%s' (current field value: '%s').";
      throw new InfluxDBMapperException(
        String.format(msg, className, fieldName, value.getClass().getName(), value));
    }

    throw new InfluxDBMapperException(
           String.format("Class '%s' field '%s' is from an unsupported type '%s'.", className, fieldName, fieldType));
  }

  private static long toMillis(final long value, final TimeUnit precision) {
    return TimeUnit.MILLISECONDS.convert(value, precision);
  }
}
