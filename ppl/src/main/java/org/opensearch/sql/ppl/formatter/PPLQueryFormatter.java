/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

import static org.opensearch.sql.ppl.formatter.FormatterConstants.*;
import static org.opensearch.sql.ppl.formatter.FormatterUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.opensearch.sql.common.antlr.CaseInsensitiveCharStream;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLLexer;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLParser;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLParserBaseVisitor;

/**
 * PPL Query Formatter that formats PPL queries with consistent style using ANTLR parse tree.
 *
 * <p>This formatter provides the following normalization:
 * <ul>
 *   <li>Lowercase keywords (WHERE, AND, OR, etc.)</li>
 *   <li>Consistent spacing around operators (=, !=, <, >, etc.)</li>
 *   <li>Consistent comma spacing in lists</li>
 *   <li>Normalized pipe separators between commands</li>
 *   <li>Lowercase function names</li>
 *   <li>Preserved identifier case (field names, table names)</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * PPLQueryFormatter formatter = new PPLQueryFormatter();
 * String formatted = formatter.format("SOURCE=logs|WHERE age>30|FIELDS name,age");
 * // Result: "source=logs | where age > 30 | fields name, age"
 * }</pre>
 *
 * <p>Error Handling:
 * <ul>
 *   <li>If the query has syntax errors, the original query is returned unchanged</li>
 *   <li>Null or empty queries return an empty string</li>
 * </ul>
 *
 * @see FormatterConstants for formatting constants
 * @see FormatterUtils for utility methods
 */
public class PPLQueryFormatter {

