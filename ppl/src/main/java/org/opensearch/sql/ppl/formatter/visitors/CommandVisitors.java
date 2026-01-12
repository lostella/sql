/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter.visitors;

import static org.opensearch.sql.ppl.formatter.FormatterConstants.*;
import static org.opensearch.sql.ppl.formatter.FormatterUtils.*;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLParser;

/**
 * Visitor methods for formatting PPL commands.
 *
 * <p>This class handles the formatting of various PPL command types including:
 * <ul>
 *   <li>Root and statement structure (query statements, pipe commands)</li>
 *   <li>Search commands (source, from, search)</li>
 *   <li>Data manipulation commands (fields, where, eval)</li>
 *   <li>Aggregation commands (stats, eventstats)</li>
 *   <li>Join commands</li>
 *   <li>Describe commands</li>
 * </ul>
 *
 * <p>Commands are formatted with consistent keyword casing (lowercase) and proper
 * spacing around operators and between clauses.
 */
public class CommandVisitors extends BaseFormattingVisitor {

    /**
     * Creates a new command visitor.
     *
     * @param tokenStream the token stream from the lexer
     */
    public CommandVisitors(CommonTokenStream tokenStream) {
        super(tokenStream);
    }

    // ========== Root and Statement Structure ==========

    /**
     * Visits the root of the parse tree.
     *
     * <p>The root contains the top-level PPL statement.
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
     * <p>A PPL statement contains either a query statement or other statement types.
     *
     * @param ctx the PPL statement context
     * @return the formatted statement
     */
    @Override
    public String visitPplStatement(OpenSearchPPLParser.PplStatementContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return visitChildren(ctx);
    }

    /**
     * Visits a query statement.
     *
     * <p>A query statement consists of an initial command (pplCommands) followed by
     * zero or more pipe-separated commands.
     *
     * <p>Example: {@code source=logs | where status=200 | fields name, age}
     *
     * @param ctx the query statement context
     * @return the formatted query with pipe separators
     */
    @Override
    public String visitQueryStatement(OpenSearchPPLParser.QueryStatementContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();
        sb.append(safeVisit(ctx.pplCommands()));

        // Add pipe-separated commands
        List<OpenSearchPPLParser.CommandsContext> commands = ctx.commands();
        if (commands != null) {
            for (OpenSearchPPLParser.CommandsContext cmd : commands) {
                sb.append(PIPE_SEPARATOR);
                sb.append(safeVisit(cmd));
            }
        }

        return sb.toString();
    }

    /**
     * Visits a commands context (a single command after a pipe).
     *
     * @param ctx the commands context
     * @return the formatted command
     */
    @Override
    public String visitCommands(OpenSearchPPLParser.CommandsContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return visitChildren(ctx);
    }

    // ========== Search and Source Commands ==========

