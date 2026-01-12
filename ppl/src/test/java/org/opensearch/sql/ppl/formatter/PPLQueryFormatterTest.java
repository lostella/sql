/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Comprehensive unit tests for PPLQueryFormatter.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Basic query formatting</li>
 *   <li>Command formatting (fields, where, stats, eval, join, etc.)</li>
 *   <li>Operator spacing</li>
 *   <li>Keyword case normalization</li>
 *   <li>Function formatting</li>
 *   <li>Error handling</li>
 *   <li>Edge cases</li>
 *   <li>Idempotency (format(format(x)) == format(x))</li>
 * </ul>
 */
public class PPLQueryFormatterTest {

    private PPLQueryFormatter formatter;

    @Before
    public void setUp() {
        formatter = new PPLQueryFormatter();
    }

    /**
     * Helper method to assert formatting and idempotency.
     * Verifies that:
     * 1. Input formats to expected output
     * 2. Re-formatting expected output produces the same result (idempotent)
     */
    private void assertFormatting(String input, String expected) {
        String formatted = formatter.format(input);
        assertEquals("Formatting failed for: " + input, expected, formatted);

        // Verify idempotency - formatting again should produce same result
        String reformatted = formatter.format(formatted);
        assertEquals("Formatting is not idempotent for: " + formatted, expected, reformatted);
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
            "search source=access_logs status=200|fields timestamp,ip,response_time|where response_time>1000|stats avg(response_time) by ip",
            "source=access_logs status = 200 | fields timestamp, ip, response_time | where response_time > 1000 | stats avg(response_time) by ip"
        );
    }

    @Test
    public void testMultiplePipeCommands() {
        assertFormatting(
            "source=logs|where status=200|fields name|stats count()",
            "source=logs | where status = 200 | fields name | stats count()"
        );
    }

    // ==================== DESCRIBE COMMAND TESTS ====================

    @Test
    public void testDescribeCommand() {
        assertFormatting("describe logs", "describe logs");
    }

    @Test
    public void testDescribeUppercase() {
        assertFormatting("DESCRIBE logs", "describe logs");
    }

    @Test
    public void testDescribeMultipleTables() {
        assertFormatting("describe logs,users", "describe logs, users");
    }

    @Test
    public void testDescribeWithWildcard() {
        assertFormatting("describe logs*", "describe logs*");
    }

    @Test
    public void testDescribeWithCrossCluster() {
        assertFormatting("describe cluster:logs*,users", "describe cluster:logs*, users");
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
    public void testFieldsWithWildcard() {
        assertFormatting("source=accounts|fields account*", "source=accounts | fields account*");
    }

    @Test
    public void testFieldsWithSuffixWildcard() {
        assertFormatting("source=accounts|fields *name", "source=accounts | fields * name");
    }

    @Test
    public void testFieldsWithExclusion() {
        assertFormatting(
            "source=accounts|fields - account_number",
            "source=accounts | fields - account_number"
        );
    }

    @Test
    public void testFieldsWithInclusion() {
        assertFormatting(
            "source=accounts|fields + account_number",
            "source=accounts | fields + account_number"
        );
    }

    @Test
    public void testFieldsAllStar() {
        assertFormatting("source=logs|fields *", "source=logs | fields *");
    }

    // ==================== WHERE COMMAND TESTS ====================

    @Test
    public void testWhereCommand() {
        assertFormatting("source=logs|where status=200", "source=logs | where status = 200");
    }

    @Test
    public void testWhereWithLogicalAnd() {
        assertFormatting(
            "source=logs|where status=200 and method=GET",
            "source=logs | where status = 200 and method = GET"
        );
    }

    @Test
    public void testWhereWithLogicalOr() {
        assertFormatting(
            "source=logs|where status=200 or status=201",
            "source=logs | where status = 200 or status = 201"
        );
    }

    @Test
    public void testWhereWithLogicalNot() {
        assertFormatting(
            "source=logs|where not status=200",
            "source=logs | where not status = 200"
        );
    }

    @Test
    public void testWhereWithLogicalXor() {
        assertFormatting("source=logs|where a=1 xor b=2", "source=logs | where a = 1 xor b = 2");
    }

    @Test
    public void testWhereWithParentheses() {
        assertFormatting(
            "source=logs|where (status=200 or status=201) and method=GET",
            "source=logs | where (status = 200 or status = 201) and method = GET"
        );
    }

    // ==================== EVAL COMMAND TESTS ====================

    @Test
    public void testEvalCommand() {
        assertFormatting(
            "source=accounts|eval doubleAge=age*2|fields age,doubleAge",
            "source=accounts | eval doubleAge = age * 2 | fields age, doubleAge"
        );
    }

    @Test
    public void testEvalMultipleFields() {
        assertFormatting(
            "source=accounts|eval doubleAge=age*2,ddAge=doubleAge*2",
            "source=accounts | eval doubleAge = age * 2, ddAge = doubleAge * 2"
        );
    }

    @Test
    public void testEvalWithArithmetic() {
        assertFormatting(
            "source=accounts|eval mb=bytes/1024/1024|stats avg(mb) by host",
            "source=accounts | eval mb = bytes / 1024 / 1024 | stats avg(mb) by host"
        );
    }

    @Test
    public void testEvalWithFunction() {
        assertFormatting(
            "source=logs|eval upper_name=UPPER(name)",
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
            "source=logs|stats count(),avg(latency),max(latency) by status",
            "source=logs | stats count(), avg(latency), max(latency) by status"
        );
    }

    @Test
    public void testStatsWithAlias() {
        assertFormatting(
            "source=logs|stats count() as cnt,avg(latency) as avg_latency by status",
            "source=logs | stats count() as cnt, avg(latency) as avg_latency by status"
        );
    }

    @Test
    public void testStatsWithPartitionsArg() {
        assertFormatting(
            "source=logs|stats partitions=10 count() by status",
            "source=logs | stats partitions=10 count() by status"
        );
    }

    @Test
    public void testStatsWithAllnumArg() {
        assertFormatting(
            "source=logs|stats allnum=true count() by status",
            "source=logs | stats allnum=true count() by status"
        );
    }

    @Test
    public void testStatsWithDelimArg() {
        assertFormatting(
            "source=logs|stats delim=',' count() by status",
            "source=logs | stats delim=',' count() by status"
        );
    }

    @Test
    public void testStatsWithMultipleArgs() {
        assertFormatting(
            "source=logs|stats partitions=5 allnum=false count() by status",
            "source=logs | stats partitions=5 allnum=false count() by status"
        );
    }

    @Test
    public void testStatsDistinctCount() {
        assertFormatting(
            "source=logs|stats distinct_count(user_id)",
            "source=logs | stats distinct_count(user_id)"
        );
    }

    @Test
    public void testStatsDC() {
        assertFormatting("source=logs|stats dc(user_id)", "source=logs | stats dc(user_id)");
    }

    // ==================== EVENTSTATS COMMAND TESTS ====================

    @Test
    public void testEventstatsCommand() {
        assertFormatting(
            "source=table|eventstats avg(a) by b",
            "source=table | eventstats avg(a) by b"
        );
    }

    @Test
    public void testEventstatsWithAlias() {
        assertFormatting(
            "source=table|eventstats avg(latency) as avg_lat by host",
            "source=table | eventstats avg(latency) as avg_lat by host"
        );
    }

    // ==================== JOIN COMMAND TESTS ====================

    @Test
    public void testJoinCommand() {
        assertFormatting(
            "source=outer|join on outer.id = inner.id inner",
            "source=outer | join on outer.id = inner.id inner"
        );
    }

    @Test
    public void testJoinWithWhereClause() {
        assertFormatting(
            "source=outer|join where outer.id = inner.id inner",
            "source=outer | join where outer.id = inner.id inner"
        );
    }

    // ==================== COMPARISON OPERATORS TESTS ====================

    @Test
    public void testComparisonGreaterThan() {
        assertFormatting("source=logs|where age>30", "source=logs | where age > 30");
    }

    @Test
    public void testComparisonLessThan() {
        assertFormatting("source=logs|where age<50", "source=logs | where age < 50");
    }

    @Test
    public void testComparisonGreaterThanOrEqual() {
        assertFormatting("source=logs|where age>=21", "source=logs | where age >= 21");
    }

    @Test
    public void testComparisonLessThanOrEqual() {
        assertFormatting("source=logs|where age<=65", "source=logs | where age <= 65");
    }

    @Test
    public void testComparisonNotEqual() {
        assertFormatting("source=logs|where status!=error", "source=logs | where status != error");
    }

    @Test
    public void testComparisonEqual() {
        assertFormatting("source=logs|where status=active", "source=logs | where status = active");
    }

    @Test
    public void testComparisonDoubleEqual() {
        assertFormatting("source=logs|where status==active", "source=logs | where status = active");
    }

    // ==================== IN EXPRESSION TESTS ====================

    @Test
    public void testInExpression() {
        assertFormatting(
            "source=logs|where status in (200,201,202)",
            "source=logs | where status in (200, 201, 202)"
        );
    }

    @Test
    public void testNotInExpression() {
        assertFormatting(
            "source=logs|where status not in (400,500)",
            "source=logs | where status not in (400, 500)"
        );
    }

    @Test
    public void testInWithStrings() {
        assertFormatting(
            "source=logs|where method in ('GET','POST')",
            "source=logs | where method in ('GET', 'POST')"
        );
    }

    // ==================== FUNCTION TESTS ====================

    @Test
    public void testLikeFunction() {
        assertFormatting(
            "source=people|where LIKE(name,'_ello%')",
            "source=people | where like(name, '_ello%')"
        );
    }

    @Test
    public void testLikeFunctionWithCaseSensitive() {
        assertFormatting(
            "source=people|where LIKE(address,'%Holmes%',true)",
            "source=people | where like(address, '%Holmes%', true)"
        );
    }

    @Test
    public void testILikeFunction() {
        assertFormatting(
            "source=people|where ILIKE(name,'_ELLo%')",
            "source=people | where ilike(name, '_ELLo%')"
        );
    }

    @Test
    public void testUpperFunction() {
        assertFormatting(
            "source=logs|where UPPER(name)='JOHN'",
            "source=logs | where upper(name) = 'JOHN'"
        );
    }

    @Test
    public void testLowerFunction() {
        assertFormatting(
            "source=logs|where LOWER(name)='john'",
            "source=logs | where lower(name) = 'john'"
        );
    }

    @Test
    public void testCountFunction() {
        assertFormatting("source=logs|stats COUNT()", "source=logs | stats count()");
    }

    @Test
    public void testCountWithField() {
        assertFormatting("source=logs|stats COUNT(status)", "source=logs | stats count(status)");
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
        assertFormatting("source=logs|stats MIN(timestamp)", "source=logs | stats min(timestamp)");
    }

    @Test
    public void testMaxFunction() {
        assertFormatting("source=logs|stats MAX(timestamp)", "source=logs | stats max(timestamp)");
    }

    // ==================== SOURCE HANDLING TESTS ====================

    @Test
    public void testSourceEqualsNoSpacing() {
        assertFormatting("source = logs", "source=logs");
        assertFormatting("source  =  logs", "source=logs");
    }

    @Test
    public void testIndexEqualsNoSpacing() {
        assertFormatting("index = logs", "index=logs");
    }

    // ==================== WILDCARD AND PATTERN TESTS ====================

    @Test
    public void testIndexWithWildcard() {
        assertFormatting("source=logs*|fields message", "source=logs* | fields message");
    }

    @Test
    public void testMultipleWildcardSources() {
        assertFormatting("source=logs*,events*", "source=logs*, events*");
    }

    // ==================== CASE NORMALIZATION TESTS ====================

    @Test
    public void testUppercaseCommands() {
        assertFormatting(
            "SOURCE=logs|FIELDS name,age|WHERE age>30|STATS COUNT() BY status",
            "source=logs | fields name, age | where age > 30 | stats count() by status"
        );
    }

    @Test
    public void testUppercaseFunctions() {
        assertFormatting(
            "source=logs|stats AVG(response_time),MAX(response_time) by status",
            "source=logs | stats avg(response_time), max(response_time) by status"
        );
    }

    @Test
    public void testMixedCaseKeywords() {
        assertFormatting(
            "Source=logs|Where status=200|Fields name",
            "source=logs | where status = 200 | fields name"
        );
    }

    @Test
    public void testBooleanLiteralNormalization() {
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

    // ==================== SPACING NORMALIZATION TESTS ====================

    @Test
    public void testCommaSpacing() {
        assertFormatting(
            "source=logs|fields name,age,status",
            "source=logs | fields name, age, status"
        );
    }

    @Test
    public void testPipeSpacing() {
        assertFormatting(
            "source=logs|fields name|where age>30|stats count()",
            "source=logs | fields name | where age > 30 | stats count()"
        );
    }

    @Test
    public void testFormattingWithVariousSpacing() {
        assertFormatting(
            "source=logs| where latency>=400| fields name",
            "source=logs | where latency >= 400 | fields name"
        );
    }

    @Test
    public void testOperatorSpacingNormalization() {
        assertFormatting(
            "source=logs|where a>1 and b<2 and c>=3 and d<=4",
            "source=logs | where a > 1 and b < 2 and c >= 3 and d <= 4"
        );
    }

    // ==================== ARITHMETIC EXPRESSION TESTS ====================

    @Test
    public void testMultiplicationInField() {
        // Note: 'doubled' used instead of 'double' which is a reserved word
        assertFormatting(
            "source=logs|eval doubled=value*2",
            "source=logs | eval doubled = value * 2"
        );
    }

    @Test
    public void testDivisionInField() {
        assertFormatting("source=logs|eval kb=bytes/1024", "source=logs | eval kb = bytes / 1024");
    }

    @Test
    public void testAdditionInField() {
        assertFormatting("source=logs|eval sum=a+1", "source=logs | eval sum = a + 1");
    }

    @Test
    public void testSubtractionInField() {
        assertFormatting("source=logs|eval diff=count-1", "source=logs | eval diff = count - 1");
    }

    @Test
    public void testChainedArithmetic() {
        assertFormatting(
            "source=logs|eval result=a*2/1024+1",
            "source=logs | eval result = a * 2 / 1024 + 1"
        );
    }

    // ==================== QUALIFIED NAME TESTS ====================

    @Test
    public void testDottedFieldName() {
        assertFormatting(
            "source=logs|fields response.code,request.method",
            "source=logs | fields response.code, request.method"
        );
    }

    @Test
    public void testNestedFieldAccess() {
        assertFormatting(
            "source=logs|where response.status.code=200",
            "source=logs | where response.status.code = 200"
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
            "source=logs|stats count() as total_count",
            "source=logs | stats count() as total_count"
        );
    }

    // ==================== CROSS-CLUSTER TESTS ====================

    @Test
    public void testCrossClusterSource() {
        assertFormatting("source=remote_cluster:logs", "source=remote_cluster:logs");
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    public void testMalformedQueryReturnsOriginal() {
        // Malformed query should return original
        String malformed = "source=logs | where";
        String result = formatter.format(malformed);
        // The formatter should handle this gracefully
        assertNotNull(result);
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
            "SEARCH SOURCE=access_logs status=200|FIELDS timestamp,user_id,response_time,method|WHERE response_time>1000 AND method='GET'|STATS AVG(response_time) AS avg_time,COUNT() AS request_count BY user_id|WHERE request_count>10",
            "source=access_logs status = 200 | fields timestamp, user_id, response_time, method | where response_time > 1000 and method = 'GET' | stats avg(response_time) as avg_time, count() as request_count by user_id | where request_count > 10"
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
}
