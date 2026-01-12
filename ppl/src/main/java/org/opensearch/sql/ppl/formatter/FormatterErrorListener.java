/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Custom error listener for PPL query parsing during formatting.
 *
 * <p>This listener collects syntax errors encountered during parsing, allowing the formatter to
 * handle malformed queries gracefully rather than failing silently or throwing exceptions.
 *
 * <p>Usage:
 * <pre>{@code
 * FormatterErrorListener errorListener = new FormatterErrorListener();
 * lexer.removeErrorListeners();
 * lexer.addErrorListener(errorListener);
 * parser.removeErrorListeners();
 * parser.addErrorListener(errorListener);
 *
 * // After parsing
 * if (errorListener.hasErrors()) {
 *     // Handle errors or return original query
 * }
 * }</pre>
 */
public class FormatterErrorListener extends BaseErrorListener {

    /** List to collect all syntax errors encountered during parsing. */
    private final List<SyntaxError> errors = new ArrayList<>();

    /**
     * Called when a syntax error is encountered during parsing.
     *
     * @param recognizer the recognizer that encountered the error
     * @param offendingSymbol the token that caused the error (may be null)
     * @param line the line number where the error occurred (1-based)
     * @param charPositionInLine the character position within the line (0-based)
     * @param msg the error message
     * @param e the recognition exception (may be null)
     */
    @Override
    public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e) {

        errors.add(new SyntaxError(line, charPositionInLine, msg, offendingSymbol));
    }

    /**
     * Checks whether any syntax errors were encountered.
     *
     * @return true if there are one or more syntax errors
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Returns the number of syntax errors encountered.
     *
     * @return the error count
     */
    public int getErrorCount() {
        return errors.size();
    }

    /**
     * Returns an unmodifiable list of all syntax errors encountered.
     *
     * @return the list of syntax errors
     */
    public List<SyntaxError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Returns a formatted string containing all error messages.
     *
     * @return a newline-separated string of error messages
     */
    public String getErrorMessages() {
        if (errors.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(errors.get(i).toString());
        }
        return sb.toString();
    }

    /**
     * Clears all collected errors.
     *
     * <p>This can be used to reuse the error listener for multiple parsing operations.
     */
    public void clear() {
        errors.clear();
    }

    /**
     * Represents a single syntax error encountered during parsing.
     */
    public static class SyntaxError {
        private final int line;
        private final int charPositionInLine;
        private final String message;
        private final Object offendingSymbol;

        /**
         * Creates a new syntax error.
         *
         * @param line the line number (1-based)
         * @param charPositionInLine the character position within the line (0-based)
         * @param message the error message
         * @param offendingSymbol the token that caused the error (may be null)
         */
        public SyntaxError(int line, int charPositionInLine, String message, Object offendingSymbol) {
            this.line = line;
            this.charPositionInLine = charPositionInLine;
            this.message = message;
            this.offendingSymbol = offendingSymbol;
        }

        /**
         * Returns the line number where the error occurred.
         *
         * @return the line number (1-based)
         */
        public int getLine() {
            return line;
        }

        /**
         * Returns the character position within the line where the error occurred.
         *
         * @return the character position (0-based)
         */
        public int getCharPositionInLine() {
            return charPositionInLine;
        }

        /**
         * Returns the error message.
         *
         * @return the error message
         */
        public String getMessage() {
            return message;
        }

        /**
         * Returns the token that caused the error.
         *
         * @return the offending symbol, or null if not available
         */
        public Object getOffendingSymbol() {
            return offendingSymbol;
        }

        /**
         * Returns a formatted string representation of this error.
         *
         * @return a string in the format "line X:Y - message"
         */
        @Override
        public String toString() {
            return String.format("line %d:%d - %s", line, charPositionInLine, message);
        }
    }
}
