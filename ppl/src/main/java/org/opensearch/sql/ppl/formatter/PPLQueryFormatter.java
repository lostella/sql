/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

import static org.opensearch.sql.ppl.formatter.FormatterConstants.*;
import static org.opensearch.sql.ppl.formatter.FormatterUtils.*;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.opensearch.sql.common.antlr.CaseInsensitiveCharStream;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLLexer;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLParser;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLParserBaseVisitor;

/**
 * Formats PPL queries with consistent style: lowercase keywords, proper spacing around operators,
 * normalized pipe separators, and preserved identifier case.
 */
public class PPLQueryFormatter {

    public String format(String query) {
        if (query == null || query.trim().isEmpty()) {
            return EMPTY;
        }
        try {
            OpenSearchPPLLexer lexer = new OpenSearchPPLLexer(new CaseInsensitiveCharStream(query));
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            OpenSearchPPLParser parser = new OpenSearchPPLParser(tokenStream);

            FormatterErrorListener errorListener = new FormatterErrorListener();
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);

            ParseTree tree = parser.root();
            if (errorListener.hasErrors()) {
                return query;
            }

            String result = new FormattingVisitor(tokenStream).visit(tree);
            return result != null ? result.trim() : query;
        } catch (Exception e) {
            return query;
        }
    }

    private static class FormattingVisitor extends OpenSearchPPLParserBaseVisitor<String> {

        private final CommonTokenStream tokenStream;

        FormattingVisitor(CommonTokenStream tokenStream) {
            this.tokenStream = tokenStream;
        }

        // ========== Root and Statement Structure ==========

        @Override
        public String visitRoot(OpenSearchPPLParser.RootContext ctx) {
            return ctx == null ? EMPTY : safeVisit(ctx.pplStatement());
        }

        @Override
        public String visitPplStatement(OpenSearchPPLParser.PplStatementContext ctx) {
            return visitChildren(ctx);
        }

        @Override
        public String visitQueryStatement(OpenSearchPPLParser.QueryStatementContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder(safeVisit(ctx.pplCommands()));
            for (OpenSearchPPLParser.CommandsContext cmd : ctx.commands()) {
                sb.append(PIPE_SEPARATOR).append(safeVisit(cmd));
            }
            return sb.toString();
        }

        @Override
        public String visitCommands(OpenSearchPPLParser.CommandsContext ctx) {
            return visitChildren(ctx);
        }

        // ========== Search and Source Commands ==========

        @Override
        public String visitSearchFrom(OpenSearchPPLParser.SearchFromContext ctx) {
            if (ctx == null) return EMPTY;

            StringBuilder sb = new StringBuilder();
            List<String> beforeExpressions = new ArrayList<>();
            List<String> afterExpressions = new ArrayList<>();
            boolean foundFromClause = false;

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (child instanceof OpenSearchPPLParser.FromClauseContext) {
                    foundFromClause = true;
                } else if (child instanceof OpenSearchPPLParser.SearchExpressionContext) {
                    String expr = safeVisit(child);
                    if (isNotEmpty(expr)) {
                        (foundFromClause ? afterExpressions : beforeExpressions).add(expr);
                    }
                }
            }

            sb.append(safeVisit(ctx.fromClause()));
            for (String expr : beforeExpressions) sb.append(SPACE).append(expr);
            for (String expr : afterExpressions) sb.append(SPACE).append(expr);
            return sb.toString();
        }

        @Override
        public String visitFromClause(OpenSearchPPLParser.FromClauseContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder();
            if (ctx.SOURCE() != null) sb.append(KEYWORD_SOURCE);
            else if (ctx.INDEX() != null) sb.append(KEYWORD_INDEX);

            if (ctx.tableOrSubqueryClause() != null) sb.append(
                safeVisit(ctx.tableOrSubqueryClause())
            );
            else if (ctx.tableFunction() != null) sb.append(safeVisit(ctx.tableFunction()));
            else if (ctx.dynamicSourceClause() != null) sb.append(
                safeVisit(ctx.dynamicSourceClause())
            );
            return sb.toString();
        }

        @Override
        public String visitTableOrSubqueryClause(
            OpenSearchPPLParser.TableOrSubqueryClauseContext ctx
        ) {
            if (ctx == null) return EMPTY;
            return ctx.tableSourceClause() != null
                ? safeVisit(ctx.tableSourceClause())
                : visitChildren(ctx);
        }

        @Override
        public String visitTableSourceClause(OpenSearchPPLParser.TableSourceClauseContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder();
            List<OpenSearchPPLParser.TableSourceContext> sources = ctx.tableSource();
            for (int i = 0; i < sources.size(); i++) {
                if (i > 0) sb.append(COMMA_SEPARATOR);
                sb.append(safeVisit(sources.get(i)));
            }
            if (ctx.alias != null) {
                sb.append(SPACE).append(KEYWORD_AS).append(SPACE).append(safeVisit(ctx.alias));
            }
            return sb.toString();
        }

        @Override
        public String visitTableSource(OpenSearchPPLParser.TableSourceContext ctx) {
            if (ctx == null) return EMPTY;
            if (ctx.tableQualifiedName() != null) return safeVisit(ctx.tableQualifiedName());
            if (ctx.ID_DATE_SUFFIX() != null) return ctx.ID_DATE_SUFFIX().getText();
            return getOriginalText(ctx);
        }

        // ========== Commands ==========

        @Override
        public String visitDescribeCommand(OpenSearchPPLParser.DescribeCommandContext ctx) {
            return ctx == null
                ? EMPTY
                : KEYWORD_DESCRIBE + SPACE + safeVisit(ctx.tableSourceClause());
        }

        @Override
        public String visitFieldsCommand(OpenSearchPPLParser.FieldsCommandContext ctx) {
            return ctx == null
                ? EMPTY
                : KEYWORD_FIELDS + SPACE + safeVisit(ctx.fieldsCommandBody());
        }

        @Override
        public String visitFieldsCommandBody(OpenSearchPPLParser.FieldsCommandBodyContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder();
            if (ctx.PLUS() != null) sb.append(FIELD_INCLUDE);
            else if (ctx.MINUS() != null) sb.append(FIELD_EXCLUDE);
            sb.append(safeVisit(ctx.wcFieldList()));
            return sb.toString();
        }

        @Override
        public String visitWcFieldList(OpenSearchPPLParser.WcFieldListContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder();
            List<OpenSearchPPLParser.SelectFieldExpressionContext> fields =
                ctx.selectFieldExpression();
            List<TerminalNode> commas = ctx.COMMA();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    sb.append(commas != null && i - 1 < commas.size() ? COMMA_SEPARATOR : SPACE);
                }
                sb.append(safeVisit(fields.get(i)));
            }
            return sb.toString();
        }

        @Override
        public String visitSelectFieldExpression(
            OpenSearchPPLParser.SelectFieldExpressionContext ctx
        ) {
            if (ctx == null) return EMPTY;
            return ctx.STAR() != null ? OP_MULTIPLY : safeVisit(ctx.wcQualifiedName());
        }

        @Override
        public String visitWhereCommand(OpenSearchPPLParser.WhereCommandContext ctx) {
            return ctx == null ? EMPTY : KEYWORD_WHERE + SPACE + safeVisit(ctx.logicalExpression());
        }

        @Override
        public String visitStatsCommand(OpenSearchPPLParser.StatsCommandContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder(KEYWORD_STATS);

            if (ctx.statsArgs() != null) {
                String args = safeVisit(ctx.statsArgs());
                if (isNotEmpty(args)) sb.append(SPACE).append(args);
            }

            List<OpenSearchPPLParser.StatsAggTermContext> aggTerms = ctx.statsAggTerm();
            for (int i = 0; i < aggTerms.size(); i++) {
                sb.append(i == 0 ? SPACE : COMMA_SEPARATOR).append(safeVisit(aggTerms.get(i)));
            }

            if (ctx.statsByClause() != null) {
                sb.append(SPACE).append(safeVisit(ctx.statsByClause()));
            }
            return sb.toString();
        }

        @Override
        public String visitStatsArgs(OpenSearchPPLParser.StatsArgsContext ctx) {
            if (ctx == null) return EMPTY;
            List<String> args = new ArrayList<>();
            if (ctx.partitionsArg() != null) ctx
                .partitionsArg()
                .forEach(a -> args.add(safeVisit(a)));
            if (ctx.allnumArg() != null) ctx.allnumArg().forEach(a -> args.add(safeVisit(a)));
            if (ctx.delimArg() != null) ctx.delimArg().forEach(a -> args.add(safeVisit(a)));
            if (ctx.bucketNullableArg() != null) ctx
                .bucketNullableArg()
                .forEach(a -> args.add(safeVisit(a)));
            return joinWithSpace(args);
        }

        @Override
        public String visitPartitionsArg(OpenSearchPPLParser.PartitionsArgContext ctx) {
            return ctx == null ? EMPTY : KEYWORD_PARTITIONS + OP_EQUAL + safeVisit(ctx.partitions);
        }

        @Override
        public String visitAllnumArg(OpenSearchPPLParser.AllnumArgContext ctx) {
            return ctx == null ? EMPTY : KEYWORD_ALLNUM + OP_EQUAL + safeVisit(ctx.allnum);
        }

        @Override
        public String visitDelimArg(OpenSearchPPLParser.DelimArgContext ctx) {
            return ctx == null ? EMPTY : KEYWORD_DELIM + OP_EQUAL + safeVisit(ctx.delim);
        }

        @Override
        public String visitBucketNullableArg(OpenSearchPPLParser.BucketNullableArgContext ctx) {
            return ctx == null
                ? EMPTY
                : KEYWORD_BUCKET_NULLABLE + OP_EQUAL + safeVisit(ctx.bucket_nullable);
        }

        @Override
        public String visitStatsAggTerm(OpenSearchPPLParser.StatsAggTermContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder(safeVisit(ctx.statsFunction()));
            if (ctx.alias != null) {
                sb.append(SPACE).append(KEYWORD_AS).append(SPACE).append(safeVisit(ctx.alias));
            }
            return sb.toString();
        }

        @Override
        public String visitEventstatsCommand(OpenSearchPPLParser.EventstatsCommandContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder(KEYWORD_EVENTSTATS);
            List<OpenSearchPPLParser.EventstatsAggTermContext> aggTerms = ctx.eventstatsAggTerm();
            for (int i = 0; i < aggTerms.size(); i++) {
                sb.append(i == 0 ? SPACE : COMMA_SEPARATOR).append(safeVisit(aggTerms.get(i)));
            }
            if (ctx.statsByClause() != null) {
                sb.append(SPACE).append(safeVisit(ctx.statsByClause()));
            }
            return sb.toString();
        }

        @Override
        public String visitEventstatsAggTerm(OpenSearchPPLParser.EventstatsAggTermContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder(safeVisit(ctx.windowFunction()));
            if (ctx.alias != null) {
                sb.append(SPACE).append(KEYWORD_AS).append(SPACE).append(safeVisit(ctx.alias));
            }
            return sb.toString();
        }

        @Override
        public String visitWindowFunction(OpenSearchPPLParser.WindowFunctionContext ctx) {
            if (ctx == null) return EMPTY;
            String args = ctx.functionArgs() != null ? safeVisit(ctx.functionArgs()) : EMPTY;
            return safeVisit(ctx.windowFunctionName()) + LPAREN + args + RPAREN;
        }

        @Override
        public String visitWindowFunctionName(OpenSearchPPLParser.WindowFunctionNameContext ctx) {
            if (ctx == null) return EMPTY;
            if (ctx.statsFunctionName() != null) return safeVisit(ctx.statsFunctionName());
            if (ctx.scalarWindowFunctionName() != null) return safeVisit(
                ctx.scalarWindowFunctionName()
            );
            return normalizeKeyword(getOriginalText(ctx));
        }

        @Override
        public String visitStatsByClause(OpenSearchPPLParser.StatsByClauseContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder(KEYWORD_BY);
            if (ctx.fieldList() != null) sb.append(SPACE).append(safeVisit(ctx.fieldList()));
            if (ctx.bySpanClause() != null) {
                sb.append(SPACE).append(safeVisit(ctx.bySpanClause()));
                if (ctx.fieldList() != null) sb
                    .append(COMMA_SEPARATOR)
                    .append(safeVisit(ctx.fieldList()));
            }
            return sb.toString();
        }

        @Override
        public String visitEvalCommand(OpenSearchPPLParser.EvalCommandContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder(KEYWORD_EVAL);
            List<OpenSearchPPLParser.EvalClauseContext> clauses = ctx.evalClause();
            for (int i = 0; i < clauses.size(); i++) {
                sb.append(i == 0 ? SPACE : COMMA_SEPARATOR).append(safeVisit(clauses.get(i)));
            }
            return sb.toString();
        }

        @Override
        public String visitEvalClause(OpenSearchPPLParser.EvalClauseContext ctx) {
            if (ctx == null) return EMPTY;
            return formatAssignment(
                safeVisit(ctx.fieldExpression()),
                safeVisit(ctx.logicalExpression())
            );
        }

        @Override
        public String visitJoinCommand(OpenSearchPPLParser.JoinCommandContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder(KEYWORD_JOIN);
            if (ctx.joinCriteria() != null) sb.append(SPACE).append(safeVisit(ctx.joinCriteria()));
            if (ctx.right != null) sb.append(SPACE).append(safeVisit(ctx.right));
            return sb.toString();
        }

        @Override
        public String visitJoinCriteria(OpenSearchPPLParser.JoinCriteriaContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder();
            if (ctx.ON() != null) sb.append(KEYWORD_ON).append(SPACE);
            else if (ctx.WHERE() != null) sb.append(KEYWORD_WHERE).append(SPACE);
            sb.append(safeVisit(ctx.logicalExpression()));
            return sb.toString();
        }

        // ========== Logical Expressions ==========

        @Override
        public String visitLogicalNot(OpenSearchPPLParser.LogicalNotContext ctx) {
            return ctx == null ? EMPTY : KEYWORD_NOT + SPACE + safeVisit(ctx.logicalExpression());
        }

        @Override
        public String visitLogicalAnd(OpenSearchPPLParser.LogicalAndContext ctx) {
            return ctx == null
                ? EMPTY
                : formatBinaryExpr(safeVisit(ctx.left), KEYWORD_AND, safeVisit(ctx.right));
        }

        @Override
        public String visitLogicalOr(OpenSearchPPLParser.LogicalOrContext ctx) {
            return ctx == null
                ? EMPTY
                : formatBinaryExpr(safeVisit(ctx.left), KEYWORD_OR, safeVisit(ctx.right));
        }

        @Override
        public String visitLogicalXor(OpenSearchPPLParser.LogicalXorContext ctx) {
            return ctx == null
                ? EMPTY
                : formatBinaryExpr(safeVisit(ctx.left), KEYWORD_XOR, safeVisit(ctx.right));
        }

        @Override
        public String visitLogicalExpr(OpenSearchPPLParser.LogicalExprContext ctx) {
            return ctx == null ? EMPTY : safeVisit(ctx.expression());
        }

        // ========== Comparison Expressions ==========

        @Override
        public String visitCompareExpr(OpenSearchPPLParser.CompareExprContext ctx) {
            if (ctx == null) return EMPTY;
            return formatBinaryExpr(
                safeVisit(ctx.left),
                formatComparisonOp(ctx.comparisonOperator()),
                safeVisit(ctx.right)
            );
        }

        private String formatComparisonOp(OpenSearchPPLParser.ComparisonOperatorContext ctx) {
            if (ctx == null) return OP_EQUAL;
            if (ctx.EQUAL() != null || ctx.DOUBLE_EQUAL() != null) return OP_EQUAL;
            if (ctx.NOT_EQUAL() != null) return OP_NOT_EQUAL;
            if (ctx.LESS() != null) return OP_LESS;
            if (ctx.GREATER() != null) return OP_GREATER;
            if (ctx.NOT_LESS() != null) return OP_GREATER_EQUAL;
            if (ctx.NOT_GREATER() != null) return OP_LESS_EQUAL;
            return ctx.getText();
        }

        @Override
        public String visitInExpr(OpenSearchPPLParser.InExprContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder(safeVisit(ctx.expression()));
            if (ctx.NOT() != null) sb.append(SPACE).append(KEYWORD_NOT);
            sb.append(SPACE).append(KEYWORD_IN).append(SPACE).append(safeVisit(ctx.valueList()));
            return sb.toString();
        }

        // ========== Arithmetic Expressions ==========

        @Override
        public String visitBinaryArithmetic(OpenSearchPPLParser.BinaryArithmeticContext ctx) {
            if (ctx == null) return EMPTY;
            String op = ctx.binaryOperator != null ? ctx.binaryOperator.getText() : EMPTY;
            return formatBinaryExpr(
                safeVisit(ctx.valueExpression(0)),
                op,
                safeVisit(ctx.valueExpression(1))
            );
        }

        @Override
        public String visitValueExpr(OpenSearchPPLParser.ValueExprContext ctx) {
            return ctx == null ? EMPTY : safeVisit(ctx.valueExpression());
        }

        @Override
        public String visitLiteralValueExpr(OpenSearchPPLParser.LiteralValueExprContext ctx) {
            return ctx == null ? EMPTY : safeVisit(ctx.literalValue());
        }

        @Override
        public String visitFieldExpr(OpenSearchPPLParser.FieldExprContext ctx) {
            return ctx == null
                ? EMPTY
                : formatArithmeticInFieldName(safeVisit(ctx.fieldExpression()));
        }

        @Override
        public String visitNestedValueExpr(OpenSearchPPLParser.NestedValueExprContext ctx) {
            return ctx == null ? EMPTY : wrapInParens(safeVisit(ctx.logicalExpression()));
        }

        // ========== Function Calls ==========

        @Override
        public String visitFunctionCallExpr(OpenSearchPPLParser.FunctionCallExprContext ctx) {
            return ctx == null ? EMPTY : safeVisit(ctx.functionCall());
        }

        @Override
        public String visitEvalFunctionCall(OpenSearchPPLParser.EvalFunctionCallContext ctx) {
            if (ctx == null) return EMPTY;
            String args = ctx.functionArgs() != null ? safeVisit(ctx.functionArgs()) : EMPTY;
            return formatFunctionCall(safeVisit(ctx.evalFunctionName()), args);
        }

        @Override
        public String visitEvalFunctionName(OpenSearchPPLParser.EvalFunctionNameContext ctx) {
            return ctx == null ? EMPTY : normalizeKeyword(getOriginalText(ctx));
        }

        @Override
        public String visitStatsFunctionCall(OpenSearchPPLParser.StatsFunctionCallContext ctx) {
            if (ctx == null) return EMPTY;
            String args = ctx.functionArgs() != null ? safeVisit(ctx.functionArgs()) : EMPTY;
            return formatFunctionCall(safeVisit(ctx.statsFunctionName()), args);
        }

        @Override
        public String visitStatsFunctionName(OpenSearchPPLParser.StatsFunctionNameContext ctx) {
            return ctx == null ? EMPTY : normalizeKeyword(getOriginalText(ctx));
        }

        @Override
        public String visitCountAllFunctionCall(
            OpenSearchPPLParser.CountAllFunctionCallContext ctx
        ) {
            return FUNC_COUNT + "()";
        }

        @Override
        public String visitCountEvalFunctionCall(
            OpenSearchPPLParser.CountEvalFunctionCallContext ctx
        ) {
            if (ctx == null || ctx.evalExpression() == null) return FUNC_COUNT + "()";
            return FUNC_COUNT + "(" + safeVisit(ctx.evalExpression()) + ")";
        }

        @Override
        public String visitDistinctCountFunctionCall(
            OpenSearchPPLParser.DistinctCountFunctionCallContext ctx
        ) {
            if (ctx == null) return EMPTY;
            String funcName =
                ctx.DISTINCT_COUNT() != null
                    ? FUNC_DISTINCT_COUNT
                    : ctx.DC() != null
                        ? FUNC_DC
                        : FUNC_DISTINCT_COUNT_APPROX;
            return funcName + "(" + safeVisit(ctx.valueExpression()) + ")";
        }

        @Override
        public String visitFunctionArgs(OpenSearchPPLParser.FunctionArgsContext ctx) {
            if (ctx == null) return EMPTY;
            List<OpenSearchPPLParser.FunctionArgContext> args = ctx.functionArg();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(COMMA_SEPARATOR);
                sb.append(safeVisit(args.get(i)));
            }
            return sb.toString();
        }

        @Override
        public String visitFunctionArg(OpenSearchPPLParser.FunctionArgContext ctx) {
            return ctx == null ? EMPTY : safeVisit(ctx.functionArgExpression());
        }

        @Override
        public String visitFunctionArgExpression(
            OpenSearchPPLParser.FunctionArgExpressionContext ctx
        ) {
            if (ctx == null) return EMPTY;
            if (ctx.logicalExpression() != null) return safeVisit(ctx.logicalExpression());
            if (ctx.lambda() != null) return safeVisit(ctx.lambda());
            return visitChildren(ctx);
        }

        // ========== Field Expressions ==========

        @Override
        public String visitFieldExpression(OpenSearchPPLParser.FieldExpressionContext ctx) {
            return ctx == null ? EMPTY : safeVisit(ctx.qualifiedName());
        }

        @Override
        public String visitWcFieldExpression(OpenSearchPPLParser.WcFieldExpressionContext ctx) {
            return ctx == null ? EMPTY : safeVisit(ctx.wcQualifiedName());
        }

        @Override
        public String visitFieldList(OpenSearchPPLParser.FieldListContext ctx) {
            if (ctx == null) return EMPTY;
            List<OpenSearchPPLParser.FieldExpressionContext> fields = ctx.fieldExpression();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) sb.append(COMMA_SEPARATOR);
                sb.append(safeVisit(fields.get(i)));
            }
            return sb.toString();
        }

        // ========== Qualified Names ==========

        @Override
        public String visitIdentsAsQualifiedName(
            OpenSearchPPLParser.IdentsAsQualifiedNameContext ctx
        ) {
            if (ctx == null) return EMPTY;
            List<OpenSearchPPLParser.IdentContext> idents = ctx.ident();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < idents.size(); i++) {
                if (i > 0) sb.append(DOT_SEPARATOR);
                sb.append(safeVisit(idents.get(i)));
            }
            return sb.toString();
        }

        @Override
        public String visitIdentsAsWildcardQualifiedName(
            OpenSearchPPLParser.IdentsAsWildcardQualifiedNameContext ctx
        ) {
            if (ctx == null) return EMPTY;
            List<OpenSearchPPLParser.WildcardContext> wildcards = ctx.wildcard();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < wildcards.size(); i++) {
                if (i > 0) sb.append(DOT_SEPARATOR);
                sb.append(safeVisit(wildcards.get(i)));
            }
            return sb.toString();
        }

        @Override
        public String visitIdentsAsTableQualifiedName(
            OpenSearchPPLParser.IdentsAsTableQualifiedNameContext ctx
        ) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder();
            if (ctx.tableIdent() != null) sb.append(safeVisit(ctx.tableIdent()));
            for (OpenSearchPPLParser.IdentContext ident : ctx.ident()) {
                sb.append(DOT_SEPARATOR).append(safeVisit(ident));
            }
            return sb.toString();
        }

        @Override
        public String visitTableIdent(OpenSearchPPLParser.TableIdentContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder();
            if (ctx.CLUSTER() != null) sb.append(ctx.CLUSTER().getText());
            sb.append(safeVisit(ctx.ident()));
            return sb.toString();
        }

        @Override
        public String visitWildcard(OpenSearchPPLParser.WildcardContext ctx) {
            if (ctx == null) return EMPTY;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                sb.append(child instanceof TerminalNode ? child.getText() : safeVisit(child));
            }
            return sb.toString();
        }

        @Override
        public String visitIdent(OpenSearchPPLParser.IdentContext ctx) {
            return ctx == null ? EMPTY : preserveIdentifier(getOriginalText(ctx));
        }

        // ========== Literals ==========

        @Override
        public String visitLiteralValue(OpenSearchPPLParser.LiteralValueContext ctx) {
            return ctx == null ? EMPTY : getOriginalText(ctx);
        }

        @Override
        public String visitIntegerLiteral(OpenSearchPPLParser.IntegerLiteralContext ctx) {
            return ctx == null ? EMPTY : ctx.getText();
        }

        @Override
        public String visitDecimalLiteral(OpenSearchPPLParser.DecimalLiteralContext ctx) {
            return ctx == null ? EMPTY : ctx.getText();
        }

        @Override
        public String visitBooleanLiteral(OpenSearchPPLParser.BooleanLiteralContext ctx) {
            return ctx == null ? EMPTY : normalizeKeyword(ctx.getText());
        }

        @Override
        public String visitStringLiteral(OpenSearchPPLParser.StringLiteralContext ctx) {
            return ctx == null ? EMPTY : getOriginalText(ctx);
        }

        // ========== Search Expressions ==========

        @Override
        public String visitSearchFieldCompare(OpenSearchPPLParser.SearchFieldCompareContext ctx) {
            if (ctx == null) return EMPTY;
            String op =
                ctx.searchComparisonOperator() != null
                    ? normalizeKeyword(getOriginalText(ctx.searchComparisonOperator()))
                    : OP_EQUAL;
            return formatBinaryExpr(
                safeVisit(ctx.fieldExpression()),
                op,
                safeVisit(ctx.searchLiteral())
            );
        }

        @Override
        public String visitSearchLiteral(OpenSearchPPLParser.SearchLiteralContext ctx) {
            return ctx == null ? EMPTY : getOriginalText(ctx);
        }

        @Override
        public String visitValueList(OpenSearchPPLParser.ValueListContext ctx) {
            if (ctx == null) return wrapInParens(EMPTY);
            List<OpenSearchPPLParser.LiteralValueContext> values = ctx.literalValue();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) sb.append(COMMA_SEPARATOR);
                sb.append(safeVisit(values.get(i)));
            }
            return wrapInParens(sb.toString());
        }

        // ========== Helper Methods ==========

        private String safeVisit(ParseTree node) {
            if (node == null) return EMPTY;
            String result = visit(node);
            return result != null ? result : EMPTY;
        }

        private String getOriginalText(ParserRuleContext ctx) {
            return FormatterUtils.getOriginalText(ctx, tokenStream);
        }

        @Override
        protected String defaultResult() {
            return EMPTY;
        }

        @Override
        protected String aggregateResult(String aggregate, String nextResult) {
            if (isEmpty(aggregate)) return nullToEmpty(nextResult);
            if (isEmpty(nextResult)) return aggregate;
            return aggregate + nextResult;
        }
    }
}