    /**
     * Visits a search from command.
     *
     * <p>Handles the search command with its from clause and optional search expressions.
     * Search expressions can appear before or after the from clause.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code search source=logs} -> {@code source=logs}</li>
     *   <li>{@code source=logs status=200} -> {@code source=logs status = 200}</li>
     * </ul>
     *
     * @param ctx the search from context
     * @return the formatted search command
     */
    @Override
    public String visitSearchFrom(OpenSearchPPLParser.SearchFromContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();

        // Collect search expressions before and after fromClause
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

        // Format: source=... [search expressions]
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
     * Visits a from clause.
     *
     * <p>Handles the source= or index= clause that specifies the data source.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code SOURCE = logs} -> {@code source=logs}</li>
     *   <li>{@code INDEX = my_index} -> {@code index=my_index}</li>
     * </ul>
     *
     * @param ctx the from clause context
     * @return the formatted from clause
     */
    @Override
    public String visitFromClause(OpenSearchPPLParser.FromClauseContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();

        // Determine source keyword (source= or index=)
        if (ctx.SOURCE() != null) {
            sb.append(KEYWORD_SOURCE);
        } else if (ctx.INDEX() != null) {
            sb.append(KEYWORD_INDEX);
        }

        // Append the table/subquery clause
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
     * @param ctx the table or subquery clause context
     * @return the formatted clause
     */
    @Override
    public String visitTableOrSubqueryClause(
            OpenSearchPPLParser.TableOrSubqueryClauseContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        if (ctx.tableSourceClause() != null) {
            return safeVisit(ctx.tableSourceClause());
        }
        return visitChildren(ctx);
    }

    /**
     * Visits a table source clause.
     *
     * <p>Handles comma-separated table sources with optional alias.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code logs} -> {@code logs}</li>
     *   <li>{@code logs,users} -> {@code logs, users}</li>
     *   <li>{@code logs as l} -> {@code logs as l}</li>
     * </ul>
     *
     * @param ctx the table source clause context
     * @return the formatted table sources
     */
    @Override
    public String visitTableSourceClause(OpenSearchPPLParser.TableSourceClauseContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();

        List<OpenSearchPPLParser.TableSourceContext> sources = ctx.tableSource();
        if (sources != null && !sources.isEmpty()) {
            sb.append(visitAndJoinWithComma(sources));
        }

        // Append alias if present
        if (ctx.alias != null) {
            sb.append(SPACE).append(KEYWORD_AS).append(SPACE);
            sb.append(safeVisit(ctx.alias));
        }

        return sb.toString();
    }

    /**
     * Visits a table source.
     *
     * @param ctx the table source context
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
     * <p>Example: {@code DESCRIBE logs} -> {@code describe logs}
     *
     * @param ctx the describe command context
     * @return the formatted describe command
     */
    @Override
    public String visitDescribeCommand(OpenSearchPPLParser.DescribeCommandContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return formatCommand(KEYWORD_DESCRIBE, safeVisit(ctx.tableSourceClause()));
    }

    // ========== Fields Command ==========

    /**
     * Visits a fields command.
     *
     * <p>Example: {@code FIELDS name,age} -> {@code fields name, age}
     *
     * @param ctx the fields command context
     * @return the formatted fields command
     */
    @Override
    public String visitFieldsCommand(OpenSearchPPLParser.FieldsCommandContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return formatCommand(KEYWORD_FIELDS, safeVisit(ctx.fieldsCommandBody()));
    }

    /**
     * Visits the body of a fields command.
     *
     * <p>Handles optional +/- prefix for field inclusion/exclusion.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code name, age} -> {@code name, age}</li>
     *   <li>{@code + name} -> {@code + name}</li>
     *   <li>{@code - password} -> {@code - password}</li>
     * </ul>
     *
     * @param ctx the fields command body context
     * @return the formatted fields body
     */
    @Override
    public String visitFieldsCommandBody(OpenSearchPPLParser.FieldsCommandBodyContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();

        // Handle +/- prefix for inclusion/exclusion
        if (ctx.PLUS() != null) {
            sb.append(FIELD_INCLUDE);
        } else if (ctx.MINUS() != null) {
            sb.append(FIELD_EXCLUDE);
        }

        sb.append(safeVisit(ctx.wcFieldList()));

        return sb.toString();
    }

    // ========== Where Command ==========

    /**
     * Visits a where command.
     *
     * <p>Example: {@code WHERE age>30} -> {@code where age > 30}
     *
     * @param ctx the where command context
     * @return the formatted where command
     */
    @Override
    public String visitWhereCommand(OpenSearchPPLParser.WhereCommandContext ctx) {
        if (ctx == null || ctx.logicalExpression() == null) {
            return EMPTY;
        }
        return formatCommand(KEYWORD_WHERE, safeVisit(ctx.logicalExpression()));
    }

    // ========== Stats Command ==========

    /**
     * Visits a stats command.
     *
     * <p>The stats command performs aggregations on data, optionally grouped by fields.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code STATS count()} -> {@code stats count()}</li>
     *   <li>{@code STATS avg(age) BY status} -> {@code stats avg(age) by status}</li>
     *   <li>{@code STATS partitions=10 count()} -> {@code stats partitions=10 count()}</li>
     * </ul>
     *
     * @param ctx the stats command context
     * @return the formatted stats command
     */
    @Override
    public String visitStatsCommand(OpenSearchPPLParser.StatsCommandContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = builderWithKeyword(KEYWORD_STATS);

        // Stats arguments (partitions, allnum, delim, bucket_nullable)
        if (ctx.statsArgs() != null) {
            String args = safeVisit(ctx.statsArgs());
            appendIfNotEmpty(sb, args);
        }

        // Aggregation terms (e.g., count(), avg(field))
        List<OpenSearchPPLParser.StatsAggTermContext> aggTerms = ctx.statsAggTerm();
        if (aggTerms != null && !aggTerms.isEmpty()) {
            sb.append(SPACE);
            sb.append(visitAndJoinWithComma(aggTerms));
        }

        // By clause (e.g., by status)
        if (ctx.statsByClause() != null) {
            sb.append(SPACE).append(safeVisit(ctx.statsByClause()));
        }

        return sb.toString();
    }

    /**
     * Visits stats arguments.
     *
     * <p>Stats arguments include:
     * <ul>
     *   <li>partitions=N - number of partitions</li>
     *   <li>allnum=true/false - treat all values as numbers</li>
     *   <li>delim=X - delimiter for multi-value fields</li>
     *   <li>bucket_nullable=true/false - allow null buckets</li>
     * </ul>
     *
     * <p>Example: {@code partitions=10 allnum=true} -> {@code partitions=10 allnum=true}
     *
     * @param ctx the stats args context
     * @return the formatted stats arguments
     */
    @Override
    public String visitStatsArgs(OpenSearchPPLParser.StatsArgsContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        List<String> args = new ArrayList<>();

        // Handle partitions argument
        if (ctx.partitionsArg() != null && !ctx.partitionsArg().isEmpty()) {
            for (OpenSearchPPLParser.PartitionsArgContext arg : ctx.partitionsArg()) {
                args.add(safeVisit(arg));
            }
        }

        // Handle allnum argument
        if (ctx.allnumArg() != null && !ctx.allnumArg().isEmpty()) {
            for (OpenSearchPPLParser.AllnumArgContext arg : ctx.allnumArg()) {
                args.add(safeVisit(arg));
            }
        }

        // Handle delim argument
        if (ctx.delimArg() != null && !ctx.delimArg().isEmpty()) {
            for (OpenSearchPPLParser.DelimArgContext arg : ctx.delimArg()) {
                args.add(safeVisit(arg));
            }
        }

        // Handle bucket_nullable argument
        if (ctx.bucketNullableArg() != null && !ctx.bucketNullableArg().isEmpty()) {
            for (OpenSearchPPLParser.BucketNullableArgContext arg : ctx.bucketNullableArg()) {
                args.add(safeVisit(arg));
            }
        }

        return joinWithSpace(args);
    }

    /**
     * Visits a partitions argument.
     *
     * <p>Example: {@code PARTITIONS = 10} -> {@code partitions=10}
     *
     * @param ctx the partitions arg context
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
     * <p>Example: {@code ALLNUM = true} -> {@code allnum=true}
     *
     * @param ctx the allnum arg context
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
     * <p>Example: {@code DELIM = ','} -> {@code delim=','}
     *
     * @param ctx the delim arg context
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
     * <p>Example: {@code BUCKET_NULLABLE = true} -> {@code bucket_nullable=true}
     *
     * @param ctx the bucket nullable arg context
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
     * Visits a stats aggregation term.
     *
     * <p>An aggregation term consists of a stats function with an optional alias.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code count()} -> {@code count()}</li>
     *   <li>{@code avg(age) AS average_age} -> {@code avg(age) as average_age}</li>
     * </ul>
     *
     * @param ctx the stats agg term context
     * @return the formatted aggregation term
     */
    @Override
    public String visitStatsAggTerm(OpenSearchPPLParser.StatsAggTermContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();
        sb.append(safeVisit(ctx.statsFunction()));

        // Append alias if present
        if (ctx.alias != null) {
            sb.append(SPACE).append(KEYWORD_AS).append(SPACE);
            sb.append(safeVisit(ctx.alias));
        }

        return sb.toString();
    }

    // ========== Eventstats Command ==========

    /**
     * Visits an eventstats command.
     *
     * <p>Eventstats computes aggregate statistics and adds them to each event.
     *
     * <p>Example: {@code EVENTSTATS avg(latency) BY host} -> {@code eventstats avg(latency) by host}
     *
     * @param ctx the eventstats command context
     * @return the formatted eventstats command
     */
    @Override
    public String visitEventstatsCommand(OpenSearchPPLParser.EventstatsCommandContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = builderWithKeyword(KEYWORD_EVENTSTATS);

        // Aggregation terms
        List<OpenSearchPPLParser.EventstatsAggTermContext> aggTerms = ctx.eventstatsAggTerm();
        if (aggTerms != null && !aggTerms.isEmpty()) {
            sb.append(SPACE);
            sb.append(visitAndJoinWithComma(aggTerms));
        }

        // By clause
        if (ctx.statsByClause() != null) {
            sb.append(SPACE).append(safeVisit(ctx.statsByClause()));
        }

        return sb.toString();
    }

    /**
     * Visits an eventstats aggregation term.
     *
     * <p>Similar to stats aggregation term but uses window functions.
     *
     * @param ctx the eventstats agg term context
     * @return the formatted aggregation term
     */
    @Override
    public String visitEventstatsAggTerm(OpenSearchPPLParser.EventstatsAggTermContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();
        sb.append(safeVisit(ctx.windowFunction()));

        // Append alias if present
        if (ctx.alias != null) {
            sb.append(SPACE).append(KEYWORD_AS).append(SPACE);
            sb.append(safeVisit(ctx.alias));
        }

        return sb.toString();
    }

    /**
     * Visits a stats by clause.
     *
     * <p>The by clause specifies grouping for aggregations.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code BY status} -> {@code by status}</li>
     *   <li>{@code BY span(timestamp, 1h)} -> {@code by span(timestamp, 1h)}</li>
     *   <li>{@code BY span(timestamp, 1h), host} -> {@code by span(timestamp, 1h), host}</li>
     * </ul>
     *
     * @param ctx the stats by clause context
     * @return the formatted by clause
     */
    @Override
    public String visitStatsByClause(OpenSearchPPLParser.StatsByClauseContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = builderWithKeyword(KEYWORD_BY);

        // Handle field list
        if (ctx.fieldList() != null) {
            sb.append(SPACE).append(safeVisit(ctx.fieldList()));
        }

        // Handle span clause
        if (ctx.bySpanClause() != null) {
            sb.append(SPACE).append(safeVisit(ctx.bySpanClause()));
            // If both span and fieldList, add fieldList after span
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
     * <p>The eval command creates or modifies fields using expressions.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code EVAL doubled=value*2} -> {@code eval doubled = value * 2}</li>
     *   <li>{@code EVAL a=1, b=2} -> {@code eval a = 1, b = 2}</li>
     * </ul>
     *
     * @param ctx the eval command context
     * @return the formatted eval command
     */
    @Override
    public String visitEvalCommand(OpenSearchPPLParser.EvalCommandContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = builderWithKeyword(KEYWORD_EVAL);

        List<OpenSearchPPLParser.EvalClauseContext> clauses = ctx.evalClause();
        if (clauses != null && !clauses.isEmpty()) {
            sb.append(SPACE);
            sb.append(visitAndJoinWithComma(clauses));
        }

        return sb.toString();
    }

    /**
     * Visits an eval clause (a single assignment).
     *
     * <p>Example: {@code fieldName=expression} -> {@code fieldName = expression}
     *
     * @param ctx the eval clause context
     * @return the formatted eval clause
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
     * <p>The join command combines data from two sources based on a condition.
     *
     * <p>Example: {@code JOIN ON left.id=right.id right_table} ->
     *            {@code join on left.id = right.id right_table}
     *
     * @param ctx the join command context
     * @return the formatted join command
     */
    @Override
    public String visitJoinCommand(OpenSearchPPLParser.JoinCommandContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = builderWithKeyword(KEYWORD_JOIN);

        // Join criteria (ON or WHERE clause)
        if (ctx.joinCriteria() != null) {
            sb.append(SPACE).append(safeVisit(ctx.joinCriteria()));
        }

        // Right side table
        if (ctx.right != null) {
            sb.append(SPACE).append(safeVisit(ctx.right));
        }

        return sb.toString();
    }

    /**
     * Visits join criteria.
     *
     * <p>Join criteria specifies the condition for joining, using either ON or WHERE.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code ON a.id = b.id} -> {@code on a.id = b.id}</li>
     *   <li>{@code WHERE a.id = b.id} -> {@code where a.id = b.id}</li>
     * </ul>
     *
     * @param ctx the join criteria context
     * @return the formatted join criteria
     */
    @Override
    public String visitJoinCriteria(OpenSearchPPLParser.JoinCriteriaContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();

        if (ctx.ON() != null) {
            sb.append(KEYWORD_ON).append(SPACE);
        } else if (ctx.WHERE() != null) {
            sb.append(KEYWORD_WHERE).append(SPACE);
        }

        sb.append(safeVisit(ctx.logicalExpression()));

        return sb.toString();
    }
}
