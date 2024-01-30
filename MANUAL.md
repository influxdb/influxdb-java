# Manual

## Quick start

The code below is similar to the one found on the README.md file but with comments removed and rows numbered for better reference.

```Java
final String serverURL = "http://127.0.0.1:8086", username = "root", password = "root";
final InfluxDB influxDB = InfluxDBFactory.connect(serverURL, username, password);  // (1)

String databaseName = "NOAA_water_database";
influxDB.query(new Query("CREATE DATABASE " + databaseName));
influxDB.setDatabase(databaseName);                                       // (2)

String retentionPolicyName = "one_day_only";
influxDB.query(new Query("CREATE RETENTION POLICY " + retentionPolicyName
        + " ON " + databaseName + " DURATION 1d REPLICATION 1 DEFAULT"));
influxDB.setRetentionPolicy(retentionPolicyName);                         // (3)

influxDB.enableBatch(
    BatchOptions.DEFAULTS
        .threadFactory(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        })
);                                                                        // (4)

Runtime.getRuntime().addShutdownHook(new Thread(influxDB::close));        // (5)

influxDB.write(Point.measurement("h2o_feet")                              // (6)
    .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    .tag("location", "santa_monica")
    .addField("level description", "below 3 feet")
    .addField("water_level", 2.064d)
    .build());

influxDB.write(Point.measurement("h2o_feet")                              // (6)
    .tag("location", "coyote_creek")
    .addField("level description", "between 6 and 9 feet")
    .addField("water_level", 8.12d)
    .build());

Thread.sleep(5_000L);

QueryResult queryResult = influxDB.query(new Query("SELECT * FROM h2o_feet")); // (7)

System.out.println(queryResult);
// It will print something like:
// QueryResult [results=[Result [series=[Series [name=h2o_feet, tags=null,
//      columns=[time, level description, location, water_level],
//      values=[
//      [2020-03-22T20:50:12.929Z, below 3 feet, santa_monica, 2.064],
//      [2020-03-22T20:50:12.929Z, between 6 and 9 feet, coyote_creek, 8.12]
//      ]]], error=null]], error=null]
```

### Connecting to InfluxDB

(1) The `InfluxDB` client is thread-safe and our recommendation is to have a single instance per application and reuse it, when possible. Every `InfluxDB` instance keeps multiple data structures, including those used to manage different pools like HTTP clients for reads and writes.

It's possible to have just one client even when reading or writing to multiple InfluxDB databases and this will be shown later here.

### Setting a default database (optional)

(2) If you are not querying different databases with a single `InfluxDB` client, it's possible to set a default database name and all queries (reads and writes) from this `InfluxDB` client will be executed against the default database.

If we only comment out the line (2) then all reads and writes queries would fail. To avoid this, we need to pass the database name as parameter to `BatchPoints` (writes) and to `Query` (reads). For example:

```Java
// ...
String databaseName = "NOAA_water_database";
// influxDB.setDatabase() won't be called...
String retentionPolicyName = "one_day_only";
// ...

BatchPoints batchPoints = BatchPoints.database(databaseName).retentionPolicy(retentionPolicyName).build();

batchPoints.point(Point.measurement("h2o_feet")
    .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
    .tag("location", "santa_monica")
    .addField("level description", "below 3 feet")
    .addField("water_level", 2.064d)
    .build());

// ...
influxDB.write(batchPoints);
// ...
QueryResult queryResult = influxDB.query(new Query("SELECT * FROM h2o_feet", databaseName));
// ...
influxDB.close();
```

It's possible to use both approaches at the same time: set a default database using `influxDB.setDatabase` and read/write passing a `databaseName` as parameter. On this case, the `databaseName` passed as parameter will be used.

### Setting a default retention policy (optional)

(3) TODO: like setting a default database, explain here how it works with RP.

### Enabling batch writes

(4) TODO: explanation about BatchOption parameters:

