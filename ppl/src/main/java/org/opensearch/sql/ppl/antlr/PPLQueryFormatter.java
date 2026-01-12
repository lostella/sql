/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.antlr;

import org.antlr.v4.runtime.tree.ParseTree;

/** PPL Query Formatter that formats PPL queries with consistent style. */
public class PPLQueryFormatter {
  
  /** Format a PPL query string. */
  public String format(String query) {
    // Simple formatter that normalizes spacing
    String formatted = query
        .toLowerCase()                     // Convert to lowercase
        .replaceAll("\\s*\\|\\s*", " | ")  // Normalize pipe spacing
        .replaceAll("\\s*,\\s*", ", ")     // Normalize comma spacing
        .replaceAll("\\s*>\\s*", " > ")    // Normalize greater than spacing
        .replaceAll("\\s*<\\s*", " < ")    // Normalize less than spacing
        .replaceAll("^search\\s+", "")     // Remove search prefix
        .replaceAll("\\s+", " ")           // Normalize whitespace
        .trim();
    
    // Fix source= to not have spaces around equals
    formatted = formatted.replaceAll("source\\s*=\\s*", "source=");
    
    // Add spaces around other equals signs (but not source=)
    formatted = formatted.replaceAll("(?<!source)\\s*=\\s*", " = ");
    
    return formatted;
  }
}
