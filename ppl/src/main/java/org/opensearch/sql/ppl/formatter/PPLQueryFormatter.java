/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

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

/** PPL Query Formatter that formats PPL queries with consistent style using ANTLR parse tree. */
public class PPLQueryFormatter {

    /** Format a PPL query string. */
    public String format(String query) {
        OpenSearchPPLLexer lexer = new OpenSearchPPLLexer(new CaseInsensitiveCharStream(query));
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        OpenSearchPPLParser parser = new OpenSearchPPLParser(tokenStream);

        ParseTree tree = parser.root();
        FormattingVisitor visitor = new FormattingVisitor(tokenStream);
        return visitor.visit(tree).trim();
    }

    /** Visitor that walks the parse tree and formats the output. */
    private static class FormattingVisitor extends OpenSearchPPLParserBaseVisitor<String> {

        private final CommonTokenStream tokenStream;

        FormattingVisitor(CommonTokenStream tokenStream) {
            this.tokenStream = tokenStream;
        }

        @Override
        public String visitRoot(OpenSearchPPLParser.RootContext ctx) {
            if (ctx.pplStatement() != null) {
                return visit(ctx.pplStatement());
            }
            return "";
        }

        @Override
        public String visitPplStatement(OpenSearchPPLParser.PplStatementContext ctx) {
            return visitChildren(ctx);
        }

        @Override
        public String visitQueryStatement(OpenSearchPPLParser.QueryStatementContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append(visit(ctx.pplCommands()));
            for (OpenSearchPPLParser.CommandsContext cmd : ctx.commands()) {
                sb.append(" | ").append(visit(cmd));
            }
            return sb.toString();
        }

        @Override
        public String visitSearchFrom(OpenSearchPPLParser.SearchFromContext ctx) {
            StringBuilder sb = new StringBuilder();

            // Build search expressions before fromClause
            List<String> beforeExpressions = new ArrayList<>();
            List<String> afterExpressions = new ArrayList<>();

            boolean foundFromClause = false;
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (child instanceof OpenSearchPPLParser.FromClauseContext) {
                    foundFromClause = true;
                } else if (child instanceof OpenSearchPPLParser.SearchExpressionContext) {
                    String expr = visit(child);
                    if (foundFromClause) {
                        afterExpressions.add(expr);
                    } else {
                        beforeExpressions.add(expr);
                    }
                }
            }

            // Format: source=... [search expressions]
            sb.append(visit(ctx.fromClause()));

            for (String expr : beforeExpressions) {
                sb.append(" ").append(expr);
            }
            for (String expr : afterExpressions) {
                sb.append(" ").append(expr);
            }

            return sb.toString();
        }

        @Override
        public String visitFromClause(OpenSearchPPLParser.FromClauseContext ctx) {
            // Handle source= or index=
            StringBuilder sb = new StringBuilder();
            if (ctx.SOURCE() != null) {
                sb.append("source=");
            } else if (ctx.INDEX() != null) {
                sb.append("index=");
            }

            if (ctx.tableOrSubqueryClause() != null) {
                sb.append(visit(ctx.tableOrSubqueryClause()));
            } else if (ctx.tableFunction() != null) {
                sb.append(visit(ctx.tableFunction()));
            } else if (ctx.dynamicSourceClause() != null) {
                sb.append(visit(ctx.dynamicSourceClause()));
            }

            return sb.toString();
        }

        @Override
        public String visitTableOrSubqueryClause(
            OpenSearchPPLParser.TableOrSubqueryClauseContext ctx
        ) {
            if (ctx.tableSourceClause() != null) {
                return visit(ctx.tableSourceClause());
            }
            return visitChildren(ctx);
        }

        @Override
        public String visitTableSourceClause(OpenSearchPPLParser.TableSourceClauseContext ctx) {
            StringBuilder sb = new StringBuilder();
            List<OpenSearchPPLParser.TableSourceContext> sources = ctx.tableSource();
            for (int i = 0; i < sources.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(visit(sources.get(i)));
            }
            if (ctx.alias != null) {
                sb.append(" as ").append(visit(ctx.alias));
            }
            return sb.toString();
        }