```Java
  // default values here are consistent with Telegraf
  public static final int DEFAULT_BATCH_ACTIONS_LIMIT = 1000;
  public static final int DEFAULT_BATCH_INTERVAL_DURATION = 1000;
  public static final int DEFAULT_JITTER_INTERVAL_DURATION = 0;
  public static final int DEFAULT_BUFFER_LIMIT = 10000;
  public static final TimeUnit DEFAULT_PRECISION = TimeUnit.NANOSECONDS;
  public static final boolean DEFAULT_DROP_ACTIONS_ON_QUEUE_EXHAUSTION = false;
```
#### Configuring behaviour of batch writes when the action queue exhausts
With batching enabled, the client provides two options on how to deal with **action queue** (where the points are accumulated as a batch) exhaustion.
1. When `dropActionsOnQueueExhaustion` is `false` (default value), `InfluxDB#write` will be blocked till the space is created in the action queue.
2. When `dropActionsOnQueueExhaustion` is `true`, new writes using `InfluxDB#write` will dropped and `droppedActionHandler` will be called.
   Example usage:
   ```Java
   influxDB.enableBatch(BatchOptions.DEFAULTS.dropActionsOnQueueExhaustion(true)
                                             .droppedActionHandler((point) -> log.error("Point dropped due to action queue exhaustion.")));
   ```
    

#### Configuring the jitter interval for batch writes

When using large number of influxdb-java clients against a single server it may happen that all the clients
will submit their buffered points at the same time and possibly overloading the server. This is usually happening
when all the clients are started at once - for instance as members of cloud hosted large cluster networks.  
If all the clients have the same flushDuration set this situation will repeat periodically.

To solve this situation the influxdb-java offers an option to offset the flushDuration by a random interval so that
the clients will flush their buffers in different intervals:

```Java
influxDB.enableBatch(BatchOptions.DEFAULTS.jitterDuration(500));
```

#### Error handling with batch writes

With batching enabled the client provides two strategies how to deal with errors thrown by the InfluxDB server.

   1. 'One shot' write - on failed write request to InfluxDB server an error is reported to the client using the means mentioned above.
   2. 'Retry on error' write (used by default) - on failed write the request by the client is repeated after batchInterval elapses (if there is a chance the write will succeed - the error was caused by overloading the server, a network error etc.)
       When new data points are written before the previous (failed) points are successfully written, those are queued inside the client and wait until older data points are successfully written.
       Size of this queue is limited and configured by `BatchOptions.bufferLimit` property. When the limit is reached, the oldest points in the queue are dropped. 'Retry on error' strategy is used when individual write batch size defined by `BatchOptions.actions` is lower than `BatchOptions.bufferLimit`.

#### Ensure application exit when batching is enabled
`BatchOptions.DEFAULTS` creates a non-daemon thread pool which prevents the JVM from initiating shutdown in the case of
exceptions or successful completion of the main thread. This will prevent shutdown hooks (many frameworks and plain JVM
applications use these to close/ cleanup resources) from running, preventing graceful termination of the application.

Thus, configuring batch options with a daemon thread pool will solve this issue and will for example ensure that the registered
(5) shutdown hook is run to close the `InfluxDB` client properly (flushing and closing of resources will happen). 

### Close InfluxDB Client on JVM Termination
(5) In order to ensure that in-flight points are flushed and resources are released properly, it is essential to call 
`influxDB.close()` the client when it is no longer required.

Registering a shutdown hook is a good way to ensure that this is done on application termination regardless of exceptions
that are thrown in the main thread of the code. Note that if you are using a framework, do check the documentation for its
way of configuring shutdown lifecycle hooks or if it might already be calling `close` automatically.


### Writing to InfluxDB

(6) ...

`----8<----BEGIN DRAFT----8<----`

Any errors that happen during the batch flush won't leak into the caller of the `write` method. By default, any kind of errors will be just logged with "SEVERE" level.
If you need to be notified and do some custom logic when such asynchronous errors happen, you can add an error handler with a `BiConsumer<Iterable<Point>, Throwable>` using the overloaded `enableBatch` method:

```Java
influxDB.enableBatch(BatchOptions.DEFAULTS.exceptionHandler(
        (failedPoints, throwable) -> { /* custom error handling here */ })
);
```

`----8<----END DRAFT----8<----`

#### Writing synchronously to InfluxDB (not recommended)

If you want to write the data points synchronously to InfluxDB and handle the errors (as they may happen) with every write:

`----8<----BEGIN DRAFT----8<----`

