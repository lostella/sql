/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.antlr;

/** PPL Query Formatter that formats PPL queries with consistent style. */
public class PPLQueryFormatter {

    /** Format a PPL query string. */
    public String format(String query) {
        // Simple formatter that normalizes spacing
        String formatted = query
            .replaceAll("\\s*\\|\\s*", " | ") // Normalize pipe spacing
            .replaceAll("\\s*,\\s*", ", ") // Normalize comma spacing
            .replaceAll("^search\\s+", "") // Remove search prefix
            .replaceAll("\\s+", " ") // Normalize whitespace
            .trim();

        // Convert PPL keywords to lowercase (but preserve identifiers)
        formatted = convertKeywordsToLowercase(formatted);

        // Handle compound operators first (before single operators)
        formatted = formatted.replaceAll("\\s*>=\\s*", " >= ");
        formatted = formatted.replaceAll("\\s*<=\\s*", " <= ");
        formatted = formatted.replaceAll("\\s*!=\\s*", " != ");

        // Handle arithmetic operators (but preserve wildcards)
        formatted = formatted.replaceAll("(?<=\\w)\\s*\\*\\s*(?=\\w)", " * "); // Multiplication between words/numbers
        formatted = formatted.replaceAll("\\s*/\\s*", " / ");
        formatted = formatted.replaceAll("\\s*\\+\\s*", " + ");
        formatted = formatted.replaceAll("(?<=\\w)\\s*-\\s*(?=\\w)", " - "); // Subtraction between words/numbers

        // Handle single operators (but avoid breaking compound operators)
        formatted = formatted.replaceAll("(?<!>)\\s*>\\s*(?!=)", " > ");
        formatted = formatted.replaceAll("(?<!<)\\s*<\\s*(?!=)", " < ");

        // Fix source= to not have spaces around equals
        formatted = formatted.replaceAll("source\\s*=\\s*", "source=");

        // Add spaces around other equals signs (but not source=, and not compound operators)
        formatted = formatted.replaceAll("(?<!source)(?<!>)(?<!<)(?<!!)\\s*=\\s*", " = ");

        return formatted;
    }

    private String convertKeywordsToLowercase(String query) {
        // Convert PPL keywords and functions to lowercase while preserving identifiers
        String[] keywords = {
            "SOURCE",
            "DESCRIBE",
            "FIELDS",
            "WHERE",
            "STATS",
            "EVENTSTATS",
            "JOIN",
            "EVAL",
            "ON",
            "BY",
            "AS",
            "IN",
            "AND",
            "OR",
            "NOT",
        };
        String[] functions = { "COUNT", "AVG", "SUM", "MIN", "MAX", "LIKE", "ILIKE" };

        String result = query;

        // Convert keywords (word boundaries to avoid partial matches)
        for (String keyword : keywords) {
            result = result.replaceAll("(?i)\\b" + keyword + "\\b", keyword.toLowerCase());
        }

        // Convert function names (followed by opening parenthesis)
        for (String function : functions) {
            result = result.replaceAll(
                "(?i)\\b" + function + "(?=\\s*\\()",
                function.toLowerCase()
            );
        }

        return result;
    }
}
