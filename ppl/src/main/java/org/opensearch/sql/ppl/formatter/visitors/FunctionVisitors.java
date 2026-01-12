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
 * Visitor methods for formatting function calls in PPL queries.
 *
 * <p>This class handles the formatting of various function types including:
 * <ul>
 *   <li>Evaluation functions (e.g., abs, ceil, concat)</li>
 *   <li>Statistical functions (e.g., avg, sum, count, max, min)</li>
 *   <li>Count variations (count(), count(field), distinct_count)</li>
 *   <li>Window functions (for eventstats)</li>
 *   <li>Function arguments and argument expressions</li>
 * </ul>
 *
 * <p>Function names are normalized to lowercase for consistency. Arguments are
 * formatted with proper comma separation and spacing.
 */
public class FunctionVisitors extends BaseFormattingVisitor {

    /**
     * Creates a new function visitor.
     *
     * @param tokenStream the token stream from the lexer
     */
    public FunctionVisitors(CommonTokenStream tokenStream) {
        super(tokenStream);
    }

    // ========== General Function Calls ==========

    /**
     * Visits a function call expression wrapper.
     *
     * <p>This is a pass-through that delegates to the contained function call.
     *
     * @param ctx the function call expression context
     * @return the formatted function call
     */
    @Override
    public String visitFunctionCallExpr(OpenSearchPPLParser.FunctionCallExprContext ctx) {
        if (ctx == null || ctx.functionCall() == null) {
            return EMPTY;
        }
        return safeVisit(ctx.functionCall());
    }

    // ========== Eval Functions ==========

    /**
     * Visits an evaluation function call.
     *
     * <p>Eval functions are general-purpose functions used in eval commands and expressions.
     * The function name is normalized to lowercase, and arguments are comma-separated.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code ABS(value)} -> {@code abs(value)}</li>
     *   <li>{@code CONCAT(first, last)} -> {@code concat(first, last)}</li>
     *   <li>{@code IF(a > b, a, b)} -> {@code if(a > b, a, b)}</li>
     * </ul>
     *
     * @param ctx the eval function call context
     * @return the formatted function call
     */
    @Override
    public String visitEvalFunctionCall(OpenSearchPPLParser.EvalFunctionCallContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        String funcName = safeVisit(ctx.evalFunctionName());
        String args = ctx.functionArgs() != null ? safeVisit(ctx.functionArgs()) : EMPTY;
        return formatFunctionCall(funcName, args);
    }

    /**
     * Visits an eval function name.
     *
     * <p>Function names are normalized to lowercase for consistency.
     *
     * @param ctx the eval function name context
     * @return the lowercase function name
     */
    @Override
    public String visitEvalFunctionName(OpenSearchPPLParser.EvalFunctionNameContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return normalizeKeyword(getOriginalText(ctx));
    }

    // ========== Stats Functions ==========

    /**
     * Visits a statistical function call.
     *
     * <p>Stats functions are aggregate functions used in stats commands.
     * The function name is normalized to lowercase.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code AVG(response_time)} -> {@code avg(response_time)}</li>
     *   <li>{@code SUM(bytes)} -> {@code sum(bytes)}</li>
     *   <li>{@code MAX(latency)} -> {@code max(latency)}</li>
     * </ul>
     *
     * @param ctx the stats function call context
     * @return the formatted function call
     */
    @Override
    public String visitStatsFunctionCall(OpenSearchPPLParser.StatsFunctionCallContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        String funcName = safeVisit(ctx.statsFunctionName());
        String args = ctx.functionArgs() != null ? safeVisit(ctx.functionArgs()) : EMPTY;
        return formatFunctionCall(funcName, args);
    }

    /**
     * Visits a stats function name.
     *
     * <p>Function names are normalized to lowercase for consistency.
     *
     * @param ctx the stats function name context
     * @return the lowercase function name
     */
    @Override
    public String visitStatsFunctionName(OpenSearchPPLParser.StatsFunctionNameContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return normalizeKeyword(getOriginalText(ctx));
    }

    // ========== Count Function Variations ==========

    /**
     * Visits a count() function call with no arguments.
     *
     * <p>This represents the count of all records: {@code COUNT()} -> {@code count()}
     *
     * @param ctx the count all function call context
     * @return the formatted count() call
     */
    @Override
    public String visitCountAllFunctionCall(OpenSearchPPLParser.CountAllFunctionCallContext ctx) {
        return String.format(FUNC_NO_ARGS_FORMAT, FUNC_COUNT);
    }

    /**
     * Visits a count() function call with an eval expression argument.
     *
     * <p>Example: {@code COUNT(status)} -> {@code count(status)}
     *
     * @param ctx the count eval function call context
     * @return the formatted count call with argument
     */
    @Override
    public String visitCountEvalFunctionCall(OpenSearchPPLParser.CountEvalFunctionCallContext ctx) {
        if (ctx == null || ctx.evalExpression() == null) {
            return String.format(FUNC_NO_ARGS_FORMAT, FUNC_COUNT);
        }
        String arg = safeVisit(ctx.evalExpression());
        return String.format(FUNC_WITH_ARGS_FORMAT, FUNC_COUNT, arg);
    }

