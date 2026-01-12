/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter.visitors;

import static org.opensearch.sql.ppl.formatter.FormatterConstants.*;
import static org.opensearch.sql.ppl.formatter.FormatterUtils.*;

import java.util.List;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.opensearch.sql.ppl.antlr.parser.OpenSearchPPLParser;

/**
 * Visitor methods for formatting field expressions and qualified names in PPL queries.
 *
 * <p>This class handles the formatting of:
 * <ul>
 *   <li>Field expressions (simple field references)</li>
 *   <li>Wildcard field expressions (fields with wildcards like field*)</li>
 *   <li>Field lists (comma-separated field names)</li>
 *   <li>Qualified names (dot-separated identifiers like table.field)</li>
 *   <li>Table qualified names (including cross-cluster references)</li>
 *   <li>Wildcards within field names</li>
 *   <li>Identifiers (preserving original case)</li>
 * </ul>
 *
 * <p>Field names and identifiers preserve their original case since they may be
 * case-sensitive in the underlying data store. Lists are formatted with consistent
 * comma spacing.
 */
public class FieldVisitors extends BaseFormattingVisitor {

    /**
     * Creates a new field visitor.
     *
     * @param tokenStream the token stream from the lexer
     */
    public FieldVisitors(CommonTokenStream tokenStream) {
        super(tokenStream);
    }

    // ========== Field Expressions ==========

    /**
     * Visits a field expression.
     *
     * <p>A field expression is a reference to a field by its qualified name.
     * The field name's original case is preserved.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code status} -> {@code status}</li>
     *   <li>{@code response.code} -> {@code response.code}</li>
     * </ul>
     *
     * @param ctx the field expression context
     * @return the formatted field expression
     */
    @Override
    public String visitFieldExpression(OpenSearchPPLParser.FieldExpressionContext ctx) {
        if (ctx == null || ctx.qualifiedName() == null) {
            return EMPTY;
        }
        return safeVisit(ctx.qualifiedName());
    }

    /**
     * Visits a wildcard field expression.
     *
     * <p>Wildcard field expressions can contain wildcards (*) for pattern matching.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code status*} -> {@code status*}</li>
     *   <li>{@code *.name} -> {@code *.name}</li>
     * </ul>
     *
     * @param ctx the wildcard field expression context
     * @return the formatted wildcard field expression
     */
    @Override
    public String visitWcFieldExpression(OpenSearchPPLParser.WcFieldExpressionContext ctx) {
        if (ctx == null || ctx.wcQualifiedName() == null) {
            return EMPTY;
        }
        return safeVisit(ctx.wcQualifiedName());
    }

    /**
     * Visits a select field expression (used in fields command with optional wildcards).
     *
     * <p>Handles both plain wildcard (*) for all fields and qualified names with wildcards.
     * When a field starts with a prefix wildcard (e.g., *name), adds a space after the *.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code *} -> {@code *}</li>
     *   <li>{@code field*} -> {@code field*}</li>
     *   <li>{@code *name} -> {@code * name}</li>
     * </ul>
     *
     * @param ctx the select field expression context
     * @return the formatted select field expression
     */
    @Override
    public String visitSelectFieldExpression(
            OpenSearchPPLParser.SelectFieldExpressionContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        // Handle standalone * (select all fields)
        if (ctx.STAR() != null) {
            return OP_MULTIPLY;
        }

        String fieldName = safeVisit(ctx.wcQualifiedName());

        // Handle prefix wildcards like "*name" - add space after leading *
        if (fieldName.startsWith(OP_MULTIPLY)
                && fieldName.length() > 1
                && fieldName.charAt(1) != ' ') {
            return OP_MULTIPLY + SPACE + fieldName.substring(1);
        }

        return fieldName;
    }

    // ========== Field Lists ==========

    /**
     * Visits a field list (comma-separated field expressions).
     *
     * <p>Formats the list with consistent comma and space separation.
     *
     * <p>Example: {@code field1,field2,field3} -> {@code field1, field2, field3}
     *
     * @param ctx the field list context
     * @return the formatted comma-separated field list
     */
    @Override
    public String visitFieldList(OpenSearchPPLParser.FieldListContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        List<OpenSearchPPLParser.FieldExpressionContext> fields = ctx.fieldExpression();
        if (fields == null || fields.isEmpty()) {
            return EMPTY;
        }
        return visitAndJoinWithComma(fields);
    }

