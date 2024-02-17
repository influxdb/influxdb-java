package org.influxdb.impl;

import org.influxdb.InfluxDB;
import org.influxdb.annotation.Measurement;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import java.util.List;

public class InfluxDBMapper extends InfluxDBResultMapper {

  private final InfluxDB influxDB;

  public InfluxDBMapper(final InfluxDB influxDB) {
    this.influxDB = influxDB;
  }

  String getDatabaseName(final Class<?> clazz) {
    return ((Measurement) clazz.getAnnotation(Measurement.class)).database();
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
   *
   * @return a {@link List} of objects from the same Class passed as parameter and sorted on the
   * same order as received from InfluxDB.
   *
   * @throws InfluxDBMapperException If {@link QueryResult} parameter contain errors,
   * <code>clazz</code> parameter is not annotated with &#64;Measurement or it was not
   * possible to define the values of your POJO (e.g. due to an unsupported field type).
   */
  public <T> List<T> toPOJO(final QueryResult queryResult, final Class<T> clazz, final String measurementName)
          throws InfluxDBMapperException {
    return toPOJO(queryResult, clazz, measurementName, TimeUnit.MILLISECONDS);
  }

  public <T> List<T> query(final Query query, final Class<T> clazz, final String measurementName) {
    QueryResult queryResult = influxDB.query(query);
    return toPOJO(queryResult, clazz, measurementName);
  }

  public <T> List<T> query(final Query query, final Class<T> clazz) {
    throwExceptionIfMissingAnnotation(clazz);
    QueryResult queryResult = influxDB.query(query);
    return toPOJO(queryResult, clazz);
  }

  public <T> List<T> query(final Class<T> clazz) {
    throwExceptionIfMissingAnnotation(clazz);

    String measurement = getMeasurementName(clazz);
    String database = getDatabaseName(clazz);

    if ("[unassigned]".equals(database)) {
      throw new IllegalArgumentException(
          Measurement.class.getSimpleName()
              + " of class "
              + clazz.getName()
              + " should specify a database value for this operation");
    }

    QueryResult queryResult = influxDB.query(new Query("SELECT * FROM " + measurement, database));
    return toPOJO(queryResult, clazz);
  }

  public <T> void save(final T model) {
    throwExceptionIfMissingAnnotation(model.getClass());
    Class<?> modelType = model.getClass();
    String database = getDatabaseName(modelType);
    String retentionPolicy = getRetentionPolicy(modelType);
    Point.Builder pointBuilder = Point.measurementByPOJO(modelType).addFieldsFromPOJO(model);
    Point point = pointBuilder.build();

    if ("[unassigned]".equals(database)) {
      influxDB.write(point);
    } else {
      influxDB.write(database, retentionPolicy, point);
    }
  }
}
