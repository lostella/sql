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
    public void testStatsCommand() {
        assertFormatting(
            "source=logs|stats count() by status",
            "source=logs | stats count() by status"
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

    @Test
    public void testIndexWithWildcard() {
        assertFormatting("source=logs*|fields message", "source=logs* | fields message");
    }

    @Test
    public void testEventstatsCommand() {
        assertFormatting(
            "source=table|eventstats avg(a) by b",
            "source=table | eventstats avg(a) by b"
        );
    }

    @Test
    public void testJoinCommand() {
        assertFormatting(
            "source=outer|join on outer.id = inner.id inner",
            "source=outer | join on outer.id = inner.id inner"
        );
    }

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
    public void testFormattingWithVariousSpacing() {
        assertFormatting(
            "source=logs| where latency>=400| fields name",
            "source=logs | where latency >= 400 | fields name"
        );
    }
}
