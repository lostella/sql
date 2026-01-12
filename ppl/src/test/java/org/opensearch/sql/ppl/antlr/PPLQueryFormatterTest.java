/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.antlr;

import static org.junit.Assert.*;

import org.junit.Test;

public class PPLQueryFormatterTest {

    private final PPLQueryFormatter formatter = new PPLQueryFormatter();

    private void assertFormatting(String input, String expected) {
        assertEquals(expected, formatter.format(input));
        assertEquals(expected, formatter.format(expected));
    }

    // ========== BASIC QUERY STRUCTURE ==========
    @Test
    public void testBasicSearchFormatting() {
        assertFormatting("search source=logs", "source=logs");
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

    // ========== DESCRIBE COMMAND ==========
    @Test
    public void testDescribeCommand() {
        assertFormatting("describe logs", "describe logs");
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

    // ========== FIELDS COMMAND ==========
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

    // ========== EVAL COMMAND ==========
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

    // ========== STATS COMMANDS ==========
    @Test
    public void testStatsCommand() {
        assertFormatting(
            "source=logs|stats count() by status",
            "source=logs | stats count() by status"
        );
    }

    @Test
    public void testEventstatsCommand() {
        assertFormatting(
            "source=table|eventstats avg(a) by b",
            "source=table | eventstats avg(a) by b"
        );
    }

    // ========== JOIN COMMAND ==========
    @Test
    public void testJoinCommand() {
        assertFormatting(
            "source=outer|join on outer.id = inner.id inner",
            "source=outer | join on outer.id = inner.id inner"
        );
    }

    // ========== WHERE COMMAND & COMPARISON OPERATORS ==========
    @Test
    public void testComparisonOperators() {
        assertFormatting("source=logs|where age>30", "source=logs | where age > 30");
        assertFormatting("source=logs|where age<50", "source=logs | where age < 50");
        assertFormatting("source=logs|where age>=21", "source=logs | where age >= 21");
        assertFormatting("source=logs|where age<=65", "source=logs | where age <= 65");
        assertFormatting("source=logs|where status!=error", "source=logs | where status != error");
    }

    @Test
    public void testFormattingWithVariousSpacing() {
        assertFormatting(
            "source=logs| where latency>=400| fields name",
            "source=logs | where latency >= 400 | fields name"
        );
    }

    // ========== LIKE FUNCTIONS ==========
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

    // ========== EQUALS OPERATOR & SOURCE HANDLING ==========
    @Test
    public void testEqualsOperatorSpacing() {
        assertFormatting("source=logs|where status=active", "source=logs | where status = active");
        assertFormatting(
            "source=logs|eval new_field=old_field",
            "source=logs | eval new_field = old_field"
        );
    }

    @Test
    public void testSourceEqualsNoSpacing() {
        assertFormatting("source = logs", "source=logs");
        assertFormatting("source  =  logs", "source=logs");
    }

    // ========== INDEX PATTERNS & WILDCARDS ==========
    @Test
    public void testIndexWithWildcard() {
        assertFormatting("source=logs*|fields message", "source=logs* | fields message");
    }

    // ========== CASE NORMALIZATION ==========
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

    // ========== SPACING NORMALIZATION ==========
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
}