    /**
     * Format a PPL query string with consistent styling.
     *
     * <p>The formatter parses the query using ANTLR and walks the parse tree to produce
     * a normalized output. If the query cannot be parsed (syntax errors), the original
     * query is returned unchanged.
     *
     * @param query the PPL query to format
     * @return the formatted query, or original query if parsing fails, or empty string if null
     */
    public String format(String query) {
        if (query == null || query.trim().isEmpty()) {
            return EMPTY;
        }

        try {
            // Set up lexer and parser with error listeners
            OpenSearchPPLLexer lexer = new OpenSearchPPLLexer(new CaseInsensitiveCharStream(query));
            CommonTokenStream tokenStream = new CommonTokenStream(lexer);
            OpenSearchPPLParser parser = new OpenSearchPPLParser(tokenStream);

            // Add custom error listener to detect syntax errors
            FormatterErrorListener errorListener = new FormatterErrorListener();
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);

            // Parse the query
            ParseTree tree = parser.root();

            // If there are syntax errors, return the original query
            if (errorListener.hasErrors()) {
                return query;
            }

            // Format the query using the visitor
            FormattingVisitor visitor = new FormattingVisitor(tokenStream);
            String result = visitor.visit(tree);

            return result != null ? result.trim() : query;
        } catch (Exception e) {
            // If any unexpected error occurs, return the original query
            return query;
        }
    }

    /**
     * Unified formatting visitor that handles all PPL parse tree nodes.
     *
     * <p>This visitor extends the base ANTLR visitor and provides formatting logic for
     * each type of parse tree node. The visitor produces a formatted string representation
     * of the query with consistent styling.
     *
     * <p>Key formatting rules:
     * <ul>
     *   <li>Keywords are lowercase</li>
     *   <li>Identifiers preserve original case</li>
     *   <li>Binary operators have spaces on both sides</li>
     *   <li>Commas are followed by a space</li>
     *   <li>Pipes have spaces on both sides</li>
     * </ul>
     */
    private static class FormattingVisitor extends OpenSearchPPLParserBaseVisitor<String> {

        /** The token stream containing the original query text. */
        private final CommonTokenStream tokenStream;

        /**
         * Creates a new formatting visitor.
         *
         * @param tokenStream the token stream from the lexer
         * @throws NullPointerException if tokenStream is null
         */
        FormattingVisitor(CommonTokenStream tokenStream) {
            this.tokenStream = Objects.requireNonNull(tokenStream, "tokenStream must not be null");
        }

        // ========== Root and Statement Structure ==========

        /**
         * Visits the root of the parse tree.
         *
         * @param ctx the root context
         * @return the formatted query
         */
        @Override
        public String visitRoot(OpenSearchPPLParser.RootContext ctx) {
            if (ctx == null || ctx.pplStatement() == null) {
                return EMPTY;
            }
            return safeVisit(ctx.pplStatement());
        }

        /**
         * Visits a PPL statement.
         *
         * @param ctx the PPL statement context
         * @return the formatted statement
         */
        @Override
        public String visitPplStatement(OpenSearchPPLParser.PplStatementContext ctx) {
            return visitChildren(ctx);
        }

        /**
         * Visits a query statement with pipe-separated commands.
         *
         * @param ctx the query statement context
         * @return the formatted query
         */
        @Override
        public String visitQueryStatement(OpenSearchPPLParser.QueryStatementContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(safeVisit(ctx.pplCommands()));

            for (OpenSearchPPLParser.CommandsContext cmd : ctx.commands()) {
                sb.append(PIPE_SEPARATOR).append(safeVisit(cmd));
            }

            return sb.toString();
        }

        // ========== Search and Source Commands ==========

        /**
         * Visits a search from command with optional search expressions.
         *
         * @param ctx the search from context
         * @return the formatted search command
         */
        @Override
        public String visitSearchFrom(OpenSearchPPLParser.SearchFromContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

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
                        if (foundFromClause) {
                            afterExpressions.add(expr);
                        } else {
                            beforeExpressions.add(expr);
                        }
                    }
                }
            }

            sb.append(safeVisit(ctx.fromClause()));

            for (String expr : beforeExpressions) {
                sb.append(SPACE).append(expr);
            }
            for (String expr : afterExpressions) {
                sb.append(SPACE).append(expr);
            }

            return sb.toString();
        }

        /**
         * Visits a from clause (source= or index=).
         *
         * @param ctx the from clause context
         * @return the formatted from clause
         */
        @Override
        public String visitFromClause(OpenSearchPPLParser.FromClauseContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();

            if (ctx.SOURCE() != null) {
                sb.append(KEYWORD_SOURCE);
            } else if (ctx.INDEX() != null) {
                sb.append(KEYWORD_INDEX);
            }

            if (ctx.tableOrSubqueryClause() != null) {
                sb.append(safeVisit(ctx.tableOrSubqueryClause()));
            } else if (ctx.tableFunction() != null) {
                sb.append(safeVisit(ctx.tableFunction()));
            } else if (ctx.dynamicSourceClause() != null) {
                sb.append(safeVisit(ctx.dynamicSourceClause()));
            }

            return sb.toString();
        }

        /**
         * Visits a table or subquery clause.
         *
         * @param ctx the context
         * @return the formatted clause
         */
        @Override
        public String visitTableOrSubqueryClause(
            OpenSearchPPLParser.TableOrSubqueryClauseContext ctx
        ) {
            if (ctx == null) {
                return EMPTY;
            }
            if (ctx.tableSourceClause() != null) {
                return safeVisit(ctx.tableSourceClause());
            }
            return visitChildren(ctx);
        }

        /**
         * Visits a table source clause with optional alias.
         *
         * @param ctx the context
         * @return the formatted table sources
         */
        @Override
        public String visitTableSourceClause(OpenSearchPPLParser.TableSourceClauseContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

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

        /**
         * Visits a table source.
         *
         * @param ctx the context
         * @return the formatted table source
         */
        @Override
        public String visitTableSource(OpenSearchPPLParser.TableSourceContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            if (ctx.tableQualifiedName() != null) {
                return safeVisit(ctx.tableQualifiedName());
            }
            if (ctx.ID_DATE_SUFFIX() != null) {
                return ctx.ID_DATE_SUFFIX().getText();
            }
            return getOriginalText(ctx);
        }

        // ========== Describe Command ==========

        /**
         * Visits a describe command.
         *
         * @param ctx the context
         * @return the formatted describe command
         */
        @Override
        public String visitDescribeCommand(OpenSearchPPLParser.DescribeCommandContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return KEYWORD_DESCRIBE + SPACE + safeVisit(ctx.tableSourceClause());
        }

        // ========== Fields Command ==========

        /**
         * Visits a fields command.
         *
         * @param ctx the context
         * @return the formatted fields command
         */
        @Override
        public String visitFieldsCommand(OpenSearchPPLParser.FieldsCommandContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return KEYWORD_FIELDS + SPACE + safeVisit(ctx.fieldsCommandBody());
        }

        /**
         * Visits a fields command body with optional +/- prefix.
         *
         * @param ctx the context
         * @return the formatted fields body
         */
        @Override
        public String visitFieldsCommandBody(OpenSearchPPLParser.FieldsCommandBodyContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();

            if (ctx.PLUS() != null) {
                sb.append(FIELD_INCLUDE);
            } else if (ctx.MINUS() != null) {
                sb.append(FIELD_EXCLUDE);
            }

            sb.append(safeVisit(ctx.wcFieldList()));
            return sb.toString();
        }

        /**
         * Visits a wildcard field list.
         *
         * @param ctx the context
         * @return the formatted field list
         */
        @Override
        public String visitWcFieldList(OpenSearchPPLParser.WcFieldListContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();
            List<OpenSearchPPLParser.SelectFieldExpressionContext> fields =
                ctx.selectFieldExpression();
            List<TerminalNode> commas = ctx.COMMA();

            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) {
                    if (commas != null && i - 1 < commas.size()) {
                        sb.append(COMMA_SEPARATOR);
                    } else {
                        sb.append(SPACE);
                    }
                }
                sb.append(safeVisit(fields.get(i)));
            }

            return sb.toString();
        }

        /**
         * Visits a select field expression.
         *
         * @param ctx the context
         * @return the formatted field expression
         */
        @Override
        public String visitSelectFieldExpression(
            OpenSearchPPLParser.SelectFieldExpressionContext ctx
        ) {
            if (ctx == null) {
                return EMPTY;
            }

            if (ctx.STAR() != null) {
                return OP_MULTIPLY;
            }

            String fieldName = safeVisit(ctx.wcQualifiedName());

            // Handle prefix wildcards like "*name" - add space after leading *
            if (
                fieldName.startsWith(OP_MULTIPLY) &&
                fieldName.length() > 1 &&
                fieldName.charAt(1) != ' '
            ) {
                return OP_MULTIPLY + SPACE + fieldName.substring(1);
            }

            return fieldName;
        }

        // ========== Where Command ==========

        /**
         * Visits a where command.
         *
         * @param ctx the context
         * @return the formatted where command
         */
        @Override
        public String visitWhereCommand(OpenSearchPPLParser.WhereCommandContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return KEYWORD_WHERE + SPACE + safeVisit(ctx.logicalExpression());
        }

        // ========== Stats Command ==========

        /**
         * Visits a stats command with aggregations and optional grouping.
         *
         * @param ctx the context
         * @return the formatted stats command
         */
        @Override
        public String visitStatsCommand(OpenSearchPPLParser.StatsCommandContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder(KEYWORD_STATS);

            // Stats arguments (partitions, allnum, delim, bucket_nullable)
            if (ctx.statsArgs() != null) {
                String args = safeVisit(ctx.statsArgs());
                if (isNotEmpty(args)) {
                    sb.append(SPACE).append(args);
                }
            }

            // Aggregation terms
            List<OpenSearchPPLParser.StatsAggTermContext> aggTerms = ctx.statsAggTerm();
            for (int i = 0; i < aggTerms.size(); i++) {
                if (i == 0) sb.append(SPACE);
                else sb.append(COMMA_SEPARATOR);
                sb.append(safeVisit(aggTerms.get(i)));
            }

            // By clause
            if (ctx.statsByClause() != null) {
                sb.append(SPACE).append(safeVisit(ctx.statsByClause()));
            }

            return sb.toString();
        }

        /**
         * Visits stats arguments (partitions, allnum, delim, bucket_nullable).
         *
         * @param ctx the context
         * @return the formatted stats arguments
         */
        @Override
        public String visitStatsArgs(OpenSearchPPLParser.StatsArgsContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            List<String> args = new ArrayList<>();

            // Handle partitions argument
            if (ctx.partitionsArg() != null) {
                for (OpenSearchPPLParser.PartitionsArgContext arg : ctx.partitionsArg()) {
                    args.add(safeVisit(arg));
                }
            }

            // Handle allnum argument
            if (ctx.allnumArg() != null) {
                for (OpenSearchPPLParser.AllnumArgContext arg : ctx.allnumArg()) {
                    args.add(safeVisit(arg));
                }
            }

            // Handle delim argument
            if (ctx.delimArg() != null) {
                for (OpenSearchPPLParser.DelimArgContext arg : ctx.delimArg()) {
                    args.add(safeVisit(arg));
                }
            }

            // Handle bucket_nullable argument
            if (ctx.bucketNullableArg() != null) {
                for (OpenSearchPPLParser.BucketNullableArgContext arg : ctx.bucketNullableArg()) {
                    args.add(safeVisit(arg));
                }
            }

            return joinWithSpace(args);
        }

        /**
         * Visits a partitions argument.
         *
         * @param ctx the context
         * @return the formatted partitions argument
         */
        @Override
        public String visitPartitionsArg(OpenSearchPPLParser.PartitionsArgContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return KEYWORD_PARTITIONS + OP_EQUAL + safeVisit(ctx.partitions);
        }

        /**
         * Visits an allnum argument.
         *
         * @param ctx the context
         * @return the formatted allnum argument
         */
        @Override
        public String visitAllnumArg(OpenSearchPPLParser.AllnumArgContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return KEYWORD_ALLNUM + OP_EQUAL + safeVisit(ctx.allnum);
        }

        /**
         * Visits a delim argument.
         *
         * @param ctx the context
         * @return the formatted delim argument
         */
        @Override
        public String visitDelimArg(OpenSearchPPLParser.DelimArgContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return KEYWORD_DELIM + OP_EQUAL + safeVisit(ctx.delim);
        }

        /**
         * Visits a bucket_nullable argument.
         *
         * @param ctx the context
         * @return the formatted bucket_nullable argument
         */
        @Override
        public String visitBucketNullableArg(OpenSearchPPLParser.BucketNullableArgContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return KEYWORD_BUCKET_NULLABLE + OP_EQUAL + safeVisit(ctx.bucket_nullable);
        }

        /**
         * Visits a stats aggregation term with optional alias.
         *
         * @param ctx the context
         * @return the formatted aggregation term
         */
        @Override
        public String visitStatsAggTerm(OpenSearchPPLParser.StatsAggTermContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(safeVisit(ctx.statsFunction()));

            if (ctx.alias != null) {
                sb.append(SPACE).append(KEYWORD_AS).append(SPACE).append(safeVisit(ctx.alias));
            }

            return sb.toString();
        }

        // ========== Eventstats Command ==========

        /**
         * Visits an eventstats command.
         *
         * @param ctx the context
         * @return the formatted eventstats command
         */
        @Override
        public String visitEventstatsCommand(OpenSearchPPLParser.EventstatsCommandContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder(KEYWORD_EVENTSTATS);

            List<OpenSearchPPLParser.EventstatsAggTermContext> aggTerms = ctx.eventstatsAggTerm();
            for (int i = 0; i < aggTerms.size(); i++) {
                if (i == 0) sb.append(SPACE);
                else sb.append(COMMA_SEPARATOR);
                sb.append(safeVisit(aggTerms.get(i)));
            }

            if (ctx.statsByClause() != null) {
                sb.append(SPACE).append(safeVisit(ctx.statsByClause()));
            }

            return sb.toString();
        }

        /**
         * Visits an eventstats aggregation term.
         *
         * @param ctx the context
         * @return the formatted aggregation term
         */
        @Override
        public String visitEventstatsAggTerm(OpenSearchPPLParser.EventstatsAggTermContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(safeVisit(ctx.windowFunction()));

            if (ctx.alias != null) {
                sb.append(SPACE).append(KEYWORD_AS).append(SPACE).append(safeVisit(ctx.alias));
            }

            return sb.toString();
        }

        /**
         * Visits a window function.
         *
         * @param ctx the context
         * @return the formatted window function
         */
        @Override
        public String visitWindowFunction(OpenSearchPPLParser.WindowFunctionContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(safeVisit(ctx.windowFunctionName()));
            sb.append(LPAREN);
            if (ctx.functionArgs() != null) {
                sb.append(safeVisit(ctx.functionArgs()));
            }
            sb.append(RPAREN);

            return sb.toString();
        }

        /**
         * Visits a window function name.
         *
         * @param ctx the context
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
            return normalizeKeyword(getOriginalText(ctx));
        }

        /**
         * Visits a stats by clause.
         *
         * @param ctx the context
         * @return the formatted by clause
         */
        @Override
        public String visitStatsByClause(OpenSearchPPLParser.StatsByClauseContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder(KEYWORD_BY);

            if (ctx.fieldList() != null) {
                sb.append(SPACE).append(safeVisit(ctx.fieldList()));
            }

            if (ctx.bySpanClause() != null) {
                sb.append(SPACE).append(safeVisit(ctx.bySpanClause()));
                if (ctx.fieldList() != null) {
                    sb.append(COMMA_SEPARATOR).append(safeVisit(ctx.fieldList()));
                }
            }

            return sb.toString();
        }

        // ========== Eval Command ==========

        /**
         * Visits an eval command.
         *
         * @param ctx the context
         * @return the formatted eval command
         */
        @Override
        public String visitEvalCommand(OpenSearchPPLParser.EvalCommandContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder(KEYWORD_EVAL);

            List<OpenSearchPPLParser.EvalClauseContext> clauses = ctx.evalClause();
            for (int i = 0; i < clauses.size(); i++) {
                if (i == 0) sb.append(SPACE);
                else sb.append(COMMA_SEPARATOR);
                sb.append(safeVisit(clauses.get(i)));
            }

            return sb.toString();
        }

        /**
         * Visits an eval clause (assignment).
         *
         * @param ctx the context
         * @return the formatted assignment
         */
        @Override
        public String visitEvalClause(OpenSearchPPLParser.EvalClauseContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            String fieldExpr = safeVisit(ctx.fieldExpression());
            String logicalExpr = safeVisit(ctx.logicalExpression());
            return formatAssignment(fieldExpr, logicalExpr);
        }

        // ========== Join Command ==========

        /**
         * Visits a join command.
         *
         * @param ctx the context
         * @return the formatted join command
         */
        @Override
        public String visitJoinCommand(OpenSearchPPLParser.JoinCommandContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder(KEYWORD_JOIN);

            if (ctx.joinCriteria() != null) {
                sb.append(SPACE).append(safeVisit(ctx.joinCriteria()));
            }

            if (ctx.right != null) {
                sb.append(SPACE).append(safeVisit(ctx.right));
            }

            return sb.toString();
        }

        /**
         * Visits join criteria (ON or WHERE clause).
         *
         * @param ctx the context
         * @return the formatted join criteria
         */
        @Override
        public String visitJoinCriteria(OpenSearchPPLParser.JoinCriteriaContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();

            if (ctx.ON() != null) {
                sb.append(KEYWORD_ON).append(SPACE);
            } else if (ctx.WHERE() != null) {
                sb.append(KEYWORD_WHERE).append(SPACE);
            }

            sb.append(safeVisit(ctx.logicalExpression()));

            return sb.toString();
        }

        // ========== Logical Expressions ==========

        /**
         * Visits a logical NOT expression.
         *
         * @param ctx the context
         * @return the formatted NOT expression
         */
        @Override
        public String visitLogicalNot(OpenSearchPPLParser.LogicalNotContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return KEYWORD_NOT + SPACE + safeVisit(ctx.logicalExpression());
        }

        /**
         * Visits a logical AND expression.
         *
         * @param ctx the context
         * @return the formatted AND expression
         */
        @Override
        public String visitLogicalAnd(OpenSearchPPLParser.LogicalAndContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return formatBinaryExpr(safeVisit(ctx.left), KEYWORD_AND, safeVisit(ctx.right));
        }

        /**
         * Visits a logical OR expression.
         *
         * @param ctx the context
         * @return the formatted OR expression
         */
        @Override
        public String visitLogicalOr(OpenSearchPPLParser.LogicalOrContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return formatBinaryExpr(safeVisit(ctx.left), KEYWORD_OR, safeVisit(ctx.right));
        }

        /**
         * Visits a logical XOR expression.
         *
         * @param ctx the context
         * @return the formatted XOR expression
         */
        @Override
        public String visitLogicalXor(OpenSearchPPLParser.LogicalXorContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return formatBinaryExpr(safeVisit(ctx.left), KEYWORD_XOR, safeVisit(ctx.right));
        }

        /**
         * Visits a logical expression wrapper.
         *
         * @param ctx the context
         * @return the formatted expression
         */
        @Override
        public String visitLogicalExpr(OpenSearchPPLParser.LogicalExprContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return safeVisit(ctx.expression());
        }

        // ========== Comparison Expressions ==========

        /**
         * Visits a comparison expression.
         *
         * @param ctx the context
         * @return the formatted comparison
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
         * @param ctx the operator context
         * @return the canonical operator
         */
        private String formatComparisonOperator(OpenSearchPPLParser.ComparisonOperatorContext ctx) {
            if (ctx == null) {
                return OP_EQUAL;
            }
            if (ctx.EQUAL() != null || ctx.DOUBLE_EQUAL() != null) return OP_EQUAL;
            if (ctx.NOT_EQUAL() != null) return OP_NOT_EQUAL;
            if (ctx.LESS() != null) return OP_LESS;
            if (ctx.GREATER() != null) return OP_GREATER;
            if (ctx.NOT_LESS() != null) return OP_GREATER_EQUAL;
            if (ctx.NOT_GREATER() != null) return OP_LESS_EQUAL;
            return ctx.getText();
        }

        /**
         * Visits an IN expression.
         *
         * @param ctx the context
         * @return the formatted IN expression
         */
        @Override
        public String visitInExpr(OpenSearchPPLParser.InExprContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();
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
         * @param ctx the context
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

        /**
         * Visits a value expression wrapper.
         *
         * @param ctx the context
         * @return the formatted expression
         */
        @Override
        public String visitValueExpr(OpenSearchPPLParser.ValueExprContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return safeVisit(ctx.valueExpression());
        }

        /**
         * Visits a literal value expression.
         *
         * @param ctx the context
         * @return the formatted literal
         */
        @Override
        public String visitLiteralValueExpr(OpenSearchPPLParser.LiteralValueExprContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return safeVisit(ctx.literalValue());
        }

        /**
         * Visits a field expression with arithmetic heuristic formatting.
         *
         * @param ctx the context
         * @return the formatted field expression
         */
        @Override
        public String visitFieldExpr(OpenSearchPPLParser.FieldExprContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            String fieldName = safeVisit(ctx.fieldExpression());
            return formatArithmeticInFieldName(fieldName);
        }

        /**
         * Visits a nested (parenthesized) value expression.
         *
         * @param ctx the context
         * @return the formatted parenthesized expression
         */
        @Override
        public String visitNestedValueExpr(OpenSearchPPLParser.NestedValueExprContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return wrapInParens(safeVisit(ctx.logicalExpression()));
        }

        // ========== Function Calls ==========

        /**
         * Visits a function call expression.
         *
         * @param ctx the context
         * @return the formatted function call
         */
        @Override
        public String visitFunctionCallExpr(OpenSearchPPLParser.FunctionCallExprContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return safeVisit(ctx.functionCall());
        }

        /**
         * Visits an eval function call.
         *
         * @param ctx the context
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
         * Visits an eval function name (normalized to lowercase).
         *
         * @param ctx the context
         * @return the lowercase function name
         */
        @Override
        public String visitEvalFunctionName(OpenSearchPPLParser.EvalFunctionNameContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return normalizeKeyword(getOriginalText(ctx));
        }

        /**
         * Visits a stats function call.
         *
         * @param ctx the context
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
         * Visits a stats function name (normalized to lowercase).
         *
         * @param ctx the context
         * @return the lowercase function name
         */
        @Override
        public String visitStatsFunctionName(OpenSearchPPLParser.StatsFunctionNameContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return normalizeKeyword(getOriginalText(ctx));
        }

        /**
         * Visits a count() function call with no arguments.
         *
         * @param ctx the context
         * @return "count()"
         */
        @Override
        public String visitCountAllFunctionCall(
            OpenSearchPPLParser.CountAllFunctionCallContext ctx
        ) {
            return String.format(FUNC_NO_ARGS_FORMAT, FUNC_COUNT);
        }

        /**
         * Visits a count() function call with an expression argument.
         *
         * @param ctx the context
         * @return the formatted count call
         */
        @Override
        public String visitCountEvalFunctionCall(
            OpenSearchPPLParser.CountEvalFunctionCallContext ctx
        ) {
            if (ctx == null) {
                return String.format(FUNC_NO_ARGS_FORMAT, FUNC_COUNT);
            }
            String arg = safeVisit(ctx.evalExpression());
            return String.format(FUNC_WITH_ARGS_FORMAT, FUNC_COUNT, arg);
        }

        /**
         * Visits a distinct count function call.
         *
         * @param ctx the context
         * @return the formatted distinct count call
         */
        @Override
        public String visitDistinctCountFunctionCall(
            OpenSearchPPLParser.DistinctCountFunctionCallContext ctx
        ) {
            if (ctx == null) {
                return EMPTY;
            }

            String funcName;
            if (ctx.DISTINCT_COUNT() != null) {
                funcName = FUNC_DISTINCT_COUNT;
            } else if (ctx.DC() != null) {
                funcName = FUNC_DC;
            } else {
                funcName = FUNC_DISTINCT_COUNT_APPROX;
            }

            return String.format(FUNC_WITH_ARGS_FORMAT, funcName, safeVisit(ctx.valueExpression()));
        }

        /**
         * Visits function arguments.
         *
         * @param ctx the context
         * @return the comma-separated arguments
         */
        @Override
        public String visitFunctionArgs(OpenSearchPPLParser.FunctionArgsContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            List<OpenSearchPPLParser.FunctionArgContext> args = ctx.functionArg();
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(COMMA_SEPARATOR);
                sb.append(safeVisit(args.get(i)));
            }

            return sb.toString();
        }

        /**
         * Visits a function argument.
         *
         * @param ctx the context
         * @return the formatted argument
         */
        @Override
        public String visitFunctionArg(OpenSearchPPLParser.FunctionArgContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return safeVisit(ctx.functionArgExpression());
        }

        /**
         * Visits a function argument expression.
         *
         * @param ctx the context
         * @return the formatted argument expression
         */
        @Override
        public String visitFunctionArgExpression(
            OpenSearchPPLParser.FunctionArgExpressionContext ctx
        ) {
            if (ctx == null) {
                return EMPTY;
            }
            if (ctx.logicalExpression() != null) {
                return safeVisit(ctx.logicalExpression());
            }
            if (ctx.lambda() != null) {
                return safeVisit(ctx.lambda());
            }
            return visitChildren(ctx);
        }

        // ========== Field Expressions ==========

        /**
         * Visits a field expression.
         *
         * @param ctx the context
         * @return the formatted field expression
         */
        @Override
        public String visitFieldExpression(OpenSearchPPLParser.FieldExpressionContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return safeVisit(ctx.qualifiedName());
        }

        /**
         * Visits a wildcard field expression.
         *
         * @param ctx the context
         * @return the formatted wildcard field expression
         */
        @Override
        public String visitWcFieldExpression(OpenSearchPPLParser.WcFieldExpressionContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return safeVisit(ctx.wcQualifiedName());
        }

        /**
         * Visits a field list.
         *
         * @param ctx the context
         * @return the comma-separated field list
         */
        @Override
        public String visitFieldList(OpenSearchPPLParser.FieldListContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            List<OpenSearchPPLParser.FieldExpressionContext> fields = ctx.fieldExpression();
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < fields.size(); i++) {
                if (i > 0) sb.append(COMMA_SEPARATOR);
                sb.append(safeVisit(fields.get(i)));
            }

            return sb.toString();
        }

        // ========== Qualified Names ==========

        /**
         * Visits a qualified name (dot-separated identifiers).
         *
         * @param ctx the context
         * @return the formatted qualified name
         */
        @Override
        public String visitIdentsAsQualifiedName(
            OpenSearchPPLParser.IdentsAsQualifiedNameContext ctx
        ) {
            if (ctx == null) {
                return EMPTY;
            }

            List<OpenSearchPPLParser.IdentContext> idents = ctx.ident();
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < idents.size(); i++) {
                if (i > 0) sb.append(DOT_SEPARATOR);
                sb.append(safeVisit(idents.get(i)));
            }

            return sb.toString();
        }

        /**
         * Visits a wildcard qualified name.
         *
         * @param ctx the context
         * @return the formatted wildcard qualified name
         */
        @Override
        public String visitIdentsAsWildcardQualifiedName(
            OpenSearchPPLParser.IdentsAsWildcardQualifiedNameContext ctx
        ) {
            if (ctx == null) {
                return EMPTY;
            }

            List<OpenSearchPPLParser.WildcardContext> wildcards = ctx.wildcard();
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < wildcards.size(); i++) {
                if (i > 0) sb.append(DOT_SEPARATOR);
                sb.append(safeVisit(wildcards.get(i)));
            }

            return sb.toString();
        }

        /**
         * Visits a table qualified name (may include cluster prefix).
         *
         * @param ctx the context
         * @return the formatted table qualified name
         */
        @Override
        public String visitIdentsAsTableQualifiedName(
            OpenSearchPPLParser.IdentsAsTableQualifiedNameContext ctx
        ) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();

            if (ctx.tableIdent() != null) {
                sb.append(safeVisit(ctx.tableIdent()));
            }

            List<OpenSearchPPLParser.IdentContext> idents = ctx.ident();
            for (OpenSearchPPLParser.IdentContext ident : idents) {
                sb.append(DOT_SEPARATOR).append(safeVisit(ident));
            }

            return sb.toString();
        }

        /**
         * Visits a table identifier (may include cluster prefix).
         *
         * @param ctx the context
         * @return the formatted table identifier
         */
        @Override
        public String visitTableIdent(OpenSearchPPLParser.TableIdentContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();

            if (ctx.CLUSTER() != null) {
                sb.append(ctx.CLUSTER().getText());
            }

            sb.append(safeVisit(ctx.ident()));
            return sb.toString();
        }

        /**
         * Visits a wildcard pattern.
         *
         * @param ctx the context
         * @return the formatted wildcard pattern
         */
        @Override
        public String visitWildcard(OpenSearchPPLParser.WildcardContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (child instanceof TerminalNode) {
                    sb.append(child.getText());
                } else {
                    sb.append(safeVisit(child));
                }
            }

            return sb.toString();
        }

        /**
         * Visits an identifier (preserves original case).
         *
         * @param ctx the context
         * @return the original identifier text
         */
        @Override
        public String visitIdent(OpenSearchPPLParser.IdentContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return preserveIdentifier(getOriginalText(ctx));
        }

        // ========== Literals ==========

        /**
         * Visits a literal value.
         *
         * @param ctx the context
         * @return the original literal text
         */
        @Override
        public String visitLiteralValue(OpenSearchPPLParser.LiteralValueContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return getOriginalText(ctx);
        }

        /**
         * Visits an integer literal.
         *
         * @param ctx the context
         * @return the integer text
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
         * @param ctx the context
         * @return the decimal text
         */
        @Override
        public String visitDecimalLiteral(OpenSearchPPLParser.DecimalLiteralContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return ctx.getText();
        }

        /**
         * Visits a boolean literal (normalized to lowercase).
         *
         * @param ctx the context
         * @return the lowercase boolean
         */
        @Override
        public String visitBooleanLiteral(OpenSearchPPLParser.BooleanLiteralContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return normalizeKeyword(ctx.getText());
        }

        /**
         * Visits a string literal.
         *
         * @param ctx the context
         * @return the original string literal
         */
        @Override
        public String visitStringLiteral(OpenSearchPPLParser.StringLiteralContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return getOriginalText(ctx);
        }

        // ========== Search Expressions ==========

        /**
         * Visits a search field comparison.
         *
         * @param ctx the context
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
         * @param ctx the operator context
         * @return the formatted operator
         */
        private String formatSearchOperator(
            OpenSearchPPLParser.SearchComparisonOperatorContext ctx
        ) {
            if (ctx == null) {
                return OP_EQUAL;
            }
            return normalizeKeyword(getOriginalText(ctx));
        }

        /**
         * Visits a search literal.
         *
         * @param ctx the context
         * @return the original literal text
         */
        @Override
        public String visitSearchLiteral(OpenSearchPPLParser.SearchLiteralContext ctx) {
            if (ctx == null) {
                return EMPTY;
            }
            return getOriginalText(ctx);
        }

        // ========== Value List ==========

        /**
         * Visits a value list (for IN expressions).
         *
         * @param ctx the context
         * @return the formatted value list
         */
        @Override
        public String visitValueList(OpenSearchPPLParser.ValueListContext ctx) {
            if (ctx == null) {
                return wrapInParens(EMPTY);
            }

            List<OpenSearchPPLParser.LiteralValueContext> values = ctx.literalValue();
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < values.size(); i++) {
                if (i > 0) sb.append(COMMA_SEPARATOR);
                sb.append(safeVisit(values.get(i)));
            }

            return wrapInParens(sb.toString());
        }

        // ========== Commands Wrapper ==========

        /**
         * Visits a commands context.
         *
         * @param ctx the context
         * @return the formatted command
         */
        @Override
        public String visitCommands(OpenSearchPPLParser.CommandsContext ctx) {
            return visitChildren(ctx);
        }

        // ========== Helper Methods ==========

        /**
         * Safely visits a parse tree node, returning empty string if null.
         *
         * @param node the node to visit
         * @return the visit result or empty string
         */
        private String safeVisit(ParseTree node) {
            if (node == null) {
                return EMPTY;
            }
            String result = visit(node);
            return result != null ? result : EMPTY;
        }

        /**
         * Gets the original text from a parser rule context.
         *
         * @param ctx the context
         * @return the original text
         */
        private String getOriginalText(ParserRuleContext ctx) {
            return FormatterUtils.getOriginalText(ctx, tokenStream);
        }

        /**
         * Returns the default result for unvisited nodes.
         *
         * @return empty string
         */
        @Override
        protected String defaultResult() {
            return EMPTY;
        }

        /**
         * Aggregates results from child visits.
         *
         * @param aggregate the accumulated result
         * @param nextResult the next result
         * @return the combined result
         */
        @Override
        protected String aggregateResult(String aggregate, String nextResult) {
            if (isEmpty(aggregate)) {
                return nullToEmpty(nextResult);
            }
            if (isEmpty(nextResult)) {
                return aggregate;
            }
            return aggregate + nextResult;
        }
    }
}
