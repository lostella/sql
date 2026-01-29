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
