/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter.visitors;

import static org.opensearch.sql.ppl.formatter.FormatterConstants.*;
import static org.opensearch.sql.ppl.formatter.FormatterUtils.*;

import org.antlr.v4.runtime.CommonTokenStream;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLParser;

/**
 * Visitor methods for formatting expressions in PPL queries.
 *
 * <p>This class handles the formatting of various expression types including:
 * <ul>
 *   <li>Logical expressions (AND, OR, XOR, NOT)</li>
 *   <li>Comparison expressions (=, !=, <, >, <=, >=)</li>
 *   <li>Arithmetic expressions (+, -, *, /)</li>
 *   <li>IN expressions</li>
 *   <li>Field expressions</li>
 *   <li>Nested/parenthesized expressions</li>
 * </ul>
 *
 * <p>All expressions are formatted with consistent spacing around operators.
 * Keywords (and, or, not, in) are normalized to lowercase.
 */
public class ExpressionVisitors extends BaseFormattingVisitor {

    /**
     * Creates a new expression visitor.
     *
     * @param tokenStream the token stream from the lexer
     */
    public ExpressionVisitors(CommonTokenStream tokenStream) {
        super(tokenStream);
    }

    // ========== Logical Expressions ==========

    /**
     * Visits a logical NOT expression.
     *
     * <p>Formats the NOT keyword in lowercase followed by the negated expression.
     *
     * <p>Example: {@code NOT status=active} -> {@code not status = active}
     *
     * @param ctx the logical NOT context
     * @return the formatted NOT expression
     */
    @Override
    public String visitLogicalNot(OpenSearchPPLParser.LogicalNotContext ctx) {
        if (ctx == null || ctx.logicalExpression() == null) {
            return EMPTY;
        }
        return KEYWORD_NOT + SPACE + safeVisit(ctx.logicalExpression());
    }

    /**
     * Visits a logical AND expression.
     *
     * <p>Formats the AND keyword in lowercase with proper spacing around it.
     *
     * <p>Example: {@code a=1 AND b=2} -> {@code a = 1 and b = 2}
     *
     * @param ctx the logical AND context
     * @return the formatted AND expression
     */
    @Override
    public String visitLogicalAnd(OpenSearchPPLParser.LogicalAndContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return formatBinaryExpr(
                safeVisit(ctx.left),
                KEYWORD_AND,
                safeVisit(ctx.right));
    }

    /**
     * Visits a logical OR expression.
     *
     * <p>Formats the OR keyword in lowercase with proper spacing around it.
     *
     * <p>Example: {@code a=1 OR b=2} -> {@code a = 1 or b = 2}
     *
     * @param ctx the logical OR context
     * @return the formatted OR expression
     */
    @Override
    public String visitLogicalOr(OpenSearchPPLParser.LogicalOrContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return formatBinaryExpr(
                safeVisit(ctx.left),
                KEYWORD_OR,
                safeVisit(ctx.right));
    }

    /**
     * Visits a logical XOR expression.
     *
     * <p>Formats the XOR keyword in lowercase with proper spacing around it.
     *
     * <p>Example: {@code a=1 XOR b=2} -> {@code a = 1 xor b = 2}
     *
     * @param ctx the logical XOR context
     * @return the formatted XOR expression
     */
    @Override
    public String visitLogicalXor(OpenSearchPPLParser.LogicalXorContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return formatBinaryExpr(
                safeVisit(ctx.left),
                KEYWORD_XOR,
                safeVisit(ctx.right));
    }

    /**
     * Visits a logical expression wrapper.
     *
     * <p>This is a pass-through that delegates to the contained expression.
     *
     * @param ctx the logical expression context
     * @return the formatted contained expression
     */
    @Override
    public String visitLogicalExpr(OpenSearchPPLParser.LogicalExprContext ctx) {
        if (ctx == null || ctx.expression() == null) {
            return EMPTY;
        }
        return safeVisit(ctx.expression());
    }

    // ========== Comparison Expressions ==========

    /**
     * Visits a comparison expression.
     *
     * <p>Formats comparison operators with proper spacing. The operator is normalized
     * to a canonical form (e.g., == becomes =).
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code age>30} -> {@code age > 30}</li>
     *   <li>{@code status==active} -> {@code status = active}</li>
     *   <li>{@code count!=0} -> {@code count != 0}</li>
     * </ul>
     *
     * @param ctx the comparison expression context
     * @return the formatted comparison expression
     */
    @Override
    public String visitCompareExpr(OpenSearchPPLParser.CompareExprContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        String left = safeVisit(ctx.left);
        String right = safeVisit(ctx.right);
        String op = formatComparisonOperator(ctx.comparisonOperator());
        return formatBinaryExpr(left, op, right);
    }

    /**
     * Formats a comparison operator to its canonical form.
     *
     * <p>Operator normalization:
     * <ul>
     *   <li>{@code =} and {@code ==} both become {@code =}</li>
     *   <li>{@code !=} stays as {@code !=}</li>
     *   <li>{@code <} stays as {@code <}</li>
     *   <li>{@code >} stays as {@code >}</li>
     *   <li>{@code >=} (or NOT_LESS) stays as {@code >=}</li>
     *   <li>{@code <=} (or NOT_GREATER) stays as {@code <=}</li>
     * </ul>
     *
     * @param ctx the comparison operator context
     * @return the canonical operator string
     */
    private String formatComparisonOperator(OpenSearchPPLParser.ComparisonOperatorContext ctx) {
        if (ctx == null) {
            return OP_EQUAL;
        }
        if (ctx.EQUAL() != null || ctx.DOUBLE_EQUAL() != null) {
            return OP_EQUAL;
        }
        if (ctx.NOT_EQUAL() != null) {
            return OP_NOT_EQUAL;
        }
        if (ctx.LESS() != null) {
            return OP_LESS;
        }
        if (ctx.GREATER() != null) {
            return OP_GREATER;
        }
        if (ctx.NOT_LESS() != null) {
            return OP_GREATER_EQUAL;
        }
        if (ctx.NOT_GREATER() != null) {
            return OP_LESS_EQUAL;
        }
        // Fallback to original text
        return ctx.getText();
    }

