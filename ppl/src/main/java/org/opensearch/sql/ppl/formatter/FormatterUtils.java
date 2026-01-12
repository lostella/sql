/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

import static org.opensearch.sql.ppl.formatter.FormatterConstants.*;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Utility methods for PPL query formatting.
 *
 * <p>This class provides common helper methods used throughout the formatting process, including
 * string manipulation, null-safe operations, and regex-based transformations.
 */
public final class FormatterUtils {

    private FormatterUtils() {
        // Utility class - prevent instantiation
    }

    // ========== Pre-compiled Regex Patterns ==========
    // These are compiled once and reused for better performance

    /** Pattern for word*digit (e.g., "age*2"). */
    private static final Pattern PATTERN_WORD_MULT_DIGIT = Pattern.compile(REGEX_WORD_MULT_DIGIT);

    /** Pattern for digit*word (e.g., "2*age"). */
    private static final Pattern PATTERN_DIGIT_MULT_WORD = Pattern.compile(REGEX_DIGIT_MULT_WORD);

    /** Pattern for word/digit (e.g., "bytes/1024"). */
    private static final Pattern PATTERN_WORD_DIV_DIGIT = Pattern.compile(REGEX_WORD_DIV_DIGIT);

    /** Pattern for digit/digit (e.g., "1024/1024"). */
    private static final Pattern PATTERN_DIGIT_DIV_DIGIT = Pattern.compile(REGEX_DIGIT_DIV_DIGIT);

    /** Pattern for word+digit (e.g., "count+1"). */
    private static final Pattern PATTERN_WORD_PLUS_DIGIT = Pattern.compile(REGEX_WORD_PLUS_DIGIT);

    /** Pattern for word-digit (e.g., "count-1"). */
    private static final Pattern PATTERN_WORD_MINUS_DIGIT = Pattern.compile(REGEX_WORD_MINUS_DIGIT);

    // ========== String Joining Methods ==========

    /**
     * Joins a list of strings with a comma separator.
     *
     * @param items the list of strings to join
     * @return the joined string with comma separators
     */
    public static String joinWithComma(List<String> items) {
        return joinWith(items, COMMA_SEPARATOR);
    }

    /**
     * Joins a list of strings with a dot separator.
     *
     * @param items the list of strings to join
     * @return the joined string with dot separators
     */
    public static String joinWithDot(List<String> items) {
        return joinWith(items, DOT_SEPARATOR);
    }

    /**
     * Joins a list of strings with a space separator.
     *
     * @param items the list of strings to join
     * @return the joined string with space separators
     */
    public static String joinWithSpace(List<String> items) {
        return joinWith(items, SPACE);
    }

    /**
     * Joins a list of strings with the specified separator.
     *
     * @param items the list of strings to join
     * @param separator the separator to use between items
     * @return the joined string
     */
    public static String joinWith(List<String> items, String separator) {
        if (items == null || items.isEmpty()) {
            return EMPTY;
        }
        return items.stream().filter(Objects::nonNull).collect(Collectors.joining(separator));
    }

    // ========== Null-Safe Methods ==========

    /**
     * Returns the given string or empty string if null.
     *
     * @param value the string value (may be null)
     * @return the value or empty string
     */
    public static String nullToEmpty(String value) {
        return value != null ? value : EMPTY;
    }

    /**
     * Checks if a string is null or empty.
     *
     * @param value the string to check
     * @return true if null or empty
     */
    public static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * Checks if a string is not null and not empty.
     *
     * @param value the string to check
     * @return true if not null and not empty
     */
    public static boolean isNotEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    // ========== Text Extraction ==========

    /**
     * Extracts the original text from a parser rule context using the token stream.
     *
     * <p>This method safely handles null contexts and missing start/stop tokens.
     *
     * @param ctx the parser rule context
     * @param tokenStream the token stream containing the original text
     * @return the original text, or empty string if unavailable
     */
    public static String getOriginalText(ParserRuleContext ctx, CommonTokenStream tokenStream) {
        if (ctx == null) {
            return EMPTY;
        }
        if (ctx.start == null || ctx.stop == null) {
            return EMPTY;
        }
        return tokenStream.getText(ctx.start, ctx.stop);
    }

    /**
     * Gets the text of a parse tree node, or empty string if null.
     *
     * @param node the parse tree node
     * @return the text or empty string
     */
    public static String getTextOrEmpty(ParseTree node) {
        return node != null ? node.getText() : EMPTY;
    }

    // ========== Case Normalization ==========

    /**
     * Normalizes a keyword to lowercase.
     *
     * <p>PPL keywords should be formatted in lowercase for consistency (e.g., WHERE -> where).
     *
     * @param keyword the keyword to normalize
     * @return the lowercase keyword
     */
    public static String normalizeKeyword(String keyword) {
        return keyword != null ? keyword.toLowerCase() : EMPTY;
    }

