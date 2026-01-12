/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter.visitors;

import static org.opensearch.sql.ppl.formatter.FormatterConstants.*;
import static org.opensearch.sql.ppl.formatter.FormatterUtils.*;

import java.util.List;
import org.antlr.v4.runtime.CommonTokenStream;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLParser;

/**
 * Visitor methods for formatting literal values in PPL queries.
 *
 * <p>This class handles the formatting of various literal types including:
 * <ul>
 *   <li>Integer literals (e.g., 42, -100)</li>
 *   <li>Decimal literals (e.g., 3.14, -2.5)</li>
 *   <li>Boolean literals (true, false)</li>
 *   <li>String literals (e.g., 'hello', "world")</li>
 *   <li>Search literals used in search expressions</li>
 *   <li>Value lists for IN expressions</li>
 * </ul>
 *
 * <p>Literal values are generally preserved as-is from the original query, with the exception
 * of boolean literals which are normalized to lowercase.
 */
public class LiteralVisitors extends BaseFormattingVisitor {

    /**
     * Creates a new literal values visitor.
     *
     * @param tokenStream the token stream from the lexer
     */
    public LiteralVisitors(CommonTokenStream tokenStream) {
        super(tokenStream);
    }

    // ========== General Literal Values ==========

    /**
     * Visits a general literal value context.
     *
     * <p>This is the parent rule that encompasses all literal types. The method preserves
     * the original text representation of the literal.
     *
     * @param ctx the literal value context
     * @return the original text of the literal
     */
    @Override
    public String visitLiteralValue(OpenSearchPPLParser.LiteralValueContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return getOriginalText(ctx);
    }

    // ========== Numeric Literals ==========

    /**
     * Visits an integer literal.
     *
     * <p>Integer literals are preserved exactly as written in the original query,
     * including any sign prefix.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code 42} -> {@code 42}</li>
     *   <li>{@code -100} -> {@code -100}</li>
     *   <li>{@code 0} -> {@code 0}</li>
     * </ul>
     *
     * @param ctx the integer literal context
     * @return the integer literal text
     */
    @Override
    public String visitIntegerLiteral(OpenSearchPPLParser.IntegerLiteralContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return ctx.getText();
    }

    /**
     * Visits a decimal literal.
     *
     * <p>Decimal literals are preserved exactly as written in the original query,
     * maintaining the original precision and format.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code 3.14} -> {@code 3.14}</li>
     *   <li>{@code -2.5} -> {@code -2.5}</li>
     *   <li>{@code 0.001} -> {@code 0.001}</li>
     * </ul>
     *
     * @param ctx the decimal literal context
     * @return the decimal literal text
     */
    @Override
    public String visitDecimalLiteral(OpenSearchPPLParser.DecimalLiteralContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return ctx.getText();
    }

    // ========== Boolean Literals ==========

    /**
     * Visits a boolean literal.
     *
     * <p>Boolean literals are normalized to lowercase for consistency. PPL accepts
     * various case combinations (TRUE, True, true) but the formatter outputs lowercase.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code TRUE} -> {@code true}</li>
     *   <li>{@code False} -> {@code false}</li>
     *   <li>{@code true} -> {@code true}</li>
     * </ul>
     *
     * @param ctx the boolean literal context
     * @return the lowercase boolean literal
     */
    @Override
    public String visitBooleanLiteral(OpenSearchPPLParser.BooleanLiteralContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return normalizeKeyword(ctx.getText());
    }

    // ========== String Literals ==========

    /**
     * Visits a string literal.
     *
     * <p>String literals are preserved exactly as written, including their quote characters
     * (single or double quotes). The content within the quotes is not modified.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code 'hello'} -> {@code 'hello'}</li>
     *   <li>{@code "world"} -> {@code "world"}</li>
     *   <li>{@code 'it\'s'} -> {@code 'it\'s'}</li>
     * </ul>
     *
     * @param ctx the string literal context
     * @return the original string literal with quotes
     */
    @Override
    public String visitStringLiteral(OpenSearchPPLParser.StringLiteralContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return getOriginalText(ctx);
    }

    // ========== Search Literals ==========

    /**
     * Visits a search literal used in search expressions.
     *
     * <p>Search literals can be various types of values used in field comparisons
     * within search commands. They are preserved as-is from the original query.
     *
     * @param ctx the search literal context
     * @return the original search literal text
     */
    @Override
    public String visitSearchLiteral(OpenSearchPPLParser.SearchLiteralContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return getOriginalText(ctx);
    }

    // ========== Value Lists ==========

    /**
     * Visits a value list, typically used in IN expressions.
     *
     * <p>Value lists are formatted as comma-separated values enclosed in parentheses,
     * with consistent spacing after each comma.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code (1,2,3)} -> {@code (1, 2, 3)}</li>
     *   <li>{@code ('a','b')} -> {@code ('a', 'b')}</li>
     *   <li>{@code (100)} -> {@code (100)}</li>
     * </ul>
     *
     * @param ctx the value list context
     * @return the formatted value list with parentheses
     */
    @Override
    public String visitValueList(OpenSearchPPLParser.ValueListContext ctx) {
        if (ctx == null) {
            return wrapInParens(EMPTY);
        }

        List<OpenSearchPPLParser.LiteralValueContext> values = ctx.literalValue();
        if (values == null || values.isEmpty()) {
            return wrapInParens(EMPTY);
        }

        String formattedValues = visitAndJoinWithComma(values);
        return wrapInParens(formattedValues);
    }

    // ========== Interval Literals ==========

    /**
     * Visits an interval literal used for time-based operations.
     *
     * <p>Interval literals specify a duration and unit, such as "INTERVAL 5 MINUTE".
     * The interval keyword and unit are normalized to lowercase.
     *
     * @param ctx the interval literal context
     * @return the formatted interval literal
     */
    @Override
    public String visitIntervalLiteral(OpenSearchPPLParser.IntervalLiteralContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();
        sb.append("interval ");
        sb.append(safeVisit(ctx.valueExpression()));
        sb.append(SPACE);
        sb.append(safeVisit(ctx.intervalUnit()));
        return sb.toString();
    }

    /**
     * Visits an interval unit (e.g., SECOND, MINUTE, HOUR, DAY).
     *
     * <p>Interval units are normalized to lowercase for consistency.
     *
     * @param ctx the interval unit context
     * @return the lowercase interval unit
     */
    @Override
    public String visitIntervalUnit(OpenSearchPPLParser.IntervalUnitContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return normalizeKeyword(ctx.getText());
    }

    // ========== Date/Time Literals ==========

    /**
     * Visits a datetime literal.
     *
     * <p>Datetime literals are preserved as-is from the original query.
     *
     * @param ctx the datetime literal context
     * @return the original datetime literal text
     */
    @Override
    public String visitDatetimeLiteral(OpenSearchPPLParser.DatetimeLiteralContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return getOriginalText(ctx);
    }
}
