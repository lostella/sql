package org.opensearch.sql.api.examples;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.calcite.DataContext;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Statistic;
import org.apache.calcite.schema.Statistics;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.opensearch.sql.api.UnifiedQueryContext;
import org.opensearch.sql.api.UnifiedQueryPlanner;
import org.opensearch.sql.api.compiler.UnifiedQueryCompiler;
import org.opensearch.sql.executor.QueryType;

public class DateComparisonBugExample {

  public static void main(String[] args) throws Exception {
    Map<String, SqlTypeName> schema = new LinkedHashMap<>();
    schema.put("id", SqlTypeName.INTEGER);
    schema.put("year_hired", SqlTypeName.INTEGER);

    List<Object[]> rows = List.of(new Object[] {1, 2020}, new Object[] {2, 2022});

    AbstractSchema testSchema =
        new AbstractSchema() {
          @Override
          protected Map<String, Table> getTableMap() {
            return Map.of("employees", new SimpleTable(schema, rows));
          }
        };

    try (UnifiedQueryContext context =
        UnifiedQueryContext.builder()
            .language(QueryType.PPL)
            .catalog("test", testSchema)
            .defaultNamespace("test")
            .build()) {

      UnifiedQueryPlanner planner = new UnifiedQueryPlanner(context);
      UnifiedQueryCompiler compiler = new UnifiedQueryCompiler(context);

      String query = "source=employees | where year_hired > 2020";
      PreparedStatement stmt = compiler.compile(planner.plan(query));
      ResultSet rs = stmt.executeQuery();

      while (rs.next()) {
        System.out.println("id=" + rs.getInt(1) + ", year_hired=" + rs.getInt(2));
      }
    }
  }

  private static class SimpleTable implements ScannableTable {
    private final Map<String, SqlTypeName> schema;
    private final List<Object[]> rows;

    SimpleTable(Map<String, SqlTypeName> schema, List<Object[]> rows) {
      this.schema = schema;
      this.rows = rows;
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
      RelDataTypeFactory.Builder builder = typeFactory.builder();
      schema.forEach((name, type) -> builder.add(name, typeFactory.createSqlType(type)));
      return builder.build();
    }

    @Override
    public Enumerable<Object[]> scan(DataContext root) {
      return Linq4j.asEnumerable(rows);
    }

    @Override
    public Statistic getStatistic() {
      return Statistics.UNKNOWN;
    }

    @Override
    public Schema.TableType getJdbcTableType() {
      return Schema.TableType.TABLE;
    }

    @Override
    public boolean isRolledUp(String column) {
      return false;
    }

    @Override
    public boolean rolledUpColumnValidInsideAgg(
        String column, SqlCall call, SqlNode parent, CalciteConnectionConfig config) {
      return false;
    }
  }
}
