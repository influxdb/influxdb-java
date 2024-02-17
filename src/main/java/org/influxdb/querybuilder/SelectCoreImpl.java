package org.influxdb.querybuilder;

import static org.influxdb.querybuilder.Appender.appendValue;
import static org.influxdb.querybuilder.Appender.joinAndAppend;
import static org.influxdb.querybuilder.Appender.joinAndAppendNames;
import static org.influxdb.querybuilder.Utility.trimLast;
import static org.influxdb.querybuilder.FunctionFactory.function;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.influxdb.querybuilder.clauses.Clause;
import org.influxdb.querybuilder.clauses.FromClause;
import org.influxdb.querybuilder.clauses.RawTextClause;
import org.influxdb.querybuilder.clauses.SubQueryFromClause;

public class SelectCoreImpl<T extends Where> implements Select, QueryStringBuilder, WithSubquery {

  private FromClause table;
  private final boolean isDistinct;
  private final List<Object> columns;
  protected final T where;
  private final Optional<String> intoMeasurement;
  private Optional<Ordering> ordering = Optional.empty();
  private List<Object> groupByColumns;
  private Optional<Function> fill = Optional.empty();
  private Optional<Integer> limit = Optional.empty();
  private Optional<Long> offSet = Optional.empty();
  private Optional<Integer> sLimit = Optional.empty();
  private Optional<Long> sOffSet = Optional.empty();
  private Optional<TimeZone> timeZone = Optional.empty();

  SelectCoreImpl(final List<Object> columns, final boolean isDistinct, final T where) {
    this.columns = columns;
    this.isDistinct = isDistinct;
    this.where = where;
    this.intoMeasurement = Optional.empty();
  }

  SelectCoreImpl(
      final List<Object> columns,
      final boolean isDistinct,
      final T where,
      final String intoMeasurement) {
    this.columns = columns;
    this.isDistinct = isDistinct;
    this.where = where;
    if (intoMeasurement != null) {
      this.intoMeasurement = Optional.of(intoMeasurement);
    } else {
      this.intoMeasurement = Optional.empty();
    }
  }

  SelectCoreImpl(
      final FromClause table, final List<Object> columns, final boolean isDistinct, final T where) {
    this.table = table;
    this.columns = columns;
    this.isDistinct = isDistinct;
    this.where = where;
    this.intoMeasurement = Optional.empty();
  }

  SelectCoreImpl(
      final FromClause table,
      final List<Object> columns,
      final boolean isDistinct,
      final T where,
      final String intoMeasurement) {
    this.table = table;
    this.columns = columns;
    this.isDistinct = isDistinct;
    this.where = where;
    if (intoMeasurement != null) {
      this.intoMeasurement = Optional.of(intoMeasurement);
    } else {
      this.intoMeasurement = Optional.empty();
    }
  }

  @Override
  public T where() {
    return where;
  }

  @Override
  public T where(final Clause clause) {
    return where.and(clause);
  }

  @Override
  public T where(final String text) {
    return where.and(new RawTextClause(text));
  }

  @Override
  public Select orderBy(final Ordering ordering) {
    this.ordering = Optional.of(ordering);
    return this;
  }

  @Override
  public Select groupBy(final Object... columns) {
    this.groupByColumns = Arrays.asList(columns);
    return this;
  }

  @Override
  public Select fill(final Number value) {
    this.fill = Optional.of(function("fill", value));
    return this;
  }

  @Override
  public Select fill(final String value) {
    if ("linear".equals(value)
        || "none".equals(value)
        || "null".equals(value)
        || "previous".equals(value)) {
      this.fill = Optional.of(function("fill", value));
      return this;
    } else {
      throw new IllegalArgumentException(
          "Please give a numeric value or linear, none, null, previous");
    }
  }

  @Override
  public Select limit(final int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("Invalid LIMIT value, must be strictly positive");
    }

    if (this.limit.isPresent()) {
      throw new IllegalStateException("A LIMIT value has already been provided");
    }

    this.limit = Optional.of(limit);
    return this;
  }

  @Override
  public Select limit(final int limit, final long offSet) {
    if (limit <= 0 || offSet <= 0) {
      throw new IllegalArgumentException(
          "Invalid LIMIT and OFFSET Value, must be strictly positive");
    }

    this.limit = Optional.of(limit);
    this.offSet = Optional.of(offSet);
    return this;
  }

  @Override
  public Select sLimit(final int sLimit) {
    if (sLimit <= 0) {
      throw new IllegalArgumentException("Invalid SLIMIT value, must be strictly positive");
    }

    if (this.sLimit.isPresent()) {
      throw new IllegalStateException("A SLIMIT value has already been provided");
    }

    this.sLimit = Optional.of(sLimit);
    return this;
  }

  @Override
  public Select sLimit(final int sLimit, final long sOffSet) {
    if (sLimit <= 0 || sOffSet <= 0) {
      throw new IllegalArgumentException(
          "Invalid LIMIT and OFFSET Value, must be strictly positive");
    }

    this.sLimit = Optional.of(sLimit);
    this.sOffSet = Optional.of(sOffSet);
    return this;
  }

  @Override
  public Select tz(final String timezone) {
    this.timeZone = Optional.of(new TimeZone(timezone));
    return this;
  }

  @Override
  public void setSubQuery(final QueryStringBuilder query) {
    this.table = new SubQueryFromClause(query);
  }

  @Override
  public StringBuilder buildQueryString() {
    return buildQueryString(new StringBuilder());
  }

  @Override
  public StringBuilder buildQueryString(final StringBuilder builder) {
    builder.append("SELECT ");

    if (isDistinct) {
      if (columns.size() > 1) {
        throw new IllegalStateException("DISTINCT function can only be used with one column");
      }
    }

    if (columns == null || columns.size() == 0) {
      builder.append('*');
    } else {
      joinAndAppendNames(builder, columns);
    }

    if (intoMeasurement.isPresent()) {
      builder.append(" INTO ").append(intoMeasurement.get());
    }

    builder.append(" FROM ");

    if (table != null) {
      table.appendTo(builder);
    } else {
      throw new IllegalStateException();
    }

    if (!where.getClauses().isEmpty()) {
      builder.append(" WHERE ");
      joinAndAppend(builder, where.getClauses());
    }

    if (groupByColumns != null) {
      builder.append(" GROUP BY ");
      joinAndAppendNames(builder, groupByColumns);
    }

    if (fill.isPresent()) {
      builder.append(" ");
      appendValue(fill.get(), builder);
    }

    if (ordering.isPresent()) {
      builder.append(" ORDER BY ");
      joinAndAppend(builder, ",", Collections.singletonList(ordering.get()));
    }

    if (limit.isPresent()) {
      builder.append(" LIMIT ").append(limit.get());
    }

    if (offSet.isPresent()) {
      builder.append(" OFFSET ").append(offSet.get());
    }

    if (sLimit.isPresent()) {
      builder.append(" SLIMIT ").append(sLimit.get());
    }

    if (sOffSet.isPresent()) {
      builder.append(" SOFFSET ").append(sOffSet.get());
    }

    if (timeZone.isPresent()) {
      timeZone.get().appendTo(builder);
    }

    trimLast(builder);
    return builder;
  }
}