        @Override
        public String visitTableSource(OpenSearchPPLParser.TableSourceContext ctx) {
            if (ctx.tableQualifiedName() != null) {
                return visit(ctx.tableQualifiedName());
            }
            if (ctx.ID_DATE_SUFFIX() != null) {
                return ctx.ID_DATE_SUFFIX().getText();
            }
            return getOriginalText(ctx);
        }

        @Override
        public String visitDescribeCommand(OpenSearchPPLParser.DescribeCommandContext ctx) {
            return "describe " + visit(ctx.tableSourceClause());
        }

        @Override
        public String visitFieldsCommand(OpenSearchPPLParser.FieldsCommandContext ctx) {
            return "fields " + visit(ctx.fieldsCommandBody());
        }

        @Override
        public String visitFieldsCommandBody(OpenSearchPPLParser.FieldsCommandBodyContext ctx) {
            StringBuilder sb = new StringBuilder();
            if (ctx.PLUS() != null) {
                sb.append("+ ");
            } else if (ctx.MINUS() != null) {
                sb.append("- ");
            }
            sb.append(visit(ctx.wcFieldList()));
            return sb.toString();
        }

        @Override
        public String visitWcFieldList(OpenSearchPPLParser.WcFieldListContext ctx) {
            StringBuilder sb = new StringBuilder();
            List<OpenSearchPPLParser.SelectFieldExpressionContext> fields =
                ctx.selectFieldExpression();
            // Check for COMMA tokens between fields
            List<TerminalNode> commas = ctx.COMMA();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    // Use comma if there was one in original, otherwise use space
                    if (commas != null && i - 1 < commas.size()) {
                        sb.append(", ");
                    } else {
                        sb.append(" ");
                    }
                }
                sb.append(visit(fields.get(i)));
            }
            return sb.toString();
        }

        @Override
        public String visitSelectFieldExpression(
            OpenSearchPPLParser.SelectFieldExpressionContext ctx
        ) {
            if (ctx.STAR() != null) {
                return "*";
            }
            String fieldName = visit(ctx.wcQualifiedName());
            // Handle prefix wildcards like "*name" - add space after leading *
            if (fieldName.startsWith("*") && fieldName.length() > 1 && fieldName.charAt(1) != ' ') {
                return "* " + fieldName.substring(1);
            }
            return fieldName;
        }

        @Override
        public String visitWhereCommand(OpenSearchPPLParser.WhereCommandContext ctx) {
            return "where " + visit(ctx.logicalExpression());
        }

        @Override
        public String visitStatsCommand(OpenSearchPPLParser.StatsCommandContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append("stats");

            // Stats args
            if (ctx.statsArgs() != null) {
                String args = visit(ctx.statsArgs());
                if (!args.isEmpty()) {
                    sb.append(" ").append(args);
                }
            }

            // Aggregation terms
            List<OpenSearchPPLParser.StatsAggTermContext> aggTerms = ctx.statsAggTerm();
            for (int i = 0; i < aggTerms.size(); i++) {
                if (i == 0) sb.append(" ");
                else sb.append(", ");
                sb.append(visit(aggTerms.get(i)));
            }

            // By clause
            if (ctx.statsByClause() != null) {
                sb.append(" ").append(visit(ctx.statsByClause()));
            }

            return sb.toString();
        }

        @Override
        public String visitStatsArgs(OpenSearchPPLParser.StatsArgsContext ctx) {
            return "";
        }

        @Override
        public String visitStatsAggTerm(OpenSearchPPLParser.StatsAggTermContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append(visit(ctx.statsFunction()));
            if (ctx.alias != null) {
                sb.append(" as ").append(visit(ctx.alias));
            }
            return sb.toString();
        }

        @Override
        public String visitEventstatsCommand(OpenSearchPPLParser.EventstatsCommandContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append("eventstats");

            // Aggregation terms
            List<OpenSearchPPLParser.EventstatsAggTermContext> aggTerms = ctx.eventstatsAggTerm();
            for (int i = 0; i < aggTerms.size(); i++) {
                if (i == 0) sb.append(" ");
                else sb.append(", ");
                sb.append(visit(aggTerms.get(i)));
            }

            // By clause
            if (ctx.statsByClause() != null) {
                sb.append(" ").append(visit(ctx.statsByClause()));
            }

            return sb.toString();
        }

        @Override
        public String visitEventstatsAggTerm(OpenSearchPPLParser.EventstatsAggTermContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append(visit(ctx.windowFunction()));
            if (ctx.alias != null) {
                sb.append(" as ").append(visit(ctx.alias));
            }
            return sb.toString();
        }

        @Override
        public String visitWindowFunction(OpenSearchPPLParser.WindowFunctionContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append(visit(ctx.windowFunctionName()));
            sb.append("(");
            if (ctx.functionArgs() != null) {
                sb.append(visit(ctx.functionArgs()));
            }
            sb.append(")");
            return sb.toString();
        }

        @Override
        public String visitWindowFunctionName(OpenSearchPPLParser.WindowFunctionNameContext ctx) {
            if (ctx.statsFunctionName() != null) {
                return visit(ctx.statsFunctionName());
            }
            if (ctx.scalarWindowFunctionName() != null) {
                return visit(ctx.scalarWindowFunctionName());
            }
            return getOriginalText(ctx).toLowerCase();
        }

        @Override
        public String visitStatsByClause(OpenSearchPPLParser.StatsByClauseContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append("by");

            if (ctx.fieldList() != null) {
                sb.append(" ").append(visit(ctx.fieldList()));
            }
            if (ctx.bySpanClause() != null) {
                sb.append(" ").append(visit(ctx.bySpanClause()));
                if (ctx.fieldList() != null) {
                    sb.append(", ").append(visit(ctx.fieldList()));
                }
            }

            return sb.toString();
        }

        @Override
        public String visitEvalCommand(OpenSearchPPLParser.EvalCommandContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append("eval");

            List<OpenSearchPPLParser.EvalClauseContext> clauses = ctx.evalClause();
            for (int i = 0; i < clauses.size(); i++) {
                if (i == 0) sb.append(" ");
                else sb.append(", ");
                sb.append(visit(clauses.get(i)));
            }

            return sb.toString();
        }

        @Override
        public String visitEvalClause(OpenSearchPPLParser.EvalClauseContext ctx) {
            String fieldExpr = visit(ctx.fieldExpression());
            String logicalExpr = visit(ctx.logicalExpression());
            return fieldExpr + " = " + logicalExpr;
        }

        @Override
        public String visitJoinCommand(OpenSearchPPLParser.JoinCommandContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append("join");

            if (ctx.joinCriteria() != null) {
                sb.append(" ").append(visit(ctx.joinCriteria()));
            }

            if (ctx.right != null) {
                sb.append(" ").append(visit(ctx.right));
            }

            return sb.toString();
        }

        @Override
        public String visitJoinCriteria(OpenSearchPPLParser.JoinCriteriaContext ctx) {
            StringBuilder sb = new StringBuilder();
            if (ctx.ON() != null) {
                sb.append("on ");
            } else if (ctx.WHERE() != null) {
                sb.append("where ");
            }
            sb.append(visit(ctx.logicalExpression()));
            return sb.toString();
        }

        // Logical expressions
        @Override
        public String visitLogicalNot(OpenSearchPPLParser.LogicalNotContext ctx) {
            return "not " + visit(ctx.logicalExpression());
        }

        @Override
        public String visitLogicalAnd(OpenSearchPPLParser.LogicalAndContext ctx) {
            return visit(ctx.left) + " and " + visit(ctx.right);
        }

        @Override
        public String visitLogicalOr(OpenSearchPPLParser.LogicalOrContext ctx) {
            return visit(ctx.left) + " or " + visit(ctx.right);
        }

        @Override
        public String visitLogicalXor(OpenSearchPPLParser.LogicalXorContext ctx) {
            return visit(ctx.left) + " xor " + visit(ctx.right);
        }

        @Override
        public String visitLogicalExpr(OpenSearchPPLParser.LogicalExprContext ctx) {
            return visit(ctx.expression());
        }

        // Comparison expressions
        @Override
        public String visitCompareExpr(OpenSearchPPLParser.CompareExprContext ctx) {
            String left = visit(ctx.left);
            String right = visit(ctx.right);
            String op = formatOperator(ctx.comparisonOperator());
            return left + " " + op + " " + right;
        }

        @Override
        public String visitInExpr(OpenSearchPPLParser.InExprContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append(visit(ctx.expression()));
            if (ctx.NOT() != null) {
                sb.append(" not");
            }
            sb.append(" in ");
            sb.append(visit(ctx.valueList()));
            return sb.toString();
        }

        // Value expressions
        @Override
        public String visitBinaryArithmetic(OpenSearchPPLParser.BinaryArithmeticContext ctx) {
            String left = visit(ctx.valueExpression(0));
            String right = visit(ctx.valueExpression(1));
            String op = ctx.binaryOperator.getText();
            return left + " " + op + " " + right;
        }

        @Override
        public String visitValueExpr(OpenSearchPPLParser.ValueExprContext ctx) {
            return visit(ctx.valueExpression());
        }

        @Override
        public String visitLiteralValueExpr(OpenSearchPPLParser.LiteralValueExprContext ctx) {
            return visit(ctx.literalValue());
        }

        @Override
        public String visitFieldExpr(OpenSearchPPLParser.FieldExprContext ctx) {
            String fieldName = visit(ctx.fieldExpression());
            // Apply heuristic to detect arithmetic patterns that were lexed as identifiers
            // Pattern: identifier followed by operator followed by number (e.g., age*2, bytes/1024)
            return formatArithmeticInFieldName(fieldName);
        }

        /**
         * Heuristic to detect and format arithmetic expressions that were incorrectly
         * lexed as single identifiers (e.g., "age*2" should become "age * 2").
         */
        private String formatArithmeticInFieldName(String fieldName) {
            // Pattern: word characters, then operator, then digits
            // Handles *, /, +, - operators between identifiers and numbers
            String result = fieldName;
            // Handle multiplication: word*number or number*word
            result = result.replaceAll("(?<=\\w)\\*(?=\\d)", " * ");
            result = result.replaceAll("(?<=\\d)\\*(?=\\w)", " * ");
            // Handle division: word/number
            result = result.replaceAll("(?<=\\w)/(?=\\d)", " / ");
            result = result.replaceAll("(?<=\\d)/(?=\\d)", " / ");
            // Handle addition: word+number
            result = result.replaceAll("(?<=\\w)\\+(?=\\d)", " + ");
            // Handle subtraction: word-number (but not hyphenated identifiers)
            result = result.replaceAll("(?<=\\w)-(?=\\d)", " - ");
            return result;
        }

        @Override
        public String visitNestedValueExpr(OpenSearchPPLParser.NestedValueExprContext ctx) {
            return "(" + visit(ctx.logicalExpression()) + ")";
        }

        // Function calls
        @Override
        public String visitFunctionCallExpr(OpenSearchPPLParser.FunctionCallExprContext ctx) {
            return visit(ctx.functionCall());
        }

        @Override
        public String visitEvalFunctionCall(OpenSearchPPLParser.EvalFunctionCallContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append(visit(ctx.evalFunctionName()));
            sb.append("(");
            if (ctx.functionArgs() != null) {
                sb.append(visit(ctx.functionArgs()));
            }
            sb.append(")");
            return sb.toString();
        }

        @Override
        public String visitEvalFunctionName(OpenSearchPPLParser.EvalFunctionNameContext ctx) {
            return getOriginalText(ctx).toLowerCase();
        }

        @Override
        public String visitStatsFunctionCall(OpenSearchPPLParser.StatsFunctionCallContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append(visit(ctx.statsFunctionName()));
            sb.append("(");
            if (ctx.functionArgs() != null) {
                sb.append(visit(ctx.functionArgs()));
            }
            sb.append(")");
            return sb.toString();
        }

        @Override
        public String visitStatsFunctionName(OpenSearchPPLParser.StatsFunctionNameContext ctx) {
            return getOriginalText(ctx).toLowerCase();
        }

        @Override
        public String visitCountAllFunctionCall(
            OpenSearchPPLParser.CountAllFunctionCallContext ctx
        ) {
            return "count()";
        }

        @Override
        public String visitCountEvalFunctionCall(
            OpenSearchPPLParser.CountEvalFunctionCallContext ctx
        ) {
            return "count(" + visit(ctx.evalExpression()) + ")";
        }

        @Override
        public String visitDistinctCountFunctionCall(
            OpenSearchPPLParser.DistinctCountFunctionCallContext ctx
        ) {
            String funcName =
                ctx.DISTINCT_COUNT() != null
                    ? "distinct_count"
                    : (ctx.DC() != null ? "dc" : "distinct_count_approx");
            return funcName + "(" + visit(ctx.valueExpression()) + ")";
        }

        @Override
        public String visitFunctionArgs(OpenSearchPPLParser.FunctionArgsContext ctx) {
            StringBuilder sb = new StringBuilder();
            List<OpenSearchPPLParser.FunctionArgContext> args = ctx.functionArg();
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(visit(args.get(i)));
            }
            return sb.toString();
        }

        @Override
        public String visitFunctionArg(OpenSearchPPLParser.FunctionArgContext ctx) {
            return visit(ctx.functionArgExpression());
        }

        @Override
        public String visitFunctionArgExpression(
            OpenSearchPPLParser.FunctionArgExpressionContext ctx
        ) {
            if (ctx.logicalExpression() != null) {
                return visit(ctx.logicalExpression());
            }
            if (ctx.lambda() != null) {
                return visit(ctx.lambda());
            }
            return visitChildren(ctx);
        }

        // Field expressions
        @Override
        public String visitFieldExpression(OpenSearchPPLParser.FieldExpressionContext ctx) {
            return visit(ctx.qualifiedName());
        }

        @Override
        public String visitWcFieldExpression(OpenSearchPPLParser.WcFieldExpressionContext ctx) {
            return visit(ctx.wcQualifiedName());
        }

        @Override
        public String visitFieldList(OpenSearchPPLParser.FieldListContext ctx) {
            StringBuilder sb = new StringBuilder();
            List<OpenSearchPPLParser.FieldExpressionContext> fields = ctx.fieldExpression();
            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(visit(fields.get(i)));
            }
            return sb.toString();
        }

        // Qualified names
        @Override
        public String visitIdentsAsQualifiedName(
            OpenSearchPPLParser.IdentsAsQualifiedNameContext ctx
        ) {
            StringBuilder sb = new StringBuilder();
            List<OpenSearchPPLParser.IdentContext> idents = ctx.ident();
            for (int i = 0; i < idents.size(); i++) {
                if (i > 0) sb.append(".");
                sb.append(visit(idents.get(i)));
            }
            return sb.toString();
        }

        @Override
        public String visitIdentsAsWildcardQualifiedName(
            OpenSearchPPLParser.IdentsAsWildcardQualifiedNameContext ctx
        ) {
            StringBuilder sb = new StringBuilder();
            List<OpenSearchPPLParser.WildcardContext> wildcards = ctx.wildcard();
            for (int i = 0; i < wildcards.size(); i++) {
                if (i > 0) sb.append(".");
                sb.append(visit(wildcards.get(i)));
            }
            return sb.toString();
        }

        @Override
        public String visitIdentsAsTableQualifiedName(
            OpenSearchPPLParser.IdentsAsTableQualifiedNameContext ctx
        ) {
            StringBuilder sb = new StringBuilder();
            // Handle tableIdent which may contain CLUSTER prefix
            if (ctx.tableIdent() != null) {
                sb.append(visit(ctx.tableIdent()));
            }
            // Handle additional ident parts separated by DOT
            List<OpenSearchPPLParser.IdentContext> idents = ctx.ident();
            for (int i = 0; i < idents.size(); i++) {
                sb.append(".");
                sb.append(visit(idents.get(i)));
            }
            return sb.toString();
        }

        @Override
        public String visitTableIdent(OpenSearchPPLParser.TableIdentContext ctx) {
            StringBuilder sb = new StringBuilder();
            if (ctx.CLUSTER() != null) {
                // CLUSTER token already includes the colon (e.g., "cluster:")
                sb.append(ctx.CLUSTER().getText());
            }
            sb.append(visit(ctx.ident()));
            return sb.toString();
        }

        @Override
        public String visitWildcard(OpenSearchPPLParser.WildcardContext ctx) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (child instanceof TerminalNode) {
                    sb.append(child.getText());
                } else {
                    sb.append(visit(child));
                }
            }
            return sb.toString();
        }

        @Override
        public String visitIdent(OpenSearchPPLParser.IdentContext ctx) {
            return getOriginalText(ctx);
        }

        // Literals
        @Override
        public String visitLiteralValue(OpenSearchPPLParser.LiteralValueContext ctx) {
            return getOriginalText(ctx);
        }

        @Override
        public String visitIntegerLiteral(OpenSearchPPLParser.IntegerLiteralContext ctx) {
            return ctx.getText();
        }

        @Override
        public String visitDecimalLiteral(OpenSearchPPLParser.DecimalLiteralContext ctx) {
            return ctx.getText();
        }

        @Override
        public String visitBooleanLiteral(OpenSearchPPLParser.BooleanLiteralContext ctx) {
            return ctx.getText().toLowerCase();
        }

        @Override
        public String visitStringLiteral(OpenSearchPPLParser.StringLiteralContext ctx) {
            return getOriginalText(ctx);
        }

        // Search expressions for search command
        @Override
        public String visitSearchFieldCompare(OpenSearchPPLParser.SearchFieldCompareContext ctx) {
            String field = visit(ctx.fieldExpression());
            String op = formatSearchOperator(ctx.searchComparisonOperator());
            String literal = visit(ctx.searchLiteral());
            return field + " " + op + " " + literal;
        }

        @Override
        public String visitSearchLiteral(OpenSearchPPLParser.SearchLiteralContext ctx) {
            return getOriginalText(ctx);
        }

        // Value list for IN expressions
        @Override
        public String visitValueList(OpenSearchPPLParser.ValueListContext ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append("(");
            List<OpenSearchPPLParser.LiteralValueContext> values = ctx.literalValue();
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(visit(values.get(i)));
            }
            sb.append(")");
            return sb.toString();
        }

        // Commands context
        @Override
        public String visitCommands(OpenSearchPPLParser.CommandsContext ctx) {
            return visitChildren(ctx);
        }

        // Default visitor implementation
        @Override
        protected String defaultResult() {
            return "";
        }

        @Override
        protected String aggregateResult(String aggregate, String nextResult) {
            if (aggregate == null || aggregate.isEmpty()) {
                return nextResult;
            }
            if (nextResult == null || nextResult.isEmpty()) {
                return aggregate;
            }
            return aggregate + nextResult;
        }

        // Helper methods
        private String formatOperator(OpenSearchPPLParser.ComparisonOperatorContext ctx) {
            if (ctx.EQUAL() != null || ctx.DOUBLE_EQUAL() != null) return "=";
            if (ctx.NOT_EQUAL() != null) return "!=";
            if (ctx.LESS() != null) return "<";
            if (ctx.GREATER() != null) return ">";
            if (ctx.NOT_LESS() != null) return ">=";
            if (ctx.NOT_GREATER() != null) return "<=";
            return ctx.getText();
        }

        private String formatSearchOperator(
            OpenSearchPPLParser.SearchComparisonOperatorContext ctx
        ) {
            return getOriginalText(ctx).toLowerCase();
        }

        private String getOriginalText(ParserRuleContext ctx) {
            if (ctx.start == null || ctx.stop == null) {
                return "";
            }
            return tokenStream.getText(ctx.start, ctx.stop);
        }
    }
}
