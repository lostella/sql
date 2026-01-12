/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter.visitors;

import static org.opensearch.sql.ppl.formatter.FormatterConstants.*;
import static org.opensearch.sql.ppl.formatter.FormatterUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLParserBaseVisitor;

/**
 * Base class for PPL formatting visitors.
 *
 * <p>This abstract class provides common functionality shared across all formatting visitors,
 * including:
 * <ul>
 *   <li>Access to the token stream for extracting original text</li>
 *   <li>Helper methods for visiting and collecting child results</li>
 *   <li>Default visitor behavior (returning empty strings)</li>
 *   <li>Result aggregation logic</li>
 * </ul>
 *
 * <p>Subclasses should override specific visit methods to handle different parts of the PPL
 * grammar while using the helper methods provided here for common operations.
 */
public abstract class BaseFormattingVisitor extends OpenSearchPPLParserBaseVisitor<String> {

    /** The token stream containing the original query text. */
    protected final CommonTokenStream tokenStream;

    /**
     * Creates a new base formatting visitor.
     *
     * @param tokenStream the token stream from the lexer, used for extracting original text
     * @throws NullPointerException if tokenStream is null
     */
    protected BaseFormattingVisitor(CommonTokenStream tokenStream) {
        this.tokenStream = Objects.requireNonNull(tokenStream, "tokenStream must not be null");
    }

    // ========== Text Extraction Methods ==========

    /**
     * Extracts the original text from a parser rule context.
     *
     * <p>This method retrieves the exact text as it appeared in the original query, preserving
     * the original casing and spacing within the token range.
     *
     * @param ctx the parser rule context
     * @return the original text, or empty string if context is null or has no tokens
     */
    protected String getOriginalText(ParserRuleContext ctx) {
        return org.opensearch.sql.ppl.formatter.FormatterUtils.getOriginalText(ctx, tokenStream);
    }

    /**
     * Gets the text of a parse tree node safely.
     *
     * @param node the parse tree node (may be null)
     * @return the node's text or empty string if null
     */
    protected String getText(ParseTree node) {
        return getTextOrEmpty(node);
    }

    // ========== List Processing Methods ==========

    /**
     * Visits a list of parse tree nodes and collects the results.
     *
     * @param <T> the type of parse tree nodes
     * @param nodes the list of nodes to visit
     * @return a list of formatted strings from visiting each node
     */
    protected <T extends ParseTree> List<String> visitAll(List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> results = new ArrayList<>(nodes.size());
        for (T node : nodes) {
            String result = visit(node);
            if (result != null) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * Visits a list of parse tree nodes and joins the results with commas.
     *
     * @param <T> the type of parse tree nodes
     * @param nodes the list of nodes to visit
     * @return a comma-separated string of the visited results
     */
    protected <T extends ParseTree> String visitAndJoinWithComma(List<T> nodes) {
        return joinWithComma(visitAll(nodes));
    }

    /**
     * Visits a list of parse tree nodes and joins the results with dots.
     *
     * @param <T> the type of parse tree nodes
     * @param nodes the list of nodes to visit
     * @return a dot-separated string of the visited results
     */
    protected <T extends ParseTree> String visitAndJoinWithDot(List<T> nodes) {
        return joinWithDot(visitAll(nodes));
    }

    /**
     * Visits a list of parse tree nodes and joins the results with spaces.
     *
     * @param <T> the type of parse tree nodes
     * @param nodes the list of nodes to visit
     * @return a space-separated string of the visited results
     */
    protected <T extends ParseTree> String visitAndJoinWithSpace(List<T> nodes) {
        return joinWithSpace(visitAll(nodes));
    }

    /**
     * Visits a list of parse tree nodes and joins with a custom separator.
     *
     * @param <T> the type of parse tree nodes
     * @param nodes the list of nodes to visit
     * @param separator the separator to use between results
     * @return the joined string
     */
    protected <T extends ParseTree> String visitAndJoin(List<T> nodes, String separator) {
        return joinWith(visitAll(nodes), separator);
    }

    // ========== Safe Visit Methods ==========

    /**
     * Safely visits a parse tree node, returning empty string if null.
     *
     * @param node the node to visit (may be null)
     * @return the visit result or empty string
     */
    protected String safeVisit(ParseTree node) {
        if (node == null) {
            return EMPTY;
        }
        String result = visit(node);
        return result != null ? result : EMPTY;
    }

    /**
     * Safely visits a parse tree node, returning a default value if null.
     *
     * @param node the node to visit (may be null)
     * @param defaultValue the default value to return if node is null
     * @return the visit result or default value
     */
    protected String safeVisit(ParseTree node, String defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        String result = visit(node);
        return result != null ? result : defaultValue;
    }

    /**
     * Visits a node and applies a transformation to the result.
     *
     * @param node the node to visit
     * @param transformer the transformation function to apply
     * @return the transformed result
     */
    protected String visitAndTransform(ParseTree node, Function<String, String> transformer) {
        String result = safeVisit(node);
        return transformer.apply(result);
    }

    // ========== Builder Methods ==========

    /**
     * Creates a new StringBuilder for building formatted output.
     *
     * @return a new StringBuilder instance
     */
    protected StringBuilder newBuilder() {
        return new StringBuilder();
    }

    /**
     * Creates a new StringBuilder initialized with a keyword.
     *
     * @param keyword the keyword to initialize with
     * @return a new StringBuilder with the keyword
     */
    protected StringBuilder builderWithKeyword(String keyword) {
        return new StringBuilder(keyword);
    }

    // ========== Formatting Helpers ==========

    /**
     * Formats a command with its keyword and body.
     *
     * @param keyword the command keyword (e.g., "where", "fields")
     * @param body the command body
     * @return the formatted command
     */
    protected String formatCommand(String keyword, String body) {
        return keyword + SPACE + body;
    }

    /**
     * Formats a command with keyword, optional modifiers, and body.
     *
     * @param keyword the command keyword
     * @param modifier the optional modifier (may be null or empty)
     * @param body the command body
     * @return the formatted command
     */
    protected String formatCommandWithModifier(String keyword, String modifier, String body) {
        StringBuilder sb = new StringBuilder(keyword);
        if (isNotEmpty(modifier)) {
            sb.append(SPACE).append(modifier);
        }
        sb.append(SPACE).append(body);
        return sb.toString();
    }

    // ========== Default Visitor Behavior ==========

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
     * Aggregates results from multiple child visits.
     *
     * <p>This method concatenates non-empty results from visiting children.
     *
     * @param aggregate the accumulated result so far
     * @param nextResult the result from visiting the next child
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