```Java
InfluxDB influxDB = InfluxDBFactory.connect("http://172.17.0.2:8086", "root", "root");
String dbName = "aTimeSeries";
influxDB.query(new Query("CREATE DATABASE " + dbName));
String rpName = "aRetentionPolicy";
influxDB.query(new Query("CREATE RETENTION POLICY " + rpName + " ON " + dbName + " DURATION 30h REPLICATION 2 DEFAULT"));

BatchPoints batchPoints = BatchPoints
                .database(dbName)
                .tag("async", "true")
                .retentionPolicy(rpName)
                .consistency(ConsistencyLevel.ALL)
                .build();
Point point1 = Point.measurement("cpu")
                    .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .addField("idle", 90L)
                    .addField("user", 9L)
                    .addField("system", 1L)
                    .build();
Point point2 = Point.measurement("disk")
                    .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .addField("used", 80L)
                    .addField("free", 1L)
                    .build();
batchPoints.point(point1);
batchPoints.point(point2);
influxDB.write(batchPoints);
Query query = new Query("SELECT idle FROM cpu", dbName);
influxDB.query(query);
influxDB.query(new Query("DROP RETENTION POLICY " + rpName + " ON " + dbName));
influxDB.query(new Query("DROP DATABASE " + dbName));
```

`----8<----END DRAFT----8<----`

### Reading from InfluxDB

(7) ...

#### Query using Callbacks

influxdb-java now supports returning results of a query via callbacks. Only one
of the following consumers are going to be called once :

```Java
this.influxDB.query(new Query("SELECT idle FROM cpu", dbName), queryResult -> {
    // Do something with the result...
}, throwable -> {
    // Do something with the error...
});
```

#### Query using parameter binding (a.k.a. "prepared statements")

