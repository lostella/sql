/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Error listener for PPL query parsing during formatting.
 *
 * <p>Tracks whether any syntax errors occurred during parsing, allowing the formatter
 * to return the original query unchanged when parsing fails.
 */
public class FormatterErrorListener extends BaseErrorListener {

    private boolean hasErrors = false;

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e
    ) {
        hasErrors = true;
    }

    /**
     * Checks whether any syntax errors were encountered.
     *
     * @return true if there are one or more syntax errors
     */
    public boolean hasErrors() {
        return hasErrors;
    }
}
