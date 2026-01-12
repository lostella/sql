/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

/**
 * Constants used by the PPL Query Formatter.
 *
 * <p>This class centralizes all string literals and formatting tokens used during PPL query
 * formatting, ensuring consistency and making maintenance easier.
 */
public final class FormatterConstants {

    private FormatterConstants() {
        // Utility class - prevent instantiation
    }

    // ========== PPL Keywords ==========

    /** The 'source=' keyword for specifying data source. */
    public static final String KEYWORD_SOURCE = "source=";

    /** The 'index=' keyword (alternative to source). */
    public static final String KEYWORD_INDEX = "index=";

    /** The 'where' command keyword. */
    public static final String KEYWORD_WHERE = "where";

    /** The 'fields' command keyword. */
    public static final String KEYWORD_FIELDS = "fields";

    /** The 'stats' command keyword. */
    public static final String KEYWORD_STATS = "stats";

    /** The 'eventstats' command keyword. */
    public static final String KEYWORD_EVENTSTATS = "eventstats";

    /** The 'eval' command keyword. */
    public static final String KEYWORD_EVAL = "eval";

    /** The 'describe' command keyword. */
    public static final String KEYWORD_DESCRIBE = "describe";

    /** The 'join' command keyword. */
    public static final String KEYWORD_JOIN = "join";

    /** The 'by' keyword for grouping. */
    public static final String KEYWORD_BY = "by";

    /** The 'as' keyword for aliasing. */
    public static final String KEYWORD_AS = "as";

    /** The 'on' keyword for join conditions. */
    public static final String KEYWORD_ON = "on";

    /** The 'in' keyword for IN expressions. */
    public static final String KEYWORD_IN = "in";

    /** The 'not' keyword for negation. */
    public static final String KEYWORD_NOT = "not";

    /** The 'and' logical operator. */
    public static final String KEYWORD_AND = "and";

    /** The 'or' logical operator. */
    public static final String KEYWORD_OR = "or";

    /** The 'xor' logical operator. */
    public static final String KEYWORD_XOR = "xor";

    /** The 'partitions' stats argument. */
    public static final String KEYWORD_PARTITIONS = "partitions";

    /** The 'allnum' stats argument. */
    public static final String KEYWORD_ALLNUM = "allnum";

    /** The 'delim' stats argument. */
    public static final String KEYWORD_DELIM = "delim";

    /** The 'bucket_nullable' stats argument. */
    public static final String KEYWORD_BUCKET_NULLABLE = "bucket_nullable";

    // ========== Operators ==========

    /** Equality operator. */
    public static final String OP_EQUAL = "=";

    /** Not equal operator. */
    public static final String OP_NOT_EQUAL = "!=";

    /** Less than operator. */
    public static final String OP_LESS = "<";

    /** Greater than operator. */
    public static final String OP_GREATER = ">";

    /** Less than or equal operator. */
    public static final String OP_LESS_EQUAL = "<=";

    /** Greater than or equal operator. */
    public static final String OP_GREATER_EQUAL = ">=";

    /** Multiplication operator. */
    public static final String OP_MULTIPLY = "*";

    /** Division operator. */
    public static final String OP_DIVIDE = "/";

    /** Addition operator. */
    public static final String OP_PLUS = "+";

    /** Subtraction operator. */
    public static final String OP_MINUS = "-";

    // ========== Delimiters & Separators ==========

    /** Pipe separator between PPL commands. */
    public static final String PIPE_SEPARATOR = " | ";

    /** Comma separator between items in a list. */
    public static final String COMMA_SEPARATOR = ", ";

    /** Dot separator for qualified names. */
    public static final String DOT_SEPARATOR = ".";

    /** Space character. */
    public static final String SPACE = " ";

    /** Opening parenthesis. */
    public static final String LPAREN = "(";

    /** Closing parenthesis. */
    public static final String RPAREN = ")";

    /** Empty string. */
    public static final String EMPTY = "";

    /** Plus sign with space for field inclusion. */
    public static final String FIELD_INCLUDE = "+ ";

    /** Minus sign with space for field exclusion. */
    public static final String FIELD_EXCLUDE = "- ";

    // ========== Function Names ==========

    /** Count function name. */
    public static final String FUNC_COUNT = "count";

    /** Distinct count function name. */
    public static final String FUNC_DISTINCT_COUNT = "distinct_count";

    /** Distinct count shorthand (dc). */
    public static final String FUNC_DC = "dc";

    /** Distinct count approximate function name. */
    public static final String FUNC_DISTINCT_COUNT_APPROX = "distinct_count_approx";

    // ========== Formatting Patterns ==========

    /**
     * Format pattern for spaced operators. Usage: String.format(SPACED_OPERATOR_FORMAT, operator)
     */
    public static final String SPACED_OPERATOR_FORMAT = " %s ";

    /** Format pattern for assignment. Usage: String.format(ASSIGNMENT_FORMAT, left, right) */
    public static final String ASSIGNMENT_FORMAT = "%s = %s";

    /** Format pattern for binary expressions. */
    public static final String BINARY_EXPR_FORMAT = "%s %s %s";

    /**
     * Format pattern for function calls without arguments. Usage: String.format(FUNC_NO_ARGS,
     * funcName)
     */
    public static final String FUNC_NO_ARGS_FORMAT = "%s()";

    /**
     * Format pattern for function calls with arguments. Usage: String.format(FUNC_WITH_ARGS,
     * funcName, args)
     */
    public static final String FUNC_WITH_ARGS_FORMAT = "%s(%s)";

    // ========== Regex Patterns for Arithmetic Detection ==========

    /**
     * Pattern to detect multiplication between word and digit (e.g., "age*2"). Captures: word *
     * digit
     */
    public static final String REGEX_WORD_MULT_DIGIT = "(?<=\\w)\\*(?=\\d)";

    /** Pattern to detect multiplication between digit and word (e.g., "2*age"). */
    public static final String REGEX_DIGIT_MULT_WORD = "(?<=\\d)\\*(?=\\w)";

    /** Pattern to detect division between word and digit (e.g., "bytes/1024"). */
    public static final String REGEX_WORD_DIV_DIGIT = "(?<=\\w)/(?=\\d)";

    /** Pattern to detect division between digits (e.g., "1024/1024"). */
    public static final String REGEX_DIGIT_DIV_DIGIT = "(?<=\\d)/(?=\\d)";

    /** Pattern to detect addition between word and digit (e.g., "count+1"). */
    public static final String REGEX_WORD_PLUS_DIGIT = "(?<=\\w)\\+(?=\\d)";

    /** Pattern to detect subtraction between word and digit (e.g., "count-1"). */
    public static final String REGEX_WORD_MINUS_DIGIT = "(?<=\\w)-(?=\\d)";

    /** Replacement string for spaced multiplication operator. */
    public static final String SPACED_MULTIPLY = " * ";

    /** Replacement string for spaced division operator. */
    public static final String SPACED_DIVIDE = " / ";

    /** Replacement string for spaced addition operator. */
    public static final String SPACED_PLUS = " + ";

    /** Replacement string for spaced subtraction operator. */
    public static final String SPACED_MINUS = " - ";
}
