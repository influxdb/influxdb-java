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

import com.android.tools.r8.RecordTag;
import org.influxdb.InfluxDBMapperException;
import org.influxdb.annotation.Column;
import org.influxdb.annotation.Measurement;
import org.influxdb.annotation.TimeColumn;
import org.influxdb.dto.QueryResult;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Test measurement classes simulate Android desugared records.
 *
 * @author Eran Leshem
 */
@SuppressWarnings({"removal", "deprecation"})
@RunWith(JUnitPlatform.class)
public class InfluxDBAndroidDesugaredRecordResultMapperTest {

  private final InfluxDBResultMapper mapper = new InfluxDBResultMapper();

  @Test
  public void testToRecord_HappyPath() {
    // Given...
    List<String> columnList = Arrays.asList("time", "uuid");
    List<Object> firstSeriesResult = Arrays.asList(Instant.now().toEpochMilli(), UUID.randomUUID().toString());

    QueryResult.Series series = new QueryResult.Series();
    series.setColumns(columnList);
    series.setName("CustomMeasurement");
    series.setValues(Arrays.asList(firstSeriesResult));

    QueryResult.Result internalResult = new QueryResult.Result();
    internalResult.setSeries(Arrays.asList(series));

    QueryResult queryResult = new QueryResult();
    queryResult.setResults(Arrays.asList(internalResult));

    //When...
    List<MyCustomMeasurement> myList = mapper.toPOJO(queryResult, MyCustomMeasurement.class);

    // Then...
    Assertions.assertEquals(1, myList.size(), "there must be one entry in the result list");
  }

  @Test
  public void testThrowExceptionIfMissingAnnotation() {
    Assertions.assertThrows(IllegalArgumentException.class,
            () -> mapper.throwExceptionIfMissingAnnotation(String.class));
  }

  @Test
  public void testThrowExceptionIfError_InfluxQueryResultHasError() {
    QueryResult queryResult = new QueryResult();
    queryResult.setError("main queryresult error");

    Assertions.assertThrows(InfluxDBMapperException.class, () -> mapper.throwExceptionIfResultWithError(queryResult));
  }

  @Test
  public void testThrowExceptionIfError_InfluxQueryResultSeriesHasError() {
    QueryResult.Result seriesResult = new QueryResult.Result();
    seriesResult.setError("series error");

    QueryResult queryResult = new QueryResult();
    queryResult.setResults(Arrays.asList(seriesResult));

    Assertions.assertThrows(InfluxDBMapperException.class, () -> mapper.throwExceptionIfResultWithError(queryResult));
  }

  @Test
  public void testGetMeasurementName_testStateMeasurement() {
    Assertions.assertEquals("CustomMeasurement", mapper.getMeasurementName(MyCustomMeasurement.class));
  }

