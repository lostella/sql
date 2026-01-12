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

    // Basic formatting tests
    @Test
    public void testBasicSearchFormatting() {
        assertFormatting("search source=logs", "source=logs");
    }

    @Test
    public void testSearchWithPipeCommands() {
        assertFormatting(
            "source=logs|fields name,age|where age>30",
            "source=logs | fields name, age | where age > 30"
        );
    }

    @Test
    public void testComplexQuery() {
        assertFormatting(
            "search source=access_logs status=200|fields timestamp,ip,response_time|where response_time>1000|stats avg(response_time) by ip",
            "source=access_logs status = 200 | fields timestamp, ip, response_time | where response_time > 1000 | stats avg(response_time) by ip"
        );
    }

    // Describe command tests
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

    // Fields command tests
    @Test
    public void testFieldsWithWildcard() {
        assertFormatting("source=accounts|fields account*", "source=accounts | fields account*");
    }

    @Test
    public void testFieldsWithSuffixWildcard() {
        assertFormatting("source=accounts|fields *name", "source=accounts | fields *name");
    }

    @Test
    public void testFieldsWithExclusion() {
        assertFormatting(
            "source=accounts|fields - account_number",
            "source=accounts | fields - account_number"
        );
    }

    // Index patterns
    @Test
    public void testIndexWithWildcard() {
        assertFormatting("source=logs*|fields message", "source=logs* | fields message");
    }

    // Stats commands
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

    // Join command
    @Test
    public void testJoinCommand() {
        assertFormatting(
            "source=outer|join on outer.id = inner.id inner",
            "source=outer | join on outer.id = inner.id inner"
        );
    }

    // Case normalization tests
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

    // Operator formatting tests
    @Test
    public void testComparisonOperators() {
        assertFormatting("source=logs|where age>30", "source=logs | where age > 30");
        assertFormatting("source=logs|where age<50", "source=logs | where age < 50");
        assertFormatting("source=logs|where age>=21", "source=logs | where age >= 21");
        assertFormatting("source=logs|where age<=65", "source=logs | where age <= 65");
        assertFormatting("source=logs|where status!=error", "source=logs | where status != error");
    }

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

    // Spacing normalization tests
    @Test
    public void testFormattingWithVariousSpacing() {
        assertFormatting(
            "source=logs| where latency>=400| fields name",
            "source=logs | where latency >= 400 | fields name"
        );
    }

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