    /**
     * Visits an IN expression.
     *
     * <p>Formats IN expressions with proper spacing and parentheses around the value list.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code status in (1,2,3)} -> {@code status in (1, 2, 3)}</li>
     *   <li>{@code status not in ('a','b')} -> {@code status not in ('a', 'b')}</li>
     * </ul>
     *
     * @param ctx the IN expression context
     * @return the formatted IN expression
     */
    @Override
    public String visitInExpr(OpenSearchPPLParser.InExprContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        StringBuilder sb = newBuilder();
        sb.append(safeVisit(ctx.expression()));

        if (ctx.NOT() != null) {
            sb.append(SPACE).append(KEYWORD_NOT);
        }
        sb.append(SPACE).append(KEYWORD_IN).append(SPACE);
        sb.append(safeVisit(ctx.valueList()));

        return sb.toString();
    }

    // ========== Arithmetic Expressions ==========

    /**
     * Visits a binary arithmetic expression.
     *
     * <p>Formats arithmetic operators (+, -, *, /) with proper spacing around them.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code a+b} -> {@code a + b}</li>
     *   <li>{@code bytes/1024} -> {@code bytes / 1024}</li>
     *   <li>{@code count*2} -> {@code count * 2}</li>
     * </ul>
     *
     * @param ctx the binary arithmetic context
     * @return the formatted arithmetic expression
     */
    @Override
    public String visitBinaryArithmetic(OpenSearchPPLParser.BinaryArithmeticContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        String left = safeVisit(ctx.valueExpression(0));
        String right = safeVisit(ctx.valueExpression(1));
        String op = ctx.binaryOperator != null ? ctx.binaryOperator.getText() : EMPTY;
        return formatBinaryExpr(left, op, right);
    }

    // ========== Value Expressions ==========

    /**
     * Visits a value expression wrapper.
     *
     * <p>This is a pass-through that delegates to the contained value expression.
     *
     * @param ctx the value expression context
     * @return the formatted value expression
     */
    @Override
    public String visitValueExpr(OpenSearchPPLParser.ValueExprContext ctx) {
        if (ctx == null || ctx.valueExpression() == null) {
            return EMPTY;
        }
        return safeVisit(ctx.valueExpression());
    }

    /**
     * Visits a literal value expression.
     *
     * <p>This is a pass-through that delegates to the literal value visitor.
     *
     * @param ctx the literal value expression context
     * @return the formatted literal value
     */
    @Override
    public String visitLiteralValueExpr(OpenSearchPPLParser.LiteralValueExprContext ctx) {
        if (ctx == null || ctx.literalValue() == null) {
            return EMPTY;
        }
        return safeVisit(ctx.literalValue());
    }

    /**
     * Visits a field expression in a value context.
     *
     * <p>This method also applies heuristic formatting for arithmetic expressions
     * that may have been lexed as single field identifiers (e.g., "age*2").
     *
     * @param ctx the field expression context
     * @return the formatted field expression
     */
    @Override
    public String visitFieldExpr(OpenSearchPPLParser.FieldExprContext ctx) {
        if (ctx == null || ctx.fieldExpression() == null) {
            return EMPTY;
        }
        String fieldName = safeVisit(ctx.fieldExpression());
        // Apply heuristic to detect and format embedded arithmetic
        return formatArithmeticInFieldName(fieldName);
    }

    /**
     * Visits a nested (parenthesized) value expression.
     *
     * <p>Wraps the inner expression in parentheses to preserve grouping.
     *
     * <p>Example: {@code (a + b)} -> {@code (a + b)}
     *
     * @param ctx the nested value expression context
     * @return the formatted parenthesized expression
     */
    @Override
    public String visitNestedValueExpr(OpenSearchPPLParser.NestedValueExprContext ctx) {
        if (ctx == null || ctx.logicalExpression() == null) {
            return wrapInParens(EMPTY);
        }
        return wrapInParens(safeVisit(ctx.logicalExpression()));
    }

    // ========== Search Expressions ==========

    /**
     * Visits a search field comparison expression.
     *
     * <p>These are field comparisons used within the search command.
     *
     * <p>Example: {@code status=200} -> {@code status = 200}
     *
     * @param ctx the search field compare context
     * @return the formatted search comparison
     */
    @Override
    public String visitSearchFieldCompare(OpenSearchPPLParser.SearchFieldCompareContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        String field = safeVisit(ctx.fieldExpression());
        String op = formatSearchOperator(ctx.searchComparisonOperator());
        String literal = safeVisit(ctx.searchLiteral());
        return formatBinaryExpr(field, op, literal);
    }

    /**
     * Formats a search comparison operator.
     *
     * <p>Search operators are normalized to lowercase.
     *
     * @param ctx the search comparison operator context
     * @return the formatted operator
     */
    private String formatSearchOperator(OpenSearchPPLParser.SearchComparisonOperatorContext ctx) {
        if (ctx == null) {
            return OP_EQUAL;
        }
        return normalizeKeyword(getOriginalText(ctx));
    }
}