    /**
     * Preserves the original case of an identifier.
     *
     * <p>Identifiers (field names, table names) should preserve their original case as they may be
     * case-sensitive in the underlying data store.
     *
     * @param identifier the identifier
     * @return the identifier with preserved case
     */
    public static String preserveIdentifier(String identifier) {
        return nullToEmpty(identifier);
    }

    // ========== Arithmetic Pattern Formatting ==========

    /**
     * Formats arithmetic expressions that may have been incorrectly lexed as single identifiers.
     *
     * <p>This is a heuristic workaround for cases where the lexer treats expressions like "age*2"
     * as a single token. The method adds proper spacing around operators.
     *
     * <p><b>Known Limitations:</b>
     * <ul>
     *   <li>May incorrectly format legitimate field names containing operators (rare)</li>
     *   <li>This is a workaround; ideally the grammar should be fixed</li>
     * </ul>
     *
     * @param fieldName the field name which may contain embedded arithmetic
     * @return the formatted field name with proper operator spacing
     */
    public static String formatArithmeticInFieldName(String fieldName) {
        if (fieldName == null) {
            return EMPTY;
        }

        String result = fieldName;

        // Handle multiplication: word*number or number*word
        result = PATTERN_WORD_MULT_DIGIT.matcher(result).replaceAll(SPACED_MULTIPLY);
        result = PATTERN_DIGIT_MULT_WORD.matcher(result).replaceAll(SPACED_MULTIPLY);

        // Handle division: word/number or number/number
        result = PATTERN_WORD_DIV_DIGIT.matcher(result).replaceAll(SPACED_DIVIDE);
        result = PATTERN_DIGIT_DIV_DIGIT.matcher(result).replaceAll(SPACED_DIVIDE);

        // Handle addition: word+number
        result = PATTERN_WORD_PLUS_DIGIT.matcher(result).replaceAll(SPACED_PLUS);

        // Handle subtraction: word-number (but not hyphenated identifiers like "my-field")
        result = PATTERN_WORD_MINUS_DIGIT.matcher(result).replaceAll(SPACED_MINUS);

        return result;
    }

    // ========== Formatting Helpers ==========

    /**
     * Formats a function call with its arguments.
     *
     * @param functionName the name of the function
     * @param args the formatted argument string (may be empty or null)
     * @return the formatted function call
     */
    public static String formatFunctionCall(String functionName, String args) {
        if (isEmpty(args)) {
            return String.format(FUNC_NO_ARGS_FORMAT, normalizeKeyword(functionName));
        }
        return String.format(FUNC_WITH_ARGS_FORMAT, normalizeKeyword(functionName), args);
    }

    /**
     * Formats a binary expression with proper spacing.
     *
     * @param left the left operand
     * @param operator the operator
     * @param right the right operand
     * @return the formatted binary expression
     */
    public static String formatBinaryExpr(String left, String operator, String right) {
        return String.format(BINARY_EXPR_FORMAT, left, operator, right);
    }

    /**
     * Formats an assignment expression.
     *
     * @param target the assignment target (left-hand side)
     * @param value the value being assigned (right-hand side)
     * @return the formatted assignment
     */
    public static String formatAssignment(String target, String value) {
        return String.format(ASSIGNMENT_FORMAT, target, value);
    }

    /**
     * Wraps a string in parentheses.
     *
     * @param content the content to wrap
     * @return the content wrapped in parentheses
     */
    public static String wrapInParens(String content) {
        return LPAREN + nullToEmpty(content) + RPAREN;
    }

    /**
     * Appends an alias clause if the alias is present.
     *
     * @param sb the StringBuilder to append to
     * @param alias the alias string (may be null or empty)
     */
    public static void appendAlias(StringBuilder sb, String alias) {
        if (isNotEmpty(alias)) {
            sb.append(SPACE).append(KEYWORD_AS).append(SPACE).append(alias);
        }
    }

    /**
     * Appends a keyword with a leading space.
     *
     * @param sb the StringBuilder to append to
     * @param keyword the keyword to append
     */
    public static void appendKeyword(StringBuilder sb, String keyword) {
        sb.append(SPACE).append(keyword);
    }

    /**
     * Appends content with a leading space if the content is not empty.
     *
     * @param sb the StringBuilder to append to
     * @param content the content to append (may be null or empty)
     */
    public static void appendIfNotEmpty(StringBuilder sb, String content) {
        if (isNotEmpty(content)) {
            sb.append(SPACE).append(content);
        }
    }

    /**
     * Creates a new StringBuilder initialized with the given content.
     *
     * @param initial the initial content
     * @return a new StringBuilder
     */
    public static StringBuilder builder(String initial) {
        return new StringBuilder(initial);
    }
}