  @Test
  public void testParseSeriesAs_testTwoValidSeries() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MyCustomMeasurement.class);

    List<String> columnList = Arrays.asList("time", "uuid");

    List<Object> firstSeriesResult = Arrays.asList(Instant.now().toEpochMilli(), UUID.randomUUID().toString());
    List<Object> secondSeriesResult = Arrays.asList(Instant.now().plusSeconds(1).toEpochMilli(),
            UUID.randomUUID().toString());

    QueryResult.Series series = new QueryResult.Series();
    series.setColumns(columnList);
    series.setValues(Arrays.asList(firstSeriesResult, secondSeriesResult));

    //When...
    List<MyCustomMeasurement> result = new LinkedList<>();
    mapper.parseSeriesAs(series, MyCustomMeasurement.class, result);

    //Then...
    Assertions.assertTrue(result.size() == 2, "there must be two series in the result list");

    Assertions.assertEquals(firstSeriesResult.get(0), result.get(0).time().toEpochMilli(),
            "Field 'time' (1st series) is not valid");
    Assertions.assertEquals(firstSeriesResult.get(1), result.get(0).uuid(), "Field 'uuid' (1st series) is not valid");

    Assertions.assertEquals(secondSeriesResult.get(0), result.get(1).time().toEpochMilli(),
            "Field 'time' (2nd series) is not valid");
    Assertions.assertEquals(secondSeriesResult.get(1), result.get(1).uuid(), "Field 'uuid' (2nd series) is not valid");
  }

  @Test
  public void testParseSeriesAs_testNonNullAndValidValues() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MyCustomMeasurementWithPrimitives.class);

    List<String> columnList = Arrays.asList("time", "uuid",
            "doubleObject", "longObject", "integerObject",
            "doublePrimitive", "longPrimitive", "integerPrimitive",
            "booleanObject", "booleanPrimitive");

    // InfluxDB client returns the time representation as Double.
    Double now = Long.valueOf(System.currentTimeMillis()).doubleValue();

    // InfluxDB client returns any number as Double.
    // See https://github.com/influxdata/influxdb-java/issues/153#issuecomment-259681987
    // for more information.

    QueryResult.Series series = new QueryResult.Series();
    series.setColumns(columnList);
    String uuidAsString = UUID.randomUUID().toString();
    List<Object> seriesResult = Arrays.asList(now, uuidAsString,
            new Double("1.01"), new Double("2"), new Double("3"),
            new Double("1.01"), new Double("4"), new Double("5"),
            "false", "true");
    series.setValues(Arrays.asList(seriesResult));

    //When...
    List<MyCustomMeasurementWithPrimitives> result = new LinkedList<>();
    mapper.parseSeriesAs(series, MyCustomMeasurementWithPrimitives.class, result);

    //Then...
    MyCustomMeasurementWithPrimitives myObject = result.get(0);
    Assertions.assertEquals(now.longValue(), myObject.time().toEpochMilli(), "field 'time' does not match");
    Assertions.assertEquals(uuidAsString, myObject.uuid(), "field 'uuid' does not match");

    Assertions.assertEquals(asDouble(seriesResult.get(2)), myObject.doubleObject(),
            "field 'doubleObject' does not match");
    Assertions.assertEquals(Long.valueOf(asDouble(seriesResult.get(3)).longValue()), myObject.longObject(),
            "field 'longObject' does not match");
    Assertions.assertEquals(Integer.valueOf(asDouble(seriesResult.get(4)).intValue()), myObject.integerObject(),
            "field 'integerObject' does not match");

    Assertions.assertTrue(
            Double.compare(asDouble(seriesResult.get(5)).doubleValue(), myObject.doublePrimitive()) == 0,
            "field 'doublePrimitive' does not match");

    Assertions.assertTrue(asDouble(seriesResult.get(6)).longValue() == myObject.longPrimitive(),
            "field 'longPrimitive' does not match");

    Assertions.assertTrue(asDouble(seriesResult.get(7)).intValue() == myObject.integerPrimitive(),
            "field 'integerPrimitive' does not match");

    Assertions.assertEquals(
            Boolean.valueOf(String.valueOf(seriesResult.get(8))), myObject.booleanObject(),
            "field 'booleanObject' does not match");

    Assertions.assertEquals(
            Boolean.valueOf(String.valueOf(seriesResult.get(9))).booleanValue(), myObject.booleanPrimitive(),
            "field 'booleanPrimitive' does not match");
  }

  private static Double asDouble(Object obj) {
    return (Double) obj;
  }

  @Test
  public void testFieldValueModified_DateAsISO8601() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MyCustomMeasurement.class);

    List<String> columnList = Arrays.asList("time");

    QueryResult.Series series = new QueryResult.Series();
    series.setColumns(columnList);
    List<Object> firstSeriesResult = Arrays.asList("2017-06-19T09:29:45.655123Z");
    series.setValues(Arrays.asList(firstSeriesResult));

    //When...
    List<MyCustomMeasurement> result = new LinkedList<>();
    mapper.parseSeriesAs(series, MyCustomMeasurement.class, result);

    //Then...
    Assertions.assertTrue(result.size() == 1);
  }

  @Test
  public void testFieldValueModified_DateAsInteger() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MyCustomMeasurement.class);

    List<String> columnList = Arrays.asList("time");

    QueryResult.Series series = new QueryResult.Series();
    series.setColumns(columnList);
    List<Object> firstSeriesResult = Arrays.asList(1_000);
    series.setValues(Arrays.asList(firstSeriesResult));

    //When...
    List<MyCustomMeasurement> result = new LinkedList<>();
    mapper.parseSeriesAs(series, MyCustomMeasurement.class, result);

    //Then...
    Assertions.assertTrue(result.size() == 1);
  }

  @Test
  public void testUnsupportedField() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MyRecordWithUnsupportedField.class);

    List<String> columnList = Arrays.asList("bar");

    QueryResult.Series series = new QueryResult.Series();
    series.setColumns(columnList);
    List<Object> firstSeriesResult = Arrays.asList("content representing a Date");
    series.setValues(Arrays.asList(firstSeriesResult));

    //When...
    List<MyRecordWithUnsupportedField> result = new LinkedList<>();
    Assertions.assertThrows(InfluxDBMapperException.class,
            () -> mapper.parseSeriesAs(series, MyRecordWithUnsupportedField.class, result));
  }

  /**
   * <a href="https://github.com/influxdata/influxdb/issues/7596">for more information</a>.
   */
  @Test
  public void testToRecord_SeriesFromQueryResultIsNull() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MyCustomMeasurement.class);

    QueryResult.Result internalResult = new QueryResult.Result();
    internalResult.setSeries(null);

    QueryResult queryResult = new QueryResult();
    queryResult.setResults(Arrays.asList(internalResult));

    // When...
    List<MyCustomMeasurement> myList = mapper.toPOJO(queryResult, MyCustomMeasurement.class);

    // Then...
    Assertions.assertTrue(myList.isEmpty(), "there must NO entry in the result list");
  }

  @Test
  public void testToRecord_QueryResultCreatedByGroupByClause() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(GroupByCarrierDeviceOS.class);

    // InfluxDB client returns the time representation as Double.
    Double now = Long.valueOf(System.currentTimeMillis()).doubleValue();

    // When the "GROUP BY" clause is used, "tags" are returned as Map<String,String>
    Map<String, String> firstSeriesTagMap = new HashMap<>(2);
    firstSeriesTagMap.put("CARRIER", "000/00");
    firstSeriesTagMap.put("DEVICE_OS_VERSION", "4.4.2");

    Map<String, String> secondSeriesTagMap = new HashMap<>(2);
    secondSeriesTagMap.put("CARRIER", "000/01");
    secondSeriesTagMap.put("DEVICE_OS_VERSION", "9.3.5");

    QueryResult.Series firstSeries = new QueryResult.Series();
    List<String> columnList = Arrays.asList("time", "median", "min", "max");
    firstSeries.setColumns(columnList);
    List<Object> firstSeriesResult = Arrays.asList(now, new Double("233.8"), new Double("0.0"),
            new Double("3090744.0"));
    firstSeries.setValues(Arrays.asList(firstSeriesResult));
    firstSeries.setTags(firstSeriesTagMap);
    firstSeries.setName("tb_network");

    QueryResult.Series secondSeries = new QueryResult.Series();
    secondSeries.setColumns(columnList);
    List<Object> secondSeriesResult = Arrays.asList(now, new Double("552.0"), new Double("135.0"),
            new Double("267705.0"));
    secondSeries.setValues(Arrays.asList(secondSeriesResult));
    secondSeries.setTags(secondSeriesTagMap);
    secondSeries.setName("tb_network");

    QueryResult.Result internalResult = new QueryResult.Result();
    internalResult.setSeries(Arrays.asList(firstSeries, secondSeries));

    QueryResult queryResult = new QueryResult();
    queryResult.setResults(Arrays.asList(internalResult));

    // When...
    List<GroupByCarrierDeviceOS> myList = mapper.toPOJO(queryResult, GroupByCarrierDeviceOS.class);

    // Then...
    GroupByCarrierDeviceOS firstGroupByEntry = myList.get(0);
    Assertions.assertEquals("000/00", firstGroupByEntry.carrier(), "field 'carrier' does not match");
    Assertions.assertEquals("4.4.2", firstGroupByEntry.deviceOsVersion(), "field 'deviceOsVersion' does not match");

    GroupByCarrierDeviceOS secondGroupByEntry = myList.get(1);
    Assertions.assertEquals("000/01", secondGroupByEntry.carrier(), "field 'carrier' does not match");
    Assertions.assertEquals("9.3.5", secondGroupByEntry.deviceOsVersion(), "field 'deviceOsVersion' does not match");
  }

  @Test
  public void testToRecord_ticket363() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MyCustomMeasurement.class);

    List<String> columnList = Arrays.asList("time");

    QueryResult.Series series = new QueryResult.Series();
    series.setColumns(columnList);
    List<Object> firstSeriesResult = Arrays.asList("2000-01-01T00:00:00.000000001Z");
    series.setValues(Arrays.asList(firstSeriesResult));

    // When...
    List<MyCustomMeasurement> result = new LinkedList<>();
    mapper.parseSeriesAs(series, MyCustomMeasurement.class, result);

    // Then...
    Assertions.assertEquals(1, result.size(), "incorrect number of elemets");
    Assertions.assertEquals(1, result.get(0).time().getNano(), "incorrect value for the nanoseconds field");
  }

  @Test
  void testToRecord_Precision() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MyCustomMeasurement.class);

    QueryResult.Series series = new QueryResult.Series();
    series.setName("CustomMeasurement");
    List<String> columnList = Arrays.asList("time");
    series.setColumns(columnList);
    List<Object> firstSeriesResult = Arrays.asList(1_500_000L);
    series.setValues(Arrays.asList(firstSeriesResult));

    QueryResult.Result internalResult = new QueryResult.Result();
    internalResult.setSeries(Arrays.asList(series));

    QueryResult queryResult = new QueryResult();
    queryResult.setResults(Arrays.asList(internalResult));

    // When...
    List<MyCustomMeasurement> result = mapper.toPOJO(queryResult, MyCustomMeasurement.class, TimeUnit.SECONDS);

    // Then...
    Assertions.assertEquals(1, result.size(), "incorrect number of elements");
    Assertions.assertEquals(1_500_000_000L, result.get(0).time().toEpochMilli(),
            "incorrect value for the millis field");
  }

  @Test
  void testToRecord_SetMeasureName() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MyCustomMeasurement.class);

    QueryResult.Series series = new QueryResult.Series();
    series.setName("MySeriesName");
    List<String> columnList = Arrays.asList("uuid");
    series.setColumns(columnList);
    List<Object> firstSeriesResult = Collections.singletonList(UUID.randomUUID().toString());
    series.setValues(Arrays.asList(firstSeriesResult));

    QueryResult.Result internalResult = new QueryResult.Result();
    internalResult.setSeries(Arrays.asList(series));

    QueryResult queryResult = new QueryResult();
    queryResult.setResults(Arrays.asList(internalResult));

    //When...
    List<MyCustomMeasurement> result =
            mapper.toPOJO(queryResult, MyCustomMeasurement.class, "MySeriesName");

    //Then...
    Assertions.assertTrue(result.size() == 1);
  }

  @Test
  public void testToRecord_HasTimeColumn() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(HasTimeColumnMeasurement.class);

    List<String> columnList = Arrays.asList("time");

    QueryResult.Series series = new QueryResult.Series();
    series.setColumns(columnList);
    List<List<Object>> valuesList = Arrays.asList(
            Arrays.asList("2015-08-17T19:00:00-05:00"), // Chicago (UTC-5)
            Arrays.asList("2015-08-17T19:00:00.000000001-05:00"), // Chicago (UTC-5)
            Arrays.asList("2000-01-01T00:00:00-00:00"),
            Arrays.asList("2000-01-02T00:00:00+00:00")
    );
    series.setValues(valuesList);

    // When...
    List<HasTimeColumnMeasurement> result = new LinkedList<>();
    mapper.parseSeriesAs(series, HasTimeColumnMeasurement.class, result);

    // Then...
    Assertions.assertEquals(4, result.size(), "incorrect number of elemets");
    // Note: RFC3339 timestamp with TZ from InfluxDB are parsed into an Instant (UTC)
    Assertions.assertTrue(result.get(0).time().equals(Instant.parse("2015-08-18T00:00:00Z")));
    Assertions.assertTrue(result.get(1).time().equals(Instant.parse("2015-08-18T00:00:00.000000001Z")));
    // RFC3339 section 4.3 https://tools.ietf.org/html/rfc3339#section-4.3
    Assertions.assertTrue(result.get(2).time().equals(Instant.parse("2000-01-01T00:00:00Z")));
    Assertions.assertTrue(result.get(3).time().equals(Instant.parse("2000-01-02T00:00:00Z")));

  }

  @Test
  public void testToRecord_ticket573() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MyCustomMeasurement.class);

    List<String> columnList = Arrays.asList("time");

    QueryResult.Series series = new QueryResult.Series();
    series.setColumns(columnList);
    List<List<Object>> valuesList = Arrays.asList(
            Arrays.asList("2015-08-17T19:00:00-05:00"), // Chicago (UTC-5)
            Arrays.asList("2015-08-17T19:00:00.000000001-05:00"), // Chicago (UTC-5)
            Arrays.asList("2000-01-01T00:00:00-00:00"),
            Arrays.asList("2000-01-02T00:00:00+00:00")
    );
    series.setValues(valuesList);

    // When...
    List<MyCustomMeasurement> result = new LinkedList<>();
    mapper.parseSeriesAs(series, MyCustomMeasurement.class, result);

    // Then...
    Assertions.assertEquals(4, result.size(), "incorrect number of elemets");
    // Note: RFC3339 timestamp with TZ from InfluxDB are parsed into an Instant (UTC)
    Assertions.assertTrue(result.get(0).time().equals(Instant.parse("2015-08-18T00:00:00Z")));
    Assertions.assertTrue(result.get(1).time().equals(Instant.parse("2015-08-18T00:00:00.000000001Z")));
    // RFC3339 section 4.3 https://tools.ietf.org/html/rfc3339#section-4.3
    Assertions.assertTrue(result.get(2).time().equals(Instant.parse("2000-01-01T00:00:00Z")));
    Assertions.assertTrue(result.get(3).time().equals(Instant.parse("2000-01-02T00:00:00Z")));
  }

  @Test
  public void testMultipleConstructors() {
    // Given...
    InfluxDBResultMapper.cacheRecordClass(MultipleConstructors.class);

    List<String> columnList = Arrays.asList("i", "s");

    QueryResult.Series series = new QueryResult.Series();
    series.setColumns(columnList);
    List<Object> firstSeriesResult = Arrays.asList(9.0, "str");
    series.setValues(Arrays.asList(firstSeriesResult));

    //When...
    List<MultipleConstructors> result = new LinkedList<>();
    mapper.parseSeriesAs(series, MultipleConstructors.class, result);

    //Then...
    Assertions.assertTrue(result.size() == 1);

    Assert.assertEquals(9, result.get(0).i());
    Assert.assertEquals("str", result.get(0).s());
  }

  @Test
  public void testConflictingConstructors() {
    Assert.assertThrows(InfluxDBMapperException.class,
            () -> InfluxDBResultMapper.cacheRecordClass(ConflictingConstructors.class));
  }

  @Measurement(name = "HasTimeColumnMeasurement")
  static final class HasTimeColumnMeasurement extends RecordTag {
    @TimeColumn
    private final Instant time;
    private final Integer value;

    HasTimeColumnMeasurement(Instant time, Integer value) {
      this.time = time;
      this.value = value;
    }

    public Instant time() {
      return time;
    }

    public Integer value() {
      return value;
    }
  }

  @Measurement(name = "CustomMeasurement")
  static final class MyCustomMeasurement extends RecordTag {
    private final Instant time;
    private final String uuid;
    private final Double doubleObject;
    private final Long longObject;
    private final Integer integerObject;
    private final Boolean booleanObject;

    @SuppressWarnings("unused")
    private final String nonColumn1;

    @SuppressWarnings("unused")
    private final Random rnd;

    MyCustomMeasurement(
            Instant time,
            String uuid,
            Double doubleObject,
            Long longObject,
            Integer integerObject,
            Boolean booleanObject,

            @SuppressWarnings("unused")
            String nonColumn1,

            @SuppressWarnings("unused")
            Random rnd) {
      this.time = time;
      this.uuid = uuid;
      this.doubleObject = doubleObject;
      this.longObject = longObject;
      this.integerObject = integerObject;
      this.booleanObject = booleanObject;
      this.nonColumn1 = nonColumn1;
      this.rnd = rnd;
    }

    public Instant time() {
      return time;
    }

    public String uuid() {
      return uuid;
    }

    public Double doubleObject() {
      return doubleObject;
    }

    public Long longObject() {
      return longObject;
    }

    public Integer integerObject() {
      return integerObject;
    }

    public Boolean booleanObject() {
      return booleanObject;
    }

    @SuppressWarnings("unused")
    public String nonColumn1() {
      return nonColumn1;
    }

    @SuppressWarnings("unused")
    public Random rnd() {
      return rnd;
    }
  }

  @Measurement(name = "CustomMeasurement")
  static final class MyCustomMeasurementWithPrimitives extends RecordTag {
    private final Instant time;
    private final String uuid;
    private final Double doubleObject;
    private final Long longObject;
    private final Integer integerObject;
    private final double doublePrimitive;
    private final long longPrimitive;
    private final int integerPrimitive;
    private final Boolean booleanObject;
    private final boolean booleanPrimitive;

    @SuppressWarnings("unused")
    private final String nonColumn1;

    @SuppressWarnings("unused")
    private final Random rnd;

    MyCustomMeasurementWithPrimitives(
            Instant time,
            String uuid,
            Double doubleObject,
            Long longObject,
            Integer integerObject,
            double doublePrimitive,
            long longPrimitive,
            int integerPrimitive,
            Boolean booleanObject,
            boolean booleanPrimitive,

            @SuppressWarnings("unused")
            String nonColumn1,

            @SuppressWarnings("unused")
            Random rnd) {
      this.time = time;
      this.uuid = uuid;
      this.doubleObject = doubleObject;
      this.longObject = longObject;
      this.integerObject = integerObject;
      this.doublePrimitive = doublePrimitive;
      this.longPrimitive = longPrimitive;
      this.integerPrimitive = integerPrimitive;
      this.booleanObject = booleanObject;
      this.booleanPrimitive = booleanPrimitive;
      this.nonColumn1 = nonColumn1;
      this.rnd = rnd;
    }

    public Instant time() {
      return time;
    }

    public String uuid() {
      return uuid;
    }

    public Double doubleObject() {
      return doubleObject;
    }

    public Long longObject() {
      return longObject;
    }

    public Integer integerObject() {
      return integerObject;
    }

    public double doublePrimitive() {
      return doublePrimitive;
    }

    public long longPrimitive() {
      return longPrimitive;
    }

    public int integerPrimitive() {
      return integerPrimitive;
    }

    public Boolean booleanObject() {
      return booleanObject;
    }

    public boolean booleanPrimitive() {
      return booleanPrimitive;
    }

    @SuppressWarnings("unused")
    public String nonColumn1() {
      return nonColumn1;
    }

    @SuppressWarnings("unused")
    public Random rnd() {
      return rnd;
    }
  }

  @Measurement(name = "foo")
  static final class MyRecordWithUnsupportedField extends RecordTag {
    @Column(name = "bar")
    private final Date myDate;

    MyRecordWithUnsupportedField(Date myDate) {
      this.myDate = myDate;
    }

    public Date myDate() {
      return myDate;
    }
  }

  /**
   * Class created based on example from <a href="https://github.com/influxdata/influxdb-java/issues/343">this issue</a>
   */
  @Measurement(name = "tb_network")
  static final class GroupByCarrierDeviceOS extends RecordTag {
    private final Instant time;

    @Column(name = "CARRIER", tag = true)
    private final String carrier;

    @Column(name = "DEVICE_OS_VERSION", tag = true)
    private final String deviceOsVersion;

    private final Double median;
    private final Double min;
    private final Double max;

    GroupByCarrierDeviceOS(
            Instant time,
            String carrier,
            String deviceOsVersion,
            Double median,
            Double min,
            Double max) {
      this.time = time;
      this.carrier = carrier;
      this.deviceOsVersion = deviceOsVersion;
      this.median = median;
      this.min = min;
      this.max = max;
    }

    public Instant time() {
      return time;
    }

    public String carrier() {
      return carrier;
    }

    public String deviceOsVersion() {
      return deviceOsVersion;
    }

    public Double median() {
      return median;
    }

    public Double min() {
      return min;
    }

    public Double max() {
      return max;
    }
  }

  static final class MultipleConstructors extends RecordTag {
    private final int i;
    private final String s;

    MultipleConstructors(int i, String s) {
      this.i = i;
      this.s = s;
    }

    MultipleConstructors(String i, String s) {
      this(Integer.parseInt(i), s);
    }

    MultipleConstructors(int i, String s, double d) {
      this(i, s);
    }

    int i() {
      return i;
    }

    String s() {
      return s;
    }
  }


  static final class ConflictingConstructors extends RecordTag {
    private final int i;
    private final String s;

    private ConflictingConstructors(int i, String s) {
      this.i = i;
      this.s = s;
    }

    private ConflictingConstructors(String s, int i) {
      this(i, s);
    }

    public int i() {
      return i;
    }

    public String s() {
      return s;
    }
  }
}