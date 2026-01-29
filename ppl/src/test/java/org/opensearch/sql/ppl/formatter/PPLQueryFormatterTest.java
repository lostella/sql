/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

import static org.junit.Assert.*;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.sql.common.antlr.CaseInsensitiveCharStream;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLLexer;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLParser;

/**
 * Comprehensive unit tests for PPLQueryFormatter.
 *
 * <p>These tests verify:
 *
 * <ul>
 *   <li>Basic query formatting
 *   <li>Command formatting (fields, where, stats, eval, join, sort, head, dedup, rename, etc.)
 *   <li>Operator spacing
 *   <li>Keyword case normalization
 *   <li>Function formatting
 *   <li>Error handling
 *   <li>Edge cases
 *   <li>Idempotency (format(format(x)) == format(x))
 *   <li>AST equivalence (original and formatted queries parse to equivalent ASTs)
 * </ul>
 */
public class PPLQueryFormatterTest {

    private PPLQueryFormatter formatter;

    @Before
    public void setUp() {
        formatter = new PPLQueryFormatter();
    }

    /**
     * Helper method to assert formatting and idempotency. Verifies that: 1. Input formats to expected
     * output 2. Re-formatting expected output produces the same result (idempotent)
     */
    private void assertFormatting(String input, String expected) {
        String formatted = formatter.format(input);
        assertEquals("Formatting failed for: " + input, expected, formatted);

        // Verify idempotency - formatting again should produce same result
        String reformatted = formatter.format(formatted);
        assertEquals("Formatting is not idempotent for: " + formatted, expected, reformatted);
    }

    /** Parse a PPL query and return the parse tree. */
    private ParseTree parse(String query) {
        OpenSearchPPLLexer lexer = new OpenSearchPPLLexer(new CaseInsensitiveCharStream(query));
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        OpenSearchPPLParser parser = new OpenSearchPPLParser(tokenStream);
        return parser.root();
    }