    /**
     * Visits a wildcard field list (used in fields command).
     *
     * <p>Handles both comma-separated and space-separated field lists.
     * The output uses comma separation for consistency.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code field1,field2} -> {@code field1, field2}</li>
     *   <li>{@code field1 field2} -> {@code field1 field2}</li>
     * </ul>
     *
     * @param ctx the wildcard field list context
     * @return the formatted field list
     */
    @Override
    public String visitWcFieldList(OpenSearchPPLParser.WcFieldListContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        List<OpenSearchPPLParser.SelectFieldExpressionContext> fields = ctx.selectFieldExpression();
        if (fields == null || fields.isEmpty()) {
            return EMPTY;
        }

        // Check for COMMA tokens between fields
        List<TerminalNode> commas = ctx.COMMA();
        StringBuilder sb = newBuilder();

        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                // Use comma if there was one in original, otherwise use space
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

    // ========== Qualified Names ==========

    /**
     * Visits a qualified name (dot-separated identifiers).
     *
     * <p>Qualified names represent field paths like table.field or nested.field.name.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code field} -> {@code field}</li>
     *   <li>{@code table.field} -> {@code table.field}</li>
     *   <li>{@code a.b.c} -> {@code a.b.c}</li>
     * </ul>
     *
     * @param ctx the qualified name context
     * @return the formatted dot-separated qualified name
     */
    @Override
    public String visitIdentsAsQualifiedName(
            OpenSearchPPLParser.IdentsAsQualifiedNameContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        List<OpenSearchPPLParser.IdentContext> idents = ctx.ident();
        if (idents == null || idents.isEmpty()) {
            return EMPTY;
        }
        return visitAndJoinWithDot(idents);
    }

    /**
     * Visits a wildcard qualified name (dot-separated with possible wildcards).
     *
     * <p>Similar to qualified names but allows wildcards in the path.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code field*} -> {@code field*}</li>
     *   <li>{@code table.field*} -> {@code table.field*}</li>
     * </ul>
     *
     * @param ctx the wildcard qualified name context
     * @return the formatted wildcard qualified name
     */
    @Override
    public String visitIdentsAsWildcardQualifiedName(
            OpenSearchPPLParser.IdentsAsWildcardQualifiedNameContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        List<OpenSearchPPLParser.WildcardContext> wildcards = ctx.wildcard();
        if (wildcards == null || wildcards.isEmpty()) {
            return EMPTY;
        }
        return visitAndJoinWithDot(wildcards);
    }

    /**
     * Visits a table qualified name (may include cluster prefix).
     *
     * <p>Table qualified names can include cross-cluster references in the format
     * cluster:index.field.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code logs} -> {@code logs}</li>
     *   <li>{@code cluster:logs} -> {@code cluster:logs}</li>
     *   <li>{@code cluster:logs.field} -> {@code cluster:logs.field}</li>
     * </ul>
     *
     * @param ctx the table qualified name context
     * @return the formatted table qualified name
     */
    @Override
    public String visitIdentsAsTableQualifiedName(
            OpenSearchPPLParser.IdentsAsTableQualifiedNameContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();

        // Handle tableIdent which may contain CLUSTER prefix
        if (ctx.tableIdent() != null) {
            sb.append(safeVisit(ctx.tableIdent()));
        }

        // Handle additional ident parts separated by DOT
        List<OpenSearchPPLParser.IdentContext> idents = ctx.ident();
        if (idents != null) {
            for (OpenSearchPPLParser.IdentContext ident : idents) {
                sb.append(DOT_SEPARATOR);
                sb.append(safeVisit(ident));
            }
        }

        return sb.toString();
    }

    // ========== Table Identifiers ==========

    /**
     * Visits a table identifier (may include cluster prefix).
     *
     * <p>Table identifiers can optionally include a cluster prefix in the format
     * "cluster:" followed by the identifier.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code logs} -> {@code logs}</li>
     *   <li>{@code cluster:logs} -> {@code cluster:logs}</li>
     * </ul>
     *
     * @param ctx the table ident context
     * @return the formatted table identifier
     */
    @Override
    public String visitTableIdent(OpenSearchPPLParser.TableIdentContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();

        // CLUSTER token already includes the colon (e.g., "cluster:")
        if (ctx.CLUSTER() != null) {
            sb.append(ctx.CLUSTER().getText());
        }

        sb.append(safeVisit(ctx.ident()));

        return sb.toString();
    }

    // ========== Wildcards ==========

    /**
     * Visits a wildcard pattern within a qualified name.
     *
     * <p>Wildcards can contain a mix of identifiers and * characters.
     * All parts are concatenated to form the complete pattern.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code field*} -> {@code field*}</li>
     *   <li>{@code *field} -> {@code *field}</li>
     *   <li>{@code f*ld} -> {@code f*ld}</li>
     * </ul>
     *
     * @param ctx the wildcard context
     * @return the formatted wildcard pattern
     */
    @Override
    public String visitWildcard(OpenSearchPPLParser.WildcardContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }

        StringBuilder sb = newBuilder();
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

    // ========== Identifiers ==========

    /**
     * Visits an identifier.
     *
     * <p>Identifiers are preserved exactly as written in the original query,
     * including their original case. This is important because field names
     * may be case-sensitive in the underlying data store.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code status} -> {@code status}</li>
     *   <li>{@code Status} -> {@code Status}</li>
     *   <li>{@code `quoted-field`} -> {@code `quoted-field`}</li>
     * </ul>
     *
     * @param ctx the ident context
     * @return the original identifier text
     */
    @Override
    public String visitIdent(OpenSearchPPLParser.IdentContext ctx) {
        if (ctx == null) {
            return EMPTY;
        }
        return preserveIdentifier(getOriginalText(ctx));
    }
}