If your Query is based on user input, it is good practice to use parameter binding to avoid [injection attacks](https://en.wikipedia.org/wiki/SQL_injection).
You can create queries with parameter binding with the help of the QueryBuilder:

```Java
Query query = QueryBuilder.newQuery("SELECT * FROM cpu WHERE idle > $idle AND system > $system")
        .forDatabase(dbName)
        .bind("idle", 90)
        .bind("system", 5)
        .create();
QueryResult results = influxDB.query(query);
```

The values of the bind() calls are bound to the placeholders in the query ($idle, $system).

## Advanced Usage

### Gzip's support

influxdb-java client doesn't enable gzip compress for http request body by default. If you want to enable gzip to reduce transfer data's size , you can call:

```Java
influxDB.enableGzip()
```

### UDP's support

influxdb-java client support udp protocol now. you can call following methods directly to write through UDP.

```Java
public void write(final int udpPort, final String records);
public void write(final int udpPort, final List<String> records);
public void write(final int udpPort, final Point point);
```

Note: make sure write content's total size should not > UDP protocol's limit(64K), or you should use http instead of udp.

### Chunking support

influxdb-java client now supports influxdb chunking. The following example uses a chunkSize of 20 and invokes the specified Consumer (e.g. System.out.println) for each received QueryResult

```Java
Query query = new Query("SELECT idle FROM cpu", dbName);
influxDB.query(query, 20, queryResult -> System.out.println(queryResult));
```

### QueryResult mapper to POJO

An alternative way to handle the QueryResult object is now available.
Supposing that you have a measurement _CPU_:

```sql
> INSERT cpu,host=serverA,region=us_west idle=0.64,happydevop=false,uptimesecs=123456789i
>
> select * from cpu
name: cpu
time                           happydevop host    idle region  uptimesecs
----                           ---------- ----    ---- ------  ----------
2017-06-20T15:32:46.202829088Z false      serverA 0.64 us_west 123456789
```

And the following tag keys:

```sql
> show tag keys from cpu
name: cpu
tagKey
------
host
region
```

1. Create a POJO to represent your measurement. For example:

```Java
public class Cpu {
    private Instant time;
    private String hostname;
    private String region;
    private Double idle;
    private Boolean happydevop;
    private Long uptimeSecs;
    // getters (and setters if you need)
}
```

2. Add @Measurement, @TimeColumn and @Column annotations (column names default to field names unless otherwise specified):

```Java
@Measurement(name = "cpu")
public class Cpu {
    @TimeColumn
    @Column
    private Instant time;
    @Column(name = "host", tag = true)
    private String hostname;
    @Column(tag = true)
    private String region;
    @Column
    private Double idle;
    @Column
    private Boolean happydevop;
    @Column(name = "uptimesecs")
    private Long uptimeSecs;
    // getters (and setters if you need)
}
```

Alternatively, you can use:

```Java
@Measurement(name = "cpu", allFields = true)
public class Cpu {
    @TimeColumn
    private Instant time;
    @Column(name = "host", tag = true)
    private String hostname;
    @Column(tag = true)
    private String region;
    private Double idle;
    private Boolean happydevop;
    @Column(name = "uptimesecs")
    private Long uptimeSecs;
    // getters (and setters if you need)
}
```

Or (if you're on JDK14+ and/or [Android SDK34+](https://android-developers.googleblog.com/2023/06/records-in-android-studio-flamingo.html)):

```Java
@Measurement(name = "cpu", allFields = true)
public record Cpu(
    @TimeColumn
    Instant time,
    @Column(name = "host", tag = true)
    String hostname,
    @Column(tag = true)
    String region,
    Double idle,
    Boolean happydevop,
    @Column(name = "uptimesecs")
    Long uptimeSecs
) {}
```

3. Call _InfluxDBResultMapper.toPOJO(...)_ to map the QueryResult to your POJO:

```java
InfluxDB influxDB = InfluxDBFactory.connect("http://localhost:8086", "root", "root");
String dbName = "myTimeseries";
QueryResult queryResult = influxDB.query(new Query("SELECT * FROM cpu", dbName));

InfluxDBResultMapper resultMapper = new InfluxDBResultMapper(); // thread-safe - can be reused
List<Cpu> cpuList = resultMapper.toPOJO(queryResult, Cpu.class);
```

### Writing using POJO

The same way we use `annotations` to transform data to POJO, we can write data as POJO.
Having the same POJO class Cpu

```java
String dbName = "myTimeseries";
String rpName = "aRetentionPolicy";
// Cpu has annotations @Measurement,@TimeColumn and @Column
Cpu cpu = new Cpu();
// ... setting data

Point point = Point.measurementByPOJO(cpu.getClass()).addFieldsFromPOJO(cpu).build();

influxDB.write(dbName, rpName, point);
```

#### QueryResult mapper limitations

* If your InfluxDB query contains multiple SELECT clauses, you will have to call InfluxResultMapper#toPOJO() multiple times to map every measurement returned by QueryResult to the respective POJO;
* If your InfluxDB query contains multiple SELECT clauses **for the same measurement**, InfluxResultMapper will process all results because there is no way to distinguish which one should be mapped to your POJO. It may result in an invalid collection being returned;
* A Class field annotated with _@Column(..., tag = true)_ (i.e. a [InfluxDB Tag](https://docs.influxdata.com/influxdb/v1.2/concepts/glossary/#tag-value)) must be declared as _String_.

#### QueryBuilder

An alternative way to create InfluxDB queries is available. By using the [QueryBuilder](QUERY_BUILDER.md) you can create queries using java instead of providing the influxdb queries as strings.

#### Generic POJO super classes

POJO classes can have generic super classes, for cases where multiple measurements have a similar structure, and differ by type(s), as in:

```java
public class SuperMeasurement<T> {
    @Column
    @TimeColumn
    private Instant time;
    @Column
    T value;
    // Other common columns and tags
}

public class SubMeasurement extends SuperMeasurement<String> {
    // Any specific columns and tags
}
```

### InfluxDBMapper

In case you want to save and load data using models you can use the [InfluxDBMapper](INFLUXDB_MAPPER.md).

### Other Usages

For additional usage examples have a look at [InfluxDBTest.java](https://github.com/influxdb/influxdb-java/blob/master/src/test/java/org/influxdb/InfluxDBTest.java "InfluxDBTest.java")

### Publishing

This is a
[link](https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide)
to the sonatype oss guide to publishing. I'll update this section once
the [jira ticket](https://issues.sonatype.org/browse/OSSRH-9728) is
closed and I'm able to upload artifacts to the sonatype repositories.

### Frequently Asked Questions

This is a [FAQ](FAQ.md) list for influxdb-java.