    /** Check if a query parses successfully without syntax errors. */
    private boolean parsesSuccessfully(String query) {
        try {
            OpenSearchPPLLexer lexer = new OpenSearchPPLLexer(new CaseInsensitiveCharStream(query));
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            OpenSearchPPLParser parser = new OpenSearchPPLParser(tokenStream);
            parser.removeErrorListeners();
            FormatterErrorListener errorListener = new FormatterErrorListener();
            parser.addErrorListener(errorListener);
            parser.root();
            return !errorListener.hasErrors();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Assert that the formatted query parses successfully and produces an equivalent AST. This
     * verifies semantic preservation.
     */
    private void assertASTEquivalence(String original) {
        String formatted = formatter.format(original);

        // Both should parse successfully
        assertTrue("Original query should parse: " + original, parsesSuccessfully(original));
        assertTrue("Formatted query should parse: " + formatted, parsesSuccessfully(formatted));

        // Parse both and compare structure
        ParseTree originalTree = parse(original);
        ParseTree formattedTree = parse(formatted);

        // Compare AST structure (node types and token values, ignoring whitespace)
        assertTrue(
            "AST mismatch for query: " + original + "\nFormatted: " + formatted,
            astEquivalent(originalTree, formattedTree)
        );
    }

    /**
     * Compare two parse trees for semantic equivalence. Ignores whitespace differences and keyword
     * case.
     */
    private boolean astEquivalent(ParseTree t1, ParseTree t2) {
        if (t1 == null && t2 == null) return true;
        if (t1 == null || t2 == null) return false;

        // Compare node types
        if (!t1.getClass().equals(t2.getClass())) {
            return false;
        }

        // For terminal nodes, compare text (case-insensitive for keywords)
        if (t1.getChildCount() == 0 && t2.getChildCount() == 0) {
            String text1 = t1.getText().toLowerCase().trim();
            String text2 = t2.getText().toLowerCase().trim();
            // Handle operator equivalence
            if (text1.equals("==")) text1 = "=";
            if (text2.equals("==")) text2 = "=";
            if (text1.equals("!<")) text1 = ">=";
            if (text2.equals("!<")) text2 = ">=";
            if (text1.equals("!>")) text1 = "<=";
            if (text2.equals("!>")) text2 = "<=";
            return text1.equals(text2);
        }

        // Compare children
        if (t1.getChildCount() != t2.getChildCount()) {
            // Allow for optional SEARCH keyword removal
            return compareChildrenFlexibly(t1, t2);
        }

        for (int i = 0; i < t1.getChildCount(); i++) {
            if (!astEquivalent(t1.getChild(i), t2.getChild(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compare children flexibly, allowing for certain structural differences that don't affect
     * semantics (e.g., optional SEARCH keyword).
     */
    private boolean compareChildrenFlexibly(ParseTree t1, ParseTree t2) {
        // Simple heuristic: if one has one extra child, check if it's just "search"
        int count1 = t1.getChildCount();
        int count2 = t2.getChildCount();

        if (Math.abs(count1 - count2) > 1) return false;

        // Try to match children, skipping "search" keyword
        int i1 = 0,
            i2 = 0;
        while (i1 < count1 && i2 < count2) {
            ParseTree c1 = t1.getChild(i1);
            ParseTree c2 = t2.getChild(i2);

            if (astEquivalent(c1, c2)) {
                i1++;
                i2++;
            } else if (c1.getText().equalsIgnoreCase("search")) {
                i1++;
            } else if (c2.getText().equalsIgnoreCase("search")) {
                i2++;
            } else {
                return false;
            }
        }

        // Handle remaining children (should only be "search")
        while (i1 < count1) {
            if (!t1.getChild(i1).getText().equalsIgnoreCase("search")) return false;
            i1++;
        }
        while (i2 < count2) {
            if (!t2.getChild(i2).getText().equalsIgnoreCase("search")) return false;
            i2++;
        }

        return true;
    }

    // ==================== NULL AND EMPTY INPUT TESTS ====================

    @Test
    public void testNullInput() {
        assertEquals("", formatter.format(null));
    }

    @Test
    public void testEmptyInput() {
        assertEquals("", formatter.format(""));
    }

    @Test
    public void testWhitespaceOnlyInput() {
        assertEquals("", formatter.format("   "));
        assertEquals("", formatter.format("\t\n"));
    }

    // ==================== BASIC QUERY STRUCTURE TESTS ====================

    @Test
    public void testBasicSearchFormatting() {
        assertFormatting("search source=logs", "source=logs");
    }

    @Test
    public void testSourceWithoutSearch() {
        assertFormatting("source=logs", "source=logs");
    }

    @Test
    public void testIndexKeyword() {
        assertFormatting("index=logs", "index=logs");
    }

    @Test
    public void testSearchWithPipeCommands() {
        assertFormatting(
            "source=logs|fields fullName,age|where age>30",
            "source=logs | fields fullName, age | where age > 30"
        );
    }

    @Test
    public void testComplexQuery() {
        assertFormatting(
            "search source=access_logs status=200|fields timestamp,ip,response_time|where" +
                " response_time>1000|stats avg(response_time) by ip",
            "source=access_logs status = 200 | fields timestamp, ip, response_time | where" +
                " response_time > 1000 | stats avg(response_time) by ip"
        );
    }

    @Test
    public void testMultiplePipeCommands() {
        assertFormatting(
            "source=logs|where status=200|fields name,age|stats count() by name",
            "source=logs | where status = 200 | fields name, age | stats count() by name"
        );
    }

    // ==================== DESCRIBE COMMAND TESTS ====================

    @Test
    public void testDescribeCommand() {
        assertFormatting("describe my_table", "describe my_table");
    }

    @Test
    public void testDescribeUppercase() {
        assertFormatting("DESCRIBE my_table", "describe my_table");
    }

    @Test
    public void testDescribeMultipleTables() {
        assertFormatting("describe table1,table2", "describe table1, table2");
    }

    @Test
    public void testDescribeWithWildcard() {
        assertFormatting("describe logs-*", "describe logs-*");
    }

    @Test
    public void testDescribeWithCrossCluster() {
        assertFormatting("describe cluster:logs", "describe cluster:logs");
    }

    // ==================== FIELDS COMMAND TESTS ====================

    @Test
    public void testFieldsCommand() {
        assertFormatting(
            "source=logs|fields name,age,status",
            "source=logs | fields name, age, status"
        );
    }

    @Test
    public void testFieldsWithPlusPrefix() {
        assertFormatting("source=logs|fields + name,age", "source=logs | fields + name, age");
    }

    @Test
    public void testFieldsWithMinusPrefix() {
        assertFormatting(
            "source=logs|fields - password,secret",
            "source=logs | fields - password, secret"
        );
    }

    @Test
    public void testFieldsCommandWithWildcard() {
        // Wildcard patterns with dots need backticks to parse correctly
        assertFormatting("source=logs|fields `user.*`", "source=logs | fields `user.*`");
    }

    // ==================== WHERE COMMAND TESTS ====================

    @Test
    public void testWhereCommand() {
        assertFormatting("source=logs|where age>30", "source=logs | where age > 30");
    }

    @Test
    public void testWhereWithLogicalAnd() {
        assertFormatting(
            "source=logs|where age>30 AND status='active'",
            "source=logs | where age > 30 and status = 'active'"
        );
    }

    @Test
    public void testWhereWithLogicalOr() {
        assertFormatting(
            "source=logs|where status='error' OR status='warning'",
            "source=logs | where status = 'error' or status = 'warning'"
        );
    }

    @Test
    public void testWhereWithLogicalNot() {
        assertFormatting(
            "source=logs|where NOT status='inactive'",
            "source=logs | where not status = 'inactive'"
        );
    }

    @Test
    public void testWhereWithLogicalXor() {
        assertFormatting("source=logs|where a=1 XOR b=2", "source=logs | where a = 1 xor b = 2");
    }

    @Test
    public void testWhereWithParentheses() {
        assertFormatting(
            "source=logs|where (age>30 AND status='active') OR admin=true",
            "source=logs | where (age > 30 and status = 'active') or admin = true"
        );
    }

    // ==================== EVAL COMMAND TESTS ====================

    @Test
    public void testEvalCommand() {
        assertFormatting(
            "source=logs|eval doubled=value * 2",
            "source=logs | eval doubled = value * 2"
        );
    }

    @Test
    public void testEvalMultipleFields() {
        assertFormatting(
            "source=logs|eval a=b+1,c=d * 2",
            "source=logs | eval a = b + 1, c = d * 2"
        );
    }

    @Test
    public void testEvalWithArithmetic() {
        // Need space before * to prevent lexer from treating *c as wildcard pattern
        assertFormatting(
            "source=logs|eval result=(a+b) * c/d",
            "source=logs | eval result = (a + b) * c / d"
        );
    }

    @Test
    public void testEvalWithFunction() {
        assertFormatting(
            "source=logs|eval upper_name=upper(name)",
            "source=logs | eval upper_name = upper(name)"
        );
    }

    // ==================== STATS COMMAND TESTS ====================

    @Test
    public void testStatsCommand() {
        assertFormatting(
            "source=logs|stats count() by status",
            "source=logs | stats count() by status"
        );
    }

    @Test
    public void testStatsMultipleAggregations() {
        assertFormatting(
            "source=logs|stats count(),avg(response_time),max(bytes)",
            "source=logs | stats count(), avg(response_time), max(bytes)"
        );
    }

    @Test
    public void testStatsWithAlias() {
        assertFormatting(
            "source=logs|stats count() AS total,avg(latency) AS avg_latency",
            "source=logs | stats count() as total, avg(latency) as avg_latency"
        );
    }

    @Test
    public void testStatsWithPartitionsArg() {
        assertFormatting(
            "source=logs|stats PARTITIONS=10 count() by status",
            "source=logs | stats partitions=10 count() by status"
        );
    }

    @Test
    public void testStatsWithAllnumArg() {
        assertFormatting(
            "source=logs|stats ALLNUM=true count()",
            "source=logs | stats allnum=true count()"
        );
    }

    @Test
    public void testStatsWithDelimArg() {
        assertFormatting(
            "source=logs|stats DELIM=',' count()",
            "source=logs | stats delim=',' count()"
        );
    }

    @Test
    public void testStatsWithMultipleArgs() {
        assertFormatting(
            "source=logs|stats PARTITIONS=5 ALLNUM=false count()",
            "source=logs | stats partitions=5 allnum=false count()"
        );
    }

    @Test
    public void testStatsDistinctCount() {
        assertFormatting(
            "source=logs|stats DISTINCT_COUNT(user_id)",
            "source=logs | stats distinct_count(user_id)"
        );
    }

    @Test
    public void testStatsDC() {
        assertFormatting("source=logs|stats DC(user)", "source=logs | stats dc(user)");
    }

    // ==================== EVENTSTATS COMMAND TESTS ====================

    @Test
    public void testEventstatsCommand() {
        assertFormatting(
            "source=logs|eventstats count() by status",
            "source=logs | eventstats count() by status"
        );
    }

    @Test
    public void testEventstatsWithAlias() {
        assertFormatting(
            "source=logs|eventstats avg(latency) AS avg_lat by host",
            "source=logs | eventstats avg(latency) as avg_lat by host"
        );
    }

    // ==================== JOIN COMMAND TESTS ====================

    @Test
    public void testJoinCommand() {
        assertFormatting(
            "source=logs|join ON logs.id=users.id users",
            "source=logs | join on logs.id = users.id users"
        );
    }

    @Test
    public void testJoinWithWhereClause() {
        assertFormatting(
            "source=logs|join WHERE logs.id=users.id users",
            "source=logs | join where logs.id = users.id users"
        );
    }

    // ==================== SORT COMMAND TESTS ====================

    @Test
    public void testSortCommand() {
        assertFormatting("source=logs|sort age", "source=logs | sort age");
    }

    @Test
    public void testSortDescending() {
        assertFormatting("source=logs|sort - age", "source=logs | sort - age");
    }

    @Test
    public void testSortAscending() {
        assertFormatting("source=logs|sort + age", "source=logs | sort + age");
    }

    @Test
    public void testSortMultipleFields() {
        assertFormatting(
            "source=logs|sort status,- timestamp,+ priority",
            "source=logs | sort status, - timestamp, + priority"
        );
    }

    @Test
    public void testSortWithCount() {
        assertFormatting("source=logs|sort 10 age", "source=logs | sort 10 age");
    }

    @Test
    public void testSortUppercase() {
        assertFormatting("source=logs|SORT age", "source=logs | sort age");
    }

    // ==================== HEAD COMMAND TESTS ====================

    @Test
    public void testHeadCommand() {
        assertFormatting("source=logs|head", "source=logs | head");
    }

    @Test
    public void testHeadWithCount() {
        assertFormatting("source=logs|head 20", "source=logs | head 20");
    }

    @Test
    public void testHeadWithFromAndCount() {
        assertFormatting("source=logs|head 10 from 5", "source=logs | head 10 from 5");
    }

    @Test
    public void testHeadUppercase() {
        assertFormatting("source=logs|HEAD 50", "source=logs | head 50");
    }

    // ==================== DEDUP COMMAND TESTS ====================

    @Test
    public void testDedupCommand() {
        assertFormatting("source=logs|dedup user_id", "source=logs | dedup user_id");
    }

    @Test
    public void testDedupMultipleFields() {
        assertFormatting(
            "source=logs|dedup user_id,session_id",
            "source=logs | dedup user_id, session_id"
        );
    }

    @Test
    public void testDedupWithCount() {
        assertFormatting("source=logs|dedup 3 user_id", "source=logs | dedup 3 user_id");
    }

    @Test
    public void testDedupWithKeepempty() {
        assertFormatting(
            "source=logs|dedup user_id keepempty=true",
            "source=logs | dedup user_id keepempty=true"
        );
    }

    @Test
    public void testDedupWithConsecutive() {
        assertFormatting(
            "source=logs|dedup user_id consecutive=true",
            "source=logs | dedup user_id consecutive=true"
        );
    }

    @Test
    public void testDedupUppercase() {
        assertFormatting("source=logs|DEDUP user_id", "source=logs | dedup user_id");
    }

    // ==================== RENAME COMMAND TESTS ====================

    @Test
    public void testRenameCommand() {
        assertFormatting(
            "source=logs|rename old_name as new_name",
            "source=logs | rename old_name as new_name"
        );
    }

    @Test
    public void testRenameMultipleFields() {
        assertFormatting(
            "source=logs|rename field1 as alias1,field2 as alias2",
            "source=logs | rename field1 as alias1, field2 as alias2"
        );
    }

    @Test
    public void testRenameUppercase() {
        assertFormatting(
            "source=logs|RENAME oldField AS newField",
            "source=logs | rename oldField as newField"
        );
    }

    // ==================== RARE COMMAND TESTS ====================

    @Test
    public void testRareCommand() {
        assertFormatting("source=logs|rare status", "source=logs | rare status");
    }

    @Test
    public void testRareWithBy() {
        assertFormatting("source=logs|rare status by host", "source=logs | rare status by host");
    }

    @Test
    public void testRareMultipleFields() {
        assertFormatting(
            "source=logs|rare status,method by host",
            "source=logs | rare status, method by host"
        );
    }

    @Test
    public void testRareUppercase() {
        assertFormatting("source=logs|RARE status", "source=logs | rare status");
    }

    // ==================== TOP COMMAND TESTS ====================

    @Test
    public void testTopCommand() {
        assertFormatting("source=logs|top status", "source=logs | top status");
    }

    @Test
    public void testTopWithCount() {
        assertFormatting("source=logs|top 10 status", "source=logs | top 10 status");
    }

    @Test
    public void testTopWithBy() {
        assertFormatting("source=logs|top 5 status by host", "source=logs | top 5 status by host");
    }

    @Test
    public void testTopMultipleFields() {
        assertFormatting("source=logs|top 10 status,method", "source=logs | top 10 status, method");
    }

    @Test
    public void testTopUppercase() {
        assertFormatting("source=logs|TOP 5 status", "source=logs | top 5 status");
    }

    // ==================== GROK COMMAND TESTS ====================

    @Test
    public void testGrokCommand() {
        assertFormatting(
            "source=logs|grok message '%{COMMONAPACHELOG}'",
            "source=logs | grok message '%{COMMONAPACHELOG}'"
        );
    }

    @Test
    public void testGrokUppercase() {
        assertFormatting(
            "source=logs|GROK message '%{IP:client}'",
            "source=logs | grok message '%{IP:client}'"
        );
    }

    // ==================== PARSE COMMAND TESTS ====================

    @Test
    public void testParseCommand() {
        assertFormatting(
            "source=logs|parse message '(?<timestamp>\\d+)'",
            "source=logs | parse message '(?<timestamp>\\d+)'"
        );
    }

    @Test
    public void testParseUppercase() {
        assertFormatting(
            "source=logs|PARSE message '(?<field>\\w+)'",
            "source=logs | parse message '(?<field>\\w+)'"
        );
    }

    // ==================== PATTERNS COMMAND TESTS ====================

    @Test
    public void testPatternsCommand() {
        assertFormatting("source=logs|patterns message", "source=logs | patterns message");
    }

    @Test
    public void testPatternsWithPattern() {
        assertFormatting(
            "source=logs|patterns message pattern='[a-z]+'",
            "source=logs | patterns message pattern='[a-z]+'"
        );
    }

    @Test
    public void testPatternsUppercase() {
        assertFormatting("source=logs|PATTERNS message", "source=logs | patterns message");
    }

    // ==================== FILLNULL COMMAND TESTS ====================

    @Test
    public void testFillnullCommand() {
        assertFormatting(
            "source=logs|fillnull value=0 field1",
            "source=logs | fillnull value=0 field1"
        );
    }

    @Test
    public void testFillnullMultipleFields() {
        assertFormatting(
            "source=logs|fillnull value='N/A' field1,field2,field3",
            "source=logs | fillnull value='N/A' field1, field2, field3"
        );
    }

    @Test
    public void testFillnullUppercase() {
        assertFormatting(
            "source=logs|FILLNULL value=0 count",
            "source=logs | fillnull value=0 count"
        );
    }

    // ==================== TRENDLINE COMMAND TESTS ====================

    @Test
    public void testTrendlineCommand() {
        assertFormatting(
            "source=logs|trendline sma(3,value)",
            "source=logs | trendline sma(3, value)"
        );
    }

    @Test
    public void testTrendlineWithAlias() {
        assertFormatting(
            "source=logs|trendline sma(5,price) as trend",
            "source=logs | trendline sma(5, price) as trend"
        );
    }

    @Test
    public void testTrendlineUppercase() {
        assertFormatting(
            "source=logs|TRENDLINE SMA(3,value)",
            "source=logs | trendline sma(3, value)"
        );
    }

    // ==================== EXPAND COMMAND TESTS ====================

    @Test
    public void testExpandCommand() {
        assertFormatting("source=logs|expand array_field", "source=logs | expand array_field");
    }

    @Test
    public void testExpandWithAlias() {
        assertFormatting("source=logs|expand items as item", "source=logs | expand items as item");
    }

    @Test
    public void testExpandUppercase() {
        assertFormatting("source=logs|EXPAND array_field", "source=logs | expand array_field");
    }

    // ==================== FLATTEN COMMAND TESTS ====================

    @Test
    public void testFlattenCommand() {
        assertFormatting("source=logs|flatten nested_field", "source=logs | flatten nested_field");
    }

    @Test
    public void testFlattenUppercase() {
        assertFormatting("source=logs|FLATTEN nested_field", "source=logs | flatten nested_field");
    }

    // ==================== LOOKUP COMMAND TESTS ====================

    @Test
    public void testLookupCommand() {
        assertFormatting("source=logs|lookup users uid", "source=logs | lookup users uid");
    }

    @Test
    public void testLookupWithOutput() {
        assertFormatting(
            "source=logs|lookup users uid output name,email",
            "source=logs | lookup users uid output name, email"
        );
    }

    @Test
    public void testLookupUppercase() {
        assertFormatting("source=logs|LOOKUP users uid", "source=logs | lookup users uid");
    }

    // ==================== BIN/SPAN COMMAND TESTS ====================

    @Test
    public void testBinCommand() {
        // Grammar: BIN fieldExpression binOption* - field comes first, then options
        assertFormatting(
            "source=logs|bin timestamp span=1h",
            "source=logs | bin timestamp span=1h"
        );
    }

    @Test
    public void testBinWithBins() {
        // Grammar: BIN fieldExpression binOption* - field comes first, then options
        assertFormatting("source=logs|bin value bins=10", "source=logs | bin value bins=10");
    }

    @Test
    public void testBinUppercase() {
        // Grammar: BIN fieldExpression binOption* - field comes first, then options
        assertFormatting(
            "source=logs|BIN timestamp SPAN=1d",
            "source=logs | bin timestamp span=1d"
        );
    }

    // ==================== COMPARISON OPERATOR TESTS ====================

    @Test
    public void testComparisonGreaterThan() {
        assertFormatting("source=logs|where age>30", "source=logs | where age > 30");
    }

    @Test
    public void testComparisonLessThan() {
        assertFormatting("source=logs|where age<30", "source=logs | where age < 30");
    }

    @Test
    public void testComparisonGreaterThanOrEqual() {
        assertFormatting("source=logs|where age>=30", "source=logs | where age >= 30");
    }

    @Test
    public void testComparisonLessThanOrEqual() {
        assertFormatting("source=logs|where age<=30", "source=logs | where age <= 30");
    }

    @Test
    public void testComparisonNotEqual() {
        assertFormatting("source=logs|where age!=30", "source=logs | where age != 30");
    }

    @Test
    public void testComparisonEqual() {
        assertFormatting("source=logs|where age=30", "source=logs | where age = 30");
    }

    @Test
    public void testComparisonDoubleEqual() {
        assertFormatting("source=logs|where age==30", "source=logs | where age = 30");
    }

    // ==================== IN EXPRESSION TESTS ====================

    @Test
    public void testInExpression() {
        assertFormatting(
            "source=logs|where status IN (200,201,204)",
            "source=logs | where status in (200, 201, 204)"
        );
    }

    @Test
    public void testNotInExpression() {
        assertFormatting(
            "source=logs|where status NOT IN (400,500)",
            "source=logs | where status not in (400, 500)"
        );
    }

    @Test
    public void testInWithStrings() {
        assertFormatting(
            "source=logs|where method IN ('GET','POST','PUT')",
            "source=logs | where method in ('GET', 'POST', 'PUT')"
        );
    }

    // ==================== FUNCTION TESTS ====================

    @Test
    public void testLikeFunction() {
        assertFormatting(
            "source=logs|where LIKE(message,'%error%')",
            "source=logs | where like(message, '%error%')"
        );
    }

    @Test
    public void testLikeFunctionWithCaseSensitive() {
        assertFormatting(
            "source=logs|where LIKE(name,'John%',true)",
            "source=logs | where like(name, 'John%', true)"
        );
    }

    @Test
    public void testILikeFunction() {
        assertFormatting(
            "source=logs|where ILIKE(message,'%ERROR%')",
            "source=logs | where ilike(message, '%ERROR%')"
        );
    }

    @Test
    public void testUpperFunction() {
        assertFormatting(
            "source=logs|eval upper_name=UPPER(name)",
            "source=logs | eval upper_name = upper(name)"
        );
    }

    @Test
    public void testLowerFunction() {
        assertFormatting(
            "source=logs|eval lower_name=LOWER(name)",
            "source=logs | eval lower_name = lower(name)"
        );
    }

    @Test
    public void testCountFunction() {
        assertFormatting("source=logs|stats COUNT()", "source=logs | stats count()");
    }

    @Test
    public void testCountWithField() {
        assertFormatting("source=logs|stats COUNT(field)", "source=logs | stats count(field)");
    }

    @Test
    public void testAvgFunction() {
        assertFormatting("source=logs|stats AVG(latency)", "source=logs | stats avg(latency)");
    }

    @Test
    public void testSumFunction() {
        assertFormatting("source=logs|stats SUM(bytes)", "source=logs | stats sum(bytes)");
    }

    @Test
    public void testMinFunction() {
        assertFormatting("source=logs|stats MIN(value)", "source=logs | stats min(value)");
    }

    @Test
    public void testMaxFunction() {
        assertFormatting("source=logs|stats MAX(value)", "source=logs | stats max(value)");
    }

    // ==================== SOURCE/INDEX FORMAT TESTS ====================

    @Test
    public void testSourceEqualsNoSpacing() {
        // source= should not have spacing around equals
        assertFormatting("SOURCE = logs", "source=logs");
    }

    @Test
    public void testIndexEqualsNoSpacing() {
        assertFormatting("INDEX = logs", "index=logs");
    }

    @Test
    public void testIndexWithWildcard() {
        assertFormatting("index=logs-*", "index=logs-*");
    }

    @Test
    public void testMultipleWildcardSources() {
        assertFormatting("source=logs-2024-*,metrics-*", "source=logs-2024-*, metrics-*");
    }

    // ==================== CASE NORMALIZATION TESTS ====================

    @Test
    public void testUppercaseCommands() {
        assertFormatting(
            "SOURCE=logs|WHERE status=200|FIELDS name",
            "source=logs | where status = 200 | fields name"
        );
    }

    @Test
    public void testUppercaseFunctions() {
        assertFormatting(
            "source=logs|stats COUNT(),AVG(latency),MAX(bytes)",
            "source=logs | stats count(), avg(latency), max(bytes)"
        );
    }

    @Test
    public void testMixedCaseKeywords() {
        assertFormatting(
            "source=logs|Where Status=200 And Method='GET'",
            "source=logs | where Status = 200 and Method = 'GET'"
        );
    }

    @Test
    public void testBooleanLiteralNormalization() {
        assertFormatting("source=logs|where active=TRUE", "source=logs | where active = true");
        assertFormatting("source=logs|where active=FALSE", "source=logs | where active = false");
        assertFormatting("source=logs|where active=True", "source=logs | where active = true");
    }

    @Test
    public void testBooleanLiteralInStatsArgs() {
        // Boolean literals in stats args are normalized to lowercase
        assertFormatting(
            "source=logs|stats allnum=TRUE count()",
            "source=logs | stats allnum=true count()"
        );
        assertFormatting(
            "source=logs|stats allnum=FALSE count()",
            "source=logs | stats allnum=false count()"
        );
    }

    // ==================== SPACING TESTS ====================

    @Test
    public void testCommaSpacing() {
        assertFormatting("source=logs|fields a,b,c,d", "source=logs | fields a, b, c, d");
    }

    @Test
    public void testPipeSpacing() {
        assertFormatting(
            "source=logs|where a=1|where b=2",
            "source=logs | where a = 1 | where b = 2"
        );
    }

    @Test
    public void testFormattingWithVariousSpacing() {
        assertFormatting(
            "source=logs  |   where   age  >  30  |   fields  name ,  age",
            "source=logs | where age > 30 | fields name, age"
        );
    }

    @Test
    public void testOperatorSpacingNormalization() {
        assertFormatting(
            "source=logs|where a>1 AND b<2 OR c>=3 AND d<=4",
            "source=logs | where a > 1 and b < 2 or c >= 3 and d <= 4"
        );
    }

    // ==================== ARITHMETIC TESTS ====================

    @Test
    public void testMultiplicationInField() {
        assertFormatting(
            "source=logs|eval doubled=age * 2",
            "source=logs | eval doubled = age * 2"
        );
    }

    @Test
    public void testMultiplicationNumberFirst() {
        // Need space before * to prevent lexer from treating *value as wildcard pattern
        assertFormatting(
            "source=logs|eval result=2 * value",
            "source=logs | eval result = 2 * value"
        );
    }

    @Test
    public void testDivisionInField() {
        assertFormatting("source=logs|eval half=value/2", "source=logs | eval half = value / 2");
    }

    @Test
    public void testAdditionInField() {
        assertFormatting("source=logs|eval total=a+1", "source=logs | eval total = a + 1");
    }

    @Test
    public void testSubtractionInField() {
        assertFormatting("source=logs|eval diff=a-1", "source=logs | eval diff = a - 1");
    }

    @Test
    public void testChainedArithmetic() {
        assertFormatting(
            "source=logs|eval complex=a * 2+b/3-c",
            "source=logs | eval complex = a * 2 + b / 3 - c"
        );
    }

    @Test
    public void testMultiplicationWithoutSpacesFailsToParse() {
        // Multiplication without spaces around * is not parsed correctly
        // because the lexer treats *2 or *value as a wildcard pattern
        assertFalse(
            "Query with 'age*2' should fail to parse",
            parsesSuccessfully("source=logs|eval doubled=age*2")
        );
        assertFalse(
            "Query with '2*value' should fail to parse",
            parsesSuccessfully("source=logs|eval result=2*value")
        );
        assertFalse(
            "Query with 'a*b' should fail to parse",
            parsesSuccessfully("source=logs|eval product=a*b")
        );
    }

    // ==================== FIELD NAME TESTS ====================

    @Test
    public void testDottedFieldName() {
        assertFormatting(
            "source=logs|fields user.name,user.email",
            "source=logs | fields user.name, user.email"
        );
    }

    @Test
    public void testNestedFieldAccess() {
        assertFormatting(
            "source=logs|where user.profile.age>30",
            "source=logs | where user.profile.age > 30"
        );
    }

    // ==================== ALIAS TESTS ====================

    @Test
    public void testSourceWithAlias() {
        assertFormatting("source=logs as l", "source=logs as l");
    }

    @Test
    public void testStatsWithFieldAlias() {
        assertFormatting(
            "source=logs|stats count() as cnt,avg(latency) as avg_lat",
            "source=logs | stats count() as cnt, avg(latency) as avg_lat"
        );
    }

    // ==================== CROSS-CLUSTER TESTS ====================

    @Test
    public void testCrossClusterSource() {
        assertFormatting("source=cluster:logs", "source=cluster:logs");
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    public void testMalformedQueryReturnsOriginal() {
        String malformed = "source=logs | where | fields";
        String result = formatter.format(malformed);
        // Should return original on error
        assertEquals(malformed, result);
    }

    @Test
    public void testPartialQueryHandling() {
        String partial = "source=";
        String result = formatter.format(partial);
        assertNotNull(result);
    }

    // ==================== IDEMPOTENCY TESTS ====================

    @Test
    public void testIdempotency() {
        String[] queries = {
            "source=logs | fields name, age | where age > 30",
            "source=logs | stats count() by status",
            "source=logs | eval doubled = value * 2",
            "source=logs | where status = 200 and method = GET",
        };

        for (String query : queries) {
            String formatted1 = formatter.format(query);
            String formatted2 = formatter.format(formatted1);
            assertEquals("Formatting is not idempotent for: " + query, formatted1, formatted2);
        }
    }

    // ==================== COMPLEX QUERY TESTS ====================

    @Test
    public void testComplexQueryWithAllFeatures() {
        assertFormatting(
            "SEARCH SOURCE=access_logs status=200|FIELDS timestamp,user_id,response_time,method|WHERE" +
                " response_time>1000 AND method='GET'|STATS AVG(response_time) AS avg_time,COUNT() AS" +
                " request_count BY user_id|WHERE request_count>10",
            "source=access_logs status = 200 | fields timestamp, user_id, response_time, method | where" +
                " response_time > 1000 and method = 'GET' | stats avg(response_time) as avg_time," +
                " count() as request_count by user_id | where request_count > 10"
        );
    }

    @Test
    public void testNestedLogicalExpressions() {
        assertFormatting(
            "source=logs|where (a=1 or b=2) and (c=3 or d=4)",
            "source=logs | where (a = 1 or b = 2) and (c = 3 or d = 4)"
        );
    }

    @Test
    public void testComplexStatsWithMultipleGroupBy() {
        assertFormatting(
            "source=logs|stats count(),avg(latency) by status,host",
            "source=logs | stats count(), avg(latency) by status, host"
        );
    }

    // ==================== REGRESSION TESTS ====================

    @Test
    public void testPreserveFieldCase() {
        // Field names should preserve their case
        assertFormatting(
            "source=logs|fields userName,firstName,lastName",
            "source=logs | fields userName, firstName, lastName"
        );
    }

    @Test
    public void testPreserveStringLiteralContent() {
        // String literal content should be preserved exactly
        assertFormatting(
            "source=logs|where message='Hello World'",
            "source=logs | where message = 'Hello World'"
        );
    }

    @Test
    public void testMultipleConsecutivePipes() {
        // Each command after pipe should be formatted correctly
        assertFormatting(
            "source=logs|where a=1|where b=2|where c=3",
            "source=logs | where a = 1 | where b = 2 | where c = 3"
        );
    }

    @Test
    public void testComplexIdentifiers() {
        assertFormatting(
            "source=logs-index|where user.`full-name` ='John Doe'",
            "source=logs-index | where user.`full-name` = 'John Doe'"
        );
        assertFormatting(
            "source=logs-index-*|where `service.latency-ms`>300",
            "source=logs-index-* | where `service.latency-ms` > 300"
        );
    }

    // ==================== AST EQUIVALENCE TESTS ====================

    @Test
    public void testASTEquivalenceBasicQuery() {
        assertASTEquivalence("source=logs");
        assertASTEquivalence("index=logs");
        assertASTEquivalence("search source=logs");
    }

    @Test
    public void testASTEquivalenceWhereClause() {
        assertASTEquivalence("source=logs | where age > 30");
        assertASTEquivalence("source=logs | where status = 200 and method = 'GET'");
        assertASTEquivalence("source=logs | where (a = 1 or b = 2) and c = 3");
    }

    @Test
    public void testASTEquivalenceFieldsCommand() {
        assertASTEquivalence("source=logs | fields name, age, status");
        assertASTEquivalence("source=logs | fields + name, age");
        assertASTEquivalence("source=logs | fields - password, secret");
    }

    @Test
    public void testASTEquivalenceStatsCommand() {
        assertASTEquivalence("source=logs | stats count() by status");
        assertASTEquivalence("source=logs | stats count(), avg(latency), max(bytes)");
        assertASTEquivalence("source=logs | stats count() as total by host");
    }

    @Test
    public void testASTEquivalenceEvalCommand() {
        assertASTEquivalence("source=logs | eval doubled = value * 2");
        assertASTEquivalence("source=logs | eval a = b + 1, c = d * 2");
    }

    @Test
    public void testASTEquivalenceSortCommand() {
        assertASTEquivalence("source=logs | sort age");
        assertASTEquivalence("source=logs | sort - timestamp");
        assertASTEquivalence("source=logs | sort status, - timestamp");
    }

    @Test
    public void testASTEquivalenceHeadCommand() {
        assertASTEquivalence("source=logs | head");
        assertASTEquivalence("source=logs | head 20");
    }

    @Test
    public void testASTEquivalenceDedupCommand() {
        assertASTEquivalence("source=logs | dedup user_id");
        assertASTEquivalence("source=logs | dedup user_id, session_id");
    }

    @Test
    public void testASTEquivalenceRenameCommand() {
        assertASTEquivalence("source=logs | rename old_name as new_name");
        assertASTEquivalence("source=logs | rename field1 as alias1, field2 as alias2");
    }

    @Test
    public void testASTEquivalenceRareTopCommands() {
        assertASTEquivalence("source=logs | rare status");
        assertASTEquivalence("source=logs | rare status by host");
        assertASTEquivalence("source=logs | top status");
        assertASTEquivalence("source=logs | top 10 status by host");
    }

    @Test
    public void testASTEquivalenceComplexQueries() {
        assertASTEquivalence(
            "source=logs | where status = 200 | fields timestamp, user_id | stats count() by user_id"
        );
        assertASTEquivalence(
            "source=logs | where age > 30 and status = 'active' | sort - timestamp | head 100"
        );
        assertASTEquivalence(
            "source=logs | eval doubled = value * 2 | where doubled > 100 | stats avg(doubled) by" +
                " category"
        );
    }

    @Test
    public void testASTEquivalenceOperatorNormalization() {
        // These should produce equivalent ASTs even with different operator representations
        String query1 = "source=logs | where age = 30";
        String query2 = "source=logs | where age == 30";

        String formatted1 = formatter.format(query1);
        String formatted2 = formatter.format(query2);

        // Both should format to the same result
        assertEquals(formatted1, formatted2);

        // Both formatted versions should be parseable
        assertTrue(parsesSuccessfully(formatted1));
        assertTrue(parsesSuccessfully(formatted2));
    }

    @Test
    public void testASTEquivalenceCaseNormalization() {
        // These should produce equivalent results
        String lowercase = "source=logs | where status = 200 | stats count() by host";
        String uppercase = "SOURCE=logs | WHERE status = 200 | STATS COUNT() BY host";
        String mixedCase = "Source=logs | Where status = 200 | Stats Count() By host";

        String formattedLower = formatter.format(lowercase);
        String formattedUpper = formatter.format(uppercase);
        String formattedMixed = formatter.format(mixedCase);

        // All should format to the same result
        assertEquals(formattedLower, formattedUpper);
        assertEquals(formattedLower, formattedMixed);
    }

    @Test
    public void testASTEquivalenceMultiplePipes() {
        assertASTEquivalence(
            "source=logs | where a = 1 | where b = 2 | where c = 3 | fields a, b, c"
        );
    }

    @Test
    public void testASTEquivalenceJoinCommand() {
        assertASTEquivalence("source=logs | join on logs.id = users.id users");
    }

    @Test
    public void testASTEquivalenceEventstatsCommand() {
        assertASTEquivalence("source=logs | eventstats count() by status");
        assertASTEquivalence("source=logs | eventstats avg(latency) as avg_lat by host");
    }

    // ==================== SEMANTIC PRESERVATION BATCH TEST ====================

    @Test
    public void testSemanticPreservationBatch() {
        // Comprehensive list of queries covering supported command types
        String[] queries = {
            // Basic queries
            "source=logs",
            "index=metrics",
            "search source=logs",
            // Where with various operators
            "source=logs | where age > 30",
            "source=logs | where age >= 30",
            "source=logs | where age < 30",
            "source=logs | where age <= 30",
            "source=logs | where age = 30",
            "source=logs | where age != 30",
            // Logical operators
            "source=logs | where a = 1 and b = 2",
            "source=logs | where a = 1 or b = 2",
            "source=logs | where not a = 1",
            "source=logs | where a = 1 xor b = 2",
            // Fields command
            "source=logs | fields name, age",
            "source=logs | fields + name",
            "source=logs | fields - password",
            // Stats command
            "source=logs | stats count()",
            "source=logs | stats count() by status",
            "source=logs | stats avg(latency), max(bytes)",
            // Eval command
            "source=logs | eval x = y * 2",
            "source=logs | eval a = b + c, d = e - f",
            // Sort command
            "source=logs | sort timestamp",
            "source=logs | sort - timestamp",
            // Head command
            "source=logs | head",
            "source=logs | head 50",
            // Dedup command
            "source=logs | dedup user_id",
            // Rename command
            "source=logs | rename old as new",
            // Rare and Top
            "source=logs | rare status",
            "source=logs | top 10 status",
            // Join command
            "source=logs | join on logs.id = users.id users",
            // Eventstats command
            "source=logs | eventstats count() by status",
        };

        for (String query : queries) {
            String formatted = formatter.format(query);

            // Verify formatted query parses successfully
            assertTrue(
                "Formatted query should parse: " + formatted + " (original: " + query + ")",
                parsesSuccessfully(formatted)
            );

            // Verify idempotency
            String reformatted = formatter.format(formatted);
            assertEquals("Formatting should be idempotent for: " + query, formatted, reformatted);
        }
    }
}
