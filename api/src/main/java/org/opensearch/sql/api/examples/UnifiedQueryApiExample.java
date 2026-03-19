/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.api.examples;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.calcite.DataContext;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.RelNode;
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

/**
 * Comprehensive example demonstrating the Unified Query API for executing PPL queries over
 * in-memory data. This example showcases:
 *
 * <ul>
 *   <li>Creating tables with various data types (INTEGER, VARCHAR, DOUBLE, BOOLEAN, DATE,
 *       TIMESTAMP, DECIMAL)
 *   <li>Executing different PPL query flavors (filtering, aggregation, sorting, projection, eval)
 *   <li>Handling query errors gracefully
 * </ul>
 *
 * <p>Run this class directly to see PPL queries executed with their results printed to stdout.
 */
public class UnifiedQueryApiExample {

  private static final String CATALOG_NAME = "mydata";
  private static final String DIVIDER = "=".repeat(80);
  private static final String THIN_DIVIDER = "-".repeat(80);

  public static void main(String[] args) {
    new UnifiedQueryApiExample().run();
  }

  /** Runs all example queries and prints results. */
  public void run() {
    System.out.println(DIVIDER);
    System.out.println("Unified Query API - PPL Examples");
    System.out.println(DIVIDER);
    System.out.println();

    // Create schema with sample tables
    AbstractSchema schema = createSchema();

    try (UnifiedQueryContext context =
        UnifiedQueryContext.builder()
            .language(QueryType.PPL)
            .catalog(CATALOG_NAME, schema)
            .defaultNamespace(CATALOG_NAME)
            .build()) {

      UnifiedQueryPlanner planner = new UnifiedQueryPlanner(context);
      UnifiedQueryCompiler compiler = new UnifiedQueryCompiler(context);

      // Print table schemas for reference
      printTableInfo();

      // ==================== SUCCESSFUL QUERIES ====================

      System.out.println("\n" + DIVIDER);
      System.out.println("SUCCESSFUL QUERIES");
      System.out.println(DIVIDER);

      // 1. Simple SELECT ALL
      executeQuery(planner, compiler, "Basic: Select all employees", "source = employees");

      // 2. Field projection
      executeQuery(
          planner,
          compiler,
          "Projection: Select specific fields",
          "source = employees | fields name, department, salary");

      // 3. Filtering with WHERE
      executeQuery(
          planner,
          compiler,
          "Filter: Employees older than 30",
          "source = employees | where age > 30");

      // 4. Multiple filter conditions
      executeQuery(
          planner,
          compiler,
          "Filter: Engineering employees with salary > 80000",
          "source = employees | where department = 'Engineering' and salary > 80000");

      // 5. Boolean field filtering
      executeQuery(
          planner,
          compiler,
          "Filter: Active employees only",
          "source = employees | where is_active = true | fields name, is_active");

      // 6. Sorting ascending
      executeQuery(
          planner,
          compiler,
          "Sort: Employees by age ascending",
          "source = employees | sort age | fields name, age");

      // 7. Sorting descending
      executeQuery(
          planner,
          compiler,
          "Sort: Employees by salary descending",
          "source = employees | sort - salary | fields name, salary");

      // 8. Limit results (head)
      executeQuery(
          planner,
          compiler,
          "Limit: Top 3 highest paid employees",
          "source = employees | sort - salary | head 3 | fields name, salary");

      // 9. Count aggregation
      executeQuery(
          planner,
          compiler,
          "Aggregate: Count employees by department",
          "source = employees | stats count() by department");

      // 10. Sum aggregation
      executeQuery(
          planner,
          compiler,
          "Aggregate: Total salary by department",
          "source = employees | stats sum(salary) by department");

      // 11. Average aggregation
      executeQuery(
          planner,
          compiler,
          "Aggregate: Average age by department",
          "source = employees | stats avg(age) by department");

      // 12. Multiple aggregations
      executeQuery(
          planner,
          compiler,
          "Aggregate: Min and max salary by department",
          "source = employees | stats min(salary), max(salary) by department");

      // 13. Eval - computed fields
      executeQuery(
          planner,
          compiler,
          "Eval: Calculate annual bonus (10% of salary)",
          "source = employees | eval bonus = salary * 0.10 | fields name, salary, bonus");

      // 14. Eval with string operations
      executeQuery(
          planner,
          compiler,
          "Eval: Uppercase department names",
          "source = employees | eval dept_upper = upper(department) | fields name, dept_upper");

      // 15. String concatenation
      executeQuery(
          planner,
          compiler,
          "String function: Create full employee description",
          "source = employees | eval description = concat(name, ' - ', department) | fields description, salary");

      // 16. Complex query combining multiple operations
      executeQuery(
          planner,
          compiler,
          "Complex: Active engineers, sorted by salary, top 3",
          "source = employees | where department = 'Engineering' and is_active = true "
              + "| sort - salary | head 3 | fields name, salary");

      // 17. Aggregation with filtering
      executeQuery(
          planner,
          compiler,
          "Aggregate: Average salary for employees over 30",
          "source = employees | where age > 30 | stats avg(salary)");

      // 18. Distinct-like behavior using stats
      executeQuery(
          planner,
          compiler,
          "Distinct: List all departments",
          "source = employees | stats count() by department | fields department");

      // 19. Query the products table (different schema)
      executeQuery(planner, compiler, "Products: All products with prices", "source = products");

      // 20. Products with decimal arithmetic
      executeQuery(
          planner,
          compiler,
          "Products: Calculate discounted prices (20% off)",
          "source = products | eval discounted = price * 0.80 | fields name, price, discounted");

      // ==================== ERROR CASES ====================

      System.out.println("\n" + DIVIDER);
      System.out.println("ERROR CASES (Expected Failures)");
      System.out.println(DIVIDER);

      // Error 1: Date comparison limitation
      // Note: DATE columns use internal integer representation in Calcite's Enumerable execution,
      // which doesn't directly compare with DATE() function results. Use TIMESTAMP columns instead.
      executeQuery(
          planner,
          compiler,
          "Error: DATE column comparison (known limitation)",
          "source = employees | where hire_date > DATE('2021-01-01') | fields name, hire_date");

      // Error 2: Table does not exist
      executeQuery(planner, compiler, "Error: Non-existent table", "source = nonexistent_table");

      // Error 3: Field does not exist
      executeQuery(
          planner,
          compiler,
          "Error: Non-existent field",
          "source = employees | where nonexistent_field = 'value'");

      // Error 4: Syntax error - missing source
      executeQuery(
          planner, compiler, "Error: Missing source keyword", "employees | where age > 30");

      // Error 5: Syntax error - invalid operator
      executeQuery(
          planner,
          compiler,
          "Error: Invalid comparison operator",
          "source = employees | where age === 30");

      // Error 6: Type mismatch in comparison
      executeQuery(
          planner,
          compiler,
          "Error: Type mismatch (comparing string to number)",
          "source = employees | where name > 100");

      // Error 7: Invalid aggregation function
      executeQuery(
          planner,
          compiler,
          "Error: Unknown aggregation function",
          "source = employees | stats unknown_func(salary)");

      // Error 8: Incomplete query
      executeQuery(
          planner, compiler, "Error: Incomplete pipe expression", "source = employees | where");

      // Error 9: Invalid field in stats
      executeQuery(
          planner,
          compiler,
          "Error: Aggregate on non-existent field",
          "source = employees | stats sum(fake_column)");

      System.out.println("\n" + DIVIDER);
      System.out.println("Example completed!");
      System.out.println(DIVIDER);

    } catch (Exception e) {
      System.err.println("Fatal error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Executes a single PPL query and prints the result or error.
   *
   * @param planner the query planner
   * @param compiler the query compiler
   * @param description human-readable description of the query
   * @param pplQuery the PPL query to execute
   */
  private void executeQuery(
      UnifiedQueryPlanner planner,
      UnifiedQueryCompiler compiler,
      String description,
      String pplQuery) {

    System.out.println("\n" + THIN_DIVIDER);
    System.out.println(">> " + description);
    System.out.println("   Query: " + pplQuery);
    System.out.println(THIN_DIVIDER);

    try {
      // Plan the query
      RelNode plan = planner.plan(pplQuery);

      // Compile and execute
      try (PreparedStatement stmt = compiler.compile(plan)) {
        ResultSet rs = stmt.executeQuery();
        printResultSet(rs);
      }

    } catch (Exception e) {
      System.out.println("ERROR: " + e.getClass().getSimpleName());
      System.out.println("Message: " + getRootCauseMessage(e));
    }
  }

  /**
   * Prints a ResultSet in a formatted table.
   *
   * @param rs the ResultSet to print
   */
  private void printResultSet(ResultSet rs) throws Exception {
    ResultSetMetaData metaData = rs.getMetaData();
    int columnCount = metaData.getColumnCount();

    // Calculate column widths
    int[] widths = new int[columnCount];
    String[] headers = new String[columnCount];

    for (int i = 1; i <= columnCount; i++) {
      headers[i - 1] = metaData.getColumnName(i);
      widths[i - 1] = Math.max(headers[i - 1].length(), 12);
    }

    // Print header
    StringBuilder headerLine = new StringBuilder("| ");
    StringBuilder separatorLine = new StringBuilder("+-");
    for (int i = 0; i < columnCount; i++) {
      headerLine.append(String.format("%-" + widths[i] + "s", headers[i])).append(" | ");
      separatorLine.append("-".repeat(widths[i])).append("-+-");
    }
    System.out.println(separatorLine);
    System.out.println(headerLine);
    System.out.println(separatorLine);

    // Print rows
    int rowCount = 0;
    while (rs.next()) {
      StringBuilder rowLine = new StringBuilder("| ");
      for (int i = 1; i <= columnCount; i++) {
        Object value = rs.getObject(i);
        String strValue = value == null ? "NULL" : formatValue(value);
        // Truncate long values
        if (strValue.length() > widths[i - 1]) {
          strValue = strValue.substring(0, widths[i - 1] - 2) + "..";
        }
        rowLine.append(String.format("%-" + widths[i - 1] + "s", strValue)).append(" | ");
      }
      System.out.println(rowLine);
      rowCount++;
    }
    System.out.println(separatorLine);
    System.out.println("Rows returned: " + rowCount);
  }

  /**
   * Formats a value for display.
   *
   * @param value the value to format
   * @return formatted string representation
   */
  private String formatValue(Object value) {
    if (value instanceof Double) {
      return String.format("%.2f", value);
    } else if (value instanceof BigDecimal) {
      return ((BigDecimal) value).setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }
    return value.toString();
  }

  /**
   * Gets the root cause message from an exception chain.
   *
   * @param e the exception
   * @return the root cause message
   */
  private String getRootCauseMessage(Throwable e) {
    Throwable cause = e;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    String message = cause.getMessage();
    return message != null ? message : cause.getClass().getSimpleName();
  }

  /** Prints information about the sample tables. */
  private void printTableInfo() {
    System.out.println("SAMPLE DATA TABLES");
    System.out.println(THIN_DIVIDER);
    System.out.println();

    System.out.println("Table: employees");
    System.out.println("  Columns:");
    System.out.println("    - id          : INTEGER (Employee ID)");
    System.out.println("    - name        : VARCHAR (Full name)");
    System.out.println("    - age         : INTEGER (Age in years)");
    System.out.println("    - department  : VARCHAR (Department name)");
    System.out.println("    - salary      : DOUBLE  (Annual salary)");
    System.out.println("    - is_active   : BOOLEAN (Currently employed)");
    System.out.println("    - hire_date   : DATE    (Date of hiring)");
    System.out.println("    - last_login  : TIMESTAMP (Last system login)");
    System.out.println("  Records: 8 employees");
    System.out.println();

    System.out.println("Table: products");
    System.out.println("  Columns:");
    System.out.println("    - id          : INTEGER (Product ID)");
    System.out.println("    - name        : VARCHAR (Product name)");
    System.out.println("    - price       : DECIMAL (Unit price)");
    System.out.println("    - in_stock    : BOOLEAN (Availability)");
    System.out.println("    - category    : VARCHAR (Product category)");
    System.out.println("  Records: 5 products");
  }

  /**
   * Creates the sample schema with employees and products tables.
   *
   * @return an AbstractSchema containing the sample tables
   */
  private AbstractSchema createSchema() {
    return new AbstractSchema() {
      @Override
      protected Map<String, Table> getTableMap() {
        return Map.of(
            "employees", createEmployeesTable(),
            "products", createProductsTable());
      }
    };
  }

  /**
   * Creates the employees table with various data types.
   *
   * @return a ScannableTable with employee data
   */
  private Table createEmployeesTable() {
    // Sample employee data with diverse types
    List<Object[]> rows =
        List.of(
            new Object[] {
              1,
              "Alice Johnson",
              28,
              "Engineering",
              95000.0,
              true,
              Date.valueOf("2020-03-15"),
              Timestamp.valueOf("2024-01-15 09:30:00")
            },
            new Object[] {
              2,
              "Bob Smith",
              35,
              "Sales",
              75000.0,
              true,
              Date.valueOf("2019-07-01"),
              Timestamp.valueOf("2024-01-14 14:22:00")
            },
            new Object[] {
              3,
              "Charlie Brown",
              45,
              "Engineering",
              120000.0,
              true,
              Date.valueOf("2015-01-10"),
              Timestamp.valueOf("2024-01-15 08:00:00")
            },
            new Object[] {
              4,
              "Diana Ross",
              29,
              "Marketing",
              68000.0,
              true,
              Date.valueOf("2021-06-20"),
              Timestamp.valueOf("2024-01-13 16:45:00")
            },
            new Object[] {
              5,
              "Edward Chen",
              52,
              "Engineering",
              150000.0,
              true,
              Date.valueOf("2010-02-28"),
              Timestamp.valueOf("2024-01-15 10:15:00")
            },
            new Object[] {
              6,
              "Fiona Garcia",
              31,
              "Sales",
              82000.0,
              false,
              Date.valueOf("2018-11-05"),
              Timestamp.valueOf("2023-12-20 11:00:00")
            },
            new Object[] {
              7,
              "George Wilson",
              38,
              "Marketing",
              78000.0,
              true,
              Date.valueOf("2017-04-12"),
              Timestamp.valueOf("2024-01-14 09:00:00")
            },
            new Object[] {
              8,
              "Hannah Lee",
              26,
              "Engineering",
              88000.0,
              true,
              Date.valueOf("2022-09-01"),
              Timestamp.valueOf("2024-01-15 11:30:00")
            });

    // Use LinkedHashMap to preserve column order (must match row data order)
    Map<String, SqlTypeName> schema = new LinkedHashMap<>();
    schema.put("id", SqlTypeName.INTEGER);
    schema.put("name", SqlTypeName.VARCHAR);
    schema.put("age", SqlTypeName.INTEGER);
    schema.put("department", SqlTypeName.VARCHAR);
    schema.put("salary", SqlTypeName.DOUBLE);
    schema.put("is_active", SqlTypeName.BOOLEAN);
    schema.put("hire_date", SqlTypeName.DATE);
    schema.put("last_login", SqlTypeName.TIMESTAMP);

    return new SimpleScannableTable(schema, rows);
  }

  /**
   * Creates the products table with decimal prices.
   *
   * @return a ScannableTable with product data
   */
  private Table createProductsTable() {
    List<Object[]> rows =
        List.of(
            new Object[] {101, "Laptop Pro", new BigDecimal("1299.99"), true, "Electronics"},
            new Object[] {102, "Wireless Mouse", new BigDecimal("29.99"), true, "Electronics"},
            new Object[] {103, "Standing Desk", new BigDecimal("549.00"), false, "Furniture"},
            new Object[] {104, "Monitor 27\"", new BigDecimal("399.99"), true, "Electronics"},
            new Object[] {105, "Ergonomic Chair", new BigDecimal("449.00"), true, "Furniture"});

    // Use LinkedHashMap to preserve column order (must match row data order)
    Map<String, SqlTypeName> schema = new LinkedHashMap<>();
    schema.put("id", SqlTypeName.INTEGER);
    schema.put("name", SqlTypeName.VARCHAR);
    schema.put("price", SqlTypeName.DECIMAL);
    schema.put("in_stock", SqlTypeName.BOOLEAN);
    schema.put("category", SqlTypeName.VARCHAR);

    return new SimpleScannableTable(schema, rows);
  }

  /**
   * A simple implementation of ScannableTable that stores schema and rows in memory. This is a
   * reusable table implementation for creating test/example data.
   */
  private static class SimpleScannableTable implements ScannableTable {
    private final Map<String, SqlTypeName> schema;
    private final List<Object[]> rows;

    SimpleScannableTable(Map<String, SqlTypeName> schema, List<Object[]> rows) {
      // Use LinkedHashMap to preserve column order
      this.schema = new java.util.LinkedHashMap<>(schema);
      this.rows = rows;
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
      RelDataTypeFactory.Builder builder = typeFactory.builder();
      schema.forEach(
          (name, type) -> {
            if (type == SqlTypeName.DECIMAL) {
              // DECIMAL needs precision and scale
              builder.add(name, typeFactory.createSqlType(type, 10, 2));
            } else if (type == SqlTypeName.VARCHAR) {
              // VARCHAR needs a length
              builder.add(name, typeFactory.createSqlType(type, 255));
            } else {
              builder.add(name, typeFactory.createSqlType(type));
            }
          });
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
