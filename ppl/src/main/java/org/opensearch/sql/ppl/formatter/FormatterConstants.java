/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

/** Constants used by the PPL Query Formatter. */
public final class FormatterConstants {

    private FormatterConstants() {}

    // Keywords
    public static final String KEYWORD_SOURCE = "source=";
    public static final String KEYWORD_INDEX = "index=";
    public static final String KEYWORD_WHERE = "where";
    public static final String KEYWORD_FIELDS = "fields";
    public static final String KEYWORD_STATS = "stats";
    public static final String KEYWORD_EVENTSTATS = "eventstats";
    public static final String KEYWORD_EVAL = "eval";
    public static final String KEYWORD_DESCRIBE = "describe";
    public static final String KEYWORD_JOIN = "join";
    public static final String KEYWORD_BY = "by";
    public static final String KEYWORD_AS = "as";
    public static final String KEYWORD_ON = "on";
    public static final String KEYWORD_IN = "in";
    public static final String KEYWORD_NOT = "not";
    public static final String KEYWORD_AND = "and";
    public static final String KEYWORD_OR = "or";
    public static final String KEYWORD_XOR = "xor";
    public static final String KEYWORD_PARTITIONS = "partitions";
    public static final String KEYWORD_ALLNUM = "allnum";
    public static final String KEYWORD_DELIM = "delim";
    public static final String KEYWORD_BUCKET_NULLABLE = "bucket_nullable";

    // Command keywords
    public static final String KEYWORD_SORT = "sort";
    public static final String KEYWORD_HEAD = "head";
    public static final String KEYWORD_DEDUP = "dedup";
    public static final String KEYWORD_RENAME = "rename";
    public static final String KEYWORD_RARE = "rare";
    public static final String KEYWORD_TOP = "top";
    public static final String KEYWORD_GROK = "grok";
    public static final String KEYWORD_PARSE = "parse";
    public static final String KEYWORD_PATTERNS = "patterns";
    public static final String KEYWORD_FILLNULL = "fillnull";
    public static final String KEYWORD_TRENDLINE = "trendline";
    public static final String KEYWORD_EXPAND = "expand";
    public static final String KEYWORD_FLATTEN = "flatten";
    public static final String KEYWORD_LOOKUP = "lookup";
    public static final String KEYWORD_BIN = "bin";

    // Command option keywords
    public static final String KEYWORD_FROM = "from";
    public static final String KEYWORD_KEEPEMPTY = "keepempty";
    public static final String KEYWORD_CONSECUTIVE = "consecutive";
    public static final String KEYWORD_VALUE = "value";
    public static final String KEYWORD_WITH = "with";
    public static final String KEYWORD_USING = "using";
    public static final String KEYWORD_APPEND = "append";
    public static final String KEYWORD_REPLACE = "replace";
    public static final String KEYWORD_OUTPUT = "output";
    public static final String KEYWORD_SPAN = "span";
    public static final String KEYWORD_BINS = "bins";

    // Operators
    public static final String OP_EQUAL = "=";
    public static final String OP_NOT_EQUAL = "!=";
    public static final String OP_LESS = "<";
    public static final String OP_GREATER = ">";
    public static final String OP_LESS_EQUAL = "<=";
    public static final String OP_GREATER_EQUAL = ">=";
    public static final String OP_MULTIPLY = "*";

    // Delimiters
    public static final String PIPE_SEPARATOR = " | ";
    public static final String COMMA_SEPARATOR = ", ";
    public static final String DOT_SEPARATOR = ".";
    public static final String SPACE = " ";
    public static final String LPAREN = "(";
    public static final String RPAREN = ")";
    public static final String EMPTY = "";
    public static final String FIELD_INCLUDE = "+ ";
    public static final String FIELD_EXCLUDE = "- ";

    // Function names
    public static final String FUNC_COUNT = "count";
    public static final String FUNC_DISTINCT_COUNT = "distinct_count";
    public static final String FUNC_DC = "dc";
    public static final String FUNC_DISTINCT_COUNT_APPROX = "distinct_count_approx";
}
