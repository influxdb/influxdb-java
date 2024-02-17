package org.influxdb.querybuilder;

import static org.influxdb.querybuilder.Operations.EQ;
import static org.influxdb.querybuilder.Operations.GT;
import static org.influxdb.querybuilder.Operations.GTE;
import static org.influxdb.querybuilder.Operations.LT;
import static org.influxdb.querybuilder.Operations.LTE;
import static org.influxdb.querybuilder.Operations.NE;
import static org.influxdb.querybuilder.Operations.NEQ;

import org.influxdb.dto.Query;
import org.influxdb.querybuilder.clauses.AddRelativeTimeClause;
import org.influxdb.querybuilder.clauses.Clause;
import org.influxdb.querybuilder.clauses.ContainsClause;
import org.influxdb.querybuilder.clauses.NegativeRegexClause;
import org.influxdb.querybuilder.clauses.OperationClause;
import org.influxdb.querybuilder.clauses.RegexClause;
import org.influxdb.querybuilder.clauses.SimpleClause;
import org.influxdb.querybuilder.clauses.SubRelativeTimeClause;
import org.influxdb.querybuilder.time.TimeInterval;

public abstract class BuiltQuery extends Query implements QueryStringBuilder {

  public BuiltQuery(final String database) {
    super(null, database);
  }

  public BuiltQuery(final String database, final boolean requiresPost) {
    super(null, database, requiresPost);
  }

  @Override
  public String getCommand() {
    StringBuilder sb = buildQueryString(new StringBuilder());
    Utility.addSemicolonIfMissing(sb);
    return sb.toString();
  }

  @Override
  public String getCommandWithUrlEncoded() {
    return encode(getCommand());
  }

  /**
   * The query builder shall provide all the building blocks needed, only a static block shall be
   * used.
   */
  public static final class QueryBuilder {

    private QueryBuilder() {
    }

    public static SelectionQueryImpl select(final String... columns) {
      return select((Object[]) columns);
    }

    public static SelectionQueryImpl select(final Object... columns) {
      return new SelectionQueryImpl(new SelectionCoreImpl(columns));
    }

    public static Clause eq(final String name, final Object value) {
      return new SimpleClause(name, EQ, value);
    }

    public static Clause eq(final Object arg1, final Object arg2) {
      return new OperationClause(arg1, EQ, arg2);
    }

    public static Clause ne(final String name, final Object value) {
      return new SimpleClause(name, NE, value);
    }

    public static Clause neq(final String name, final Object value) {
      return new SimpleClause(name, NEQ, value);
    }

    public static Clause neq(final Object arg1, final Object arg2) {
      return new OperationClause(arg1, NEQ, arg2);
    }

    public static Clause ne(final Object arg1, final Object arg2) {
      return new OperationClause(arg1, NE, arg2);
    }

    public static Clause contains(final String name, final String value) {
      return new ContainsClause(name, value);
    }

    public static Clause regex(final String name, final String value) {
      return new RegexClause(name, value);
    }

    public static Clause nregex(final String name, final String value) {
      return new NegativeRegexClause(name, value);
    }

    public static Clause lt(final String name, final Object value) {
      return new SimpleClause(name, LT, value);
    }

    public static Clause lt(final Object arg1, final Object arg2) {
      return new OperationClause(arg1, LT, arg2);
    }

    public static Clause lte(final String name, final Object value) {
      return new SimpleClause(name, LTE, value);
    }

    public static Clause lte(final Object arg1, final Object arg2) {
      return new OperationClause(arg1, LTE, arg2);
    }

    public static Clause gt(final String name, final Object value) {
      return new SimpleClause(name, GT, value);
    }

    public static Clause gt(final Object arg1, final Object arg2) {
      return new OperationClause(arg1, GT, arg2);
    }

    public static Clause gte(final String name, final Object value) {
      return new SimpleClause(name, GTE, value);
    }

    public static Clause gte(final Object arg1, final Object arg2) {
      return new OperationClause(arg1, GTE, arg2);
    }

    public static Clause addTime(final long interval, final String literal) {
      return new AddRelativeTimeClause(new TimeInterval(interval, literal));
    }

    public static Clause subTime(final long interval, final String literal) {
      return new SubRelativeTimeClause(new TimeInterval(interval, literal));
    }

    /**
     *
     * @return the Ordering
     */
    public static Ordering asc() {
      return new Ordering(false);
    }

    /**
     * InfluxDB supports only time for ordering.
     *
     * @return the Ordering
     */
    public static Ordering desc() {
      return new Ordering(true);
    }

    public static Object raw(final String str) {
      return new RawText(str);
    }

    public static Object max(final Object column) {
      return FunctionFactory.max(column);
    }

    public static Object min(final Object column) {
      return FunctionFactory.min(column);
    }

    public static Object time(final Long timeInterval, final String durationLiteral) {
      return FunctionFactory.time(timeInterval, durationLiteral);
    }

    public static TimeInterval ti(final Long timeInterval, final String durationLiteral) {
      return new TimeInterval(timeInterval, durationLiteral);
    }

    public static SimpleClause cop(final String column, final String op, final Object arg2) {
      return new SimpleClause(column, op, arg2);
    }

    public static OperationClause op(final Object arg1, final String op, final Object arg2) {
      return new OperationClause(arg1, op, arg2);
    }

    public static Object time(
        final Long timeInterval,
        final String durationLiteral,
        final Long offsetInterval,
        final String offSetLiteral) {
      return FunctionFactory.time(timeInterval, durationLiteral, offsetInterval, offSetLiteral);
    }

    public static Object now() {
      return FunctionFactory.now();
    }
  }
}
