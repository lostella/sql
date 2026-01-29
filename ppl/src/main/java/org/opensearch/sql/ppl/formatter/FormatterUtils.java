/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

import static org.opensearch.sql.ppl.formatter.FormatterConstants.*;

import java.util.List;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;

/** Utility methods for PPL query formatting. */
public final class FormatterUtils {

    private FormatterUtils() {}

    // Pre-compiled patterns for arithmetic detection in field names
    private static final Pattern WORD_MULT_DIGIT = Pattern.compile("(?<=\\w)\\*(?=\\d)");
    private static final Pattern DIGIT_MULT_WORD = Pattern.compile("(?<=\\d)\\*(?=\\w)");
    private static final Pattern WORD_DIV_DIGIT = Pattern.compile("(?<=\\w)/(?=\\d)");
    private static final Pattern DIGIT_DIV_DIGIT = Pattern.compile("(?<=\\d)/(?=\\d)");
    private static final Pattern WORD_PLUS_DIGIT = Pattern.compile("(?<=\\w)\\+(?=\\d)");
    private static final Pattern WORD_MINUS_DIGIT = Pattern.compile("(?<=\\w)-(?=\\d)");

    public static String getOriginalText(ParserRuleContext ctx, CommonTokenStream tokenStream) {
        if (ctx == null || ctx.start == null || ctx.stop == null) {
            return EMPTY;
        }
        return tokenStream.getText(ctx.start, ctx.stop);
    }

    public static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public static boolean isNotEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    public static String nullToEmpty(String value) {
        return value != null ? value : EMPTY;
    }

    public static String normalizeKeyword(String keyword) {
        return keyword != null ? keyword.toLowerCase() : EMPTY;
    }

    public static String preserveIdentifier(String identifier) {
        return nullToEmpty(identifier);
    }

    public static String joinWithSpace(List<String> items) {
        if (items == null || items.isEmpty()) {
            return EMPTY;
        }
        return String.join(SPACE, items);
    }

    public static String formatBinaryExpr(String left, String operator, String right) {
        return left + SPACE + operator + SPACE + right;
    }

    public static String formatAssignment(String target, String value) {
        return target + " = " + value;
    }

    public static String formatFunctionCall(String functionName, String args) {
        String name = normalizeKeyword(functionName);
        return isEmpty(args) ? name + "()" : name + "(" + args + ")";
    }

    public static String wrapInParens(String content) {
        return LPAREN + nullToEmpty(content) + RPAREN;
    }

    /**
     * Formats arithmetic expressions that may have been incorrectly lexed as single identifiers. This
     * handles cases like "age*2" being parsed as a single field name.
     */
    public static String formatArithmeticInFieldName(String fieldName) {
        if (fieldName == null) {
            return EMPTY;
        }
        String result = fieldName;
        result = WORD_MULT_DIGIT.matcher(result).replaceAll(" * ");
        result = DIGIT_MULT_WORD.matcher(result).replaceAll(" * ");
        result = WORD_DIV_DIGIT.matcher(result).replaceAll(" / ");
        result = DIGIT_DIV_DIGIT.matcher(result).replaceAll(" / ");
        result = WORD_PLUS_DIGIT.matcher(result).replaceAll(" + ");
        result = WORD_MINUS_DIGIT.matcher(result).replaceAll(" - ");
        return result;
    }
}