    /**
     * Visits a distinct count function call.
     *
     * <p>Handles the various forms of distinct count:
     * <ul>
     *   <li>{@code DISTINCT_COUNT(field)} -> {@code distinct_count(field)}</li>
     *   <li>{@code DC(field)} -> {@code dc(field)}</li>
     *   <li>{@code DISTINCT_COUNT_APPROX(field)} -> {@code distinct_count_approx(field)}</li>
     * </ul>
     *
     * @param ctx the distinct count function call context
     * @return the formatted distinct count call
     */
    @Override
    public String visitDistinctCountFunctionCall(
        OpenSearchPPLParser.DistinctCountFunctionCallContext ctx
    ) {
        if (ctx == null) {
            return EMPTY;
        }

        // Determine which distinct count variant was used
        String funcName;
        if (ctx.DISTINCT_COUNT() != null) {
            funcName = FUNC_DISTINCT_COUNT;
        } else if (ctx.DC() != null) {
            funcName = FUNC_DC;
        } else {
            funcName = FUNC_DISTINCT_COUNT_APPROX;
        }

        String arg = safeVisit(ctx.valueExpression());
        return String.format(FUNC_WITH_ARGS_FORMAT, funcName, arg);
    }

    // ========== Window Functions ==========

    /**
     * Visits a window function (used in eventstats).
     *
     * <p>Window functions include statistical functions and scalar window functions
     * like ROW_NUMBER, RANK, etc.
     *
     * <p>Example: {@code ROW_NUMBER()} -> {@code row_number()}
     *
     * @param ctx the window function context
     * @return the formatted window function call
     */
    @Override
    public String visitWindowFunction(OpenSearchPPLParser.WindowFunctionContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        String funcName = safeVisit(ctx.windowFunctionName());
        String args = ctx.functionArgs() != null ? safeVisit(ctx.functionArgs()) : EMPTY;
        return formatFunctionCall(funcName, args);
    }

    /**
     * Visits a window function name.
     *
     * <p>Window function names include stats function names and scalar window function names.
     * All are normalized to lowercase.
     *
     * @param ctx the window function name context
     * @return the lowercase function name
     */
    @Override
    public String visitWindowFunctionName(OpenSearchPPLParser.WindowFunctionNameContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        if (ctx.statsFunctionName() != null) {
            return safeVisit(ctx.statsFunctionName());
        }
        if (ctx.scalarWindowFunctionName() != null) {
            return safeVisit(ctx.scalarWindowFunctionName());
        }
        // Fallback to lowercase original text
        return normalizeKeyword(getOriginalText(ctx));
    }

    /**
     * Visits a scalar window function name (ROW_NUMBER, RANK, DENSE_RANK, etc.).
     *
     * @param ctx the scalar window function name context
     * @return the lowercase function name
     */
    @Override
    public String visitScalarWindowFunctionName(
        OpenSearchPPLParser.ScalarWindowFunctionNameContext ctx
    ) {
        if (ctx == null) {
            return EMPTY;
        }
        return normalizeKeyword(getOriginalText(ctx));
    }

    // ========== Function Arguments ==========

    /**
     * Visits a function argument list.
     *
     * <p>Arguments are formatted as a comma-separated list with consistent spacing.
     *
     * <p>Example: {@code arg1,arg2,arg3} -> {@code arg1, arg2, arg3}
     *
     * @param ctx the function args context
     * @return the formatted comma-separated arguments
     */
    @Override
    public String visitFunctionArgs(OpenSearchPPLParser.FunctionArgsContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        List<OpenSearchPPLParser.FunctionArgContext> args = ctx.functionArg();
        if (args == null || args.isEmpty()) {
            return EMPTY;
        }
        return visitAndJoinWithComma(args);
    }

    /**
     * Visits a single function argument.
     *
     * <p>Delegates to the function argument expression visitor.
     *
     * @param ctx the function arg context
     * @return the formatted argument
     */
    @Override
    public String visitFunctionArg(OpenSearchPPLParser.FunctionArgContext ctx) {
        if (ctx == null || ctx.functionArgExpression() == null) {
            return EMPTY;
        }
        return safeVisit(ctx.functionArgExpression());
    }

    /**
     * Visits a function argument expression.
     *
     * <p>Function arguments can be:
     * <ul>
     *   <li>Logical expressions (e.g., field comparisons)</li>
     *   <li>Lambda expressions</li>
     *   <li>Other expression types</li>
     * </ul>
     *
     * @param ctx the function arg expression context
     * @return the formatted argument expression
     */
    @Override
    public String visitFunctionArgExpression(OpenSearchPPLParser.FunctionArgExpressionContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        if (ctx.logicalExpression() != null) {
            return safeVisit(ctx.logicalExpression());
        }
        if (ctx.lambda() != null) {
            return safeVisit(ctx.lambda());
        }
        // Fallback to visiting children
        return visitChildren(ctx);
    }

    // ========== Lambda Expressions ==========

    /**
     * Visits a lambda expression.
     *
     * <p>Lambda expressions are used in functions like transform, filter, etc.
     *
     * <p>Example: {@code x -> x * 2}
     *
     * @param ctx the lambda context
     * @return the formatted lambda expression
     */
    @Override
    public String visitLambda(OpenSearchPPLParser.LambdaContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        // Lambda format: identifier(s) -> expression
        StringBuilder sb = newBuilder();

        if (ctx.ident() != null && !ctx.ident().isEmpty()) {
            sb.append(visitAndJoinWithComma(ctx.ident()));
        }
        sb.append(" -> ");
        sb.append(safeVisit(ctx.logicalExpression()));

        return sb.toString();
    }
}
