/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for DatasetQueryFormatter.
 *
 * <p>These tests verify:
 *
 * <ul>
 *   <li>Single JSON file processing
 *   <li>Directory processing with recursive JSON file discovery
 *   <li>PPL query formatting within tasks array
 *   <li>Preservation of non-PPL JSON structure
 *   <li>Error handling for invalid inputs
 *   <li>Edge cases (empty files, missing keys, etc.)
 * </ul>
 */
public class DatasetQueryFormatterTest {

  private DatasetQueryFormatter formatter;
  private Path tempDir;

  @Before
  public void setUp() throws IOException {
    formatter = new DatasetQueryFormatter();
    tempDir = Files.createTempDirectory("dataset_formatter_test");
  }

  @After
  public void tearDown() throws IOException {
    if (tempDir != null && Files.exists(tempDir)) {
      Files.walk(tempDir)
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (IOException e) {
                  // Ignore cleanup errors
                }
              });
    }
  }

  @Test
  public void testProcessSingleFile() throws IOException {
    // Create input JSON
    JSONObject input = createSampleDataset();
    Path inputFile = tempDir.resolve("input.json");
    Path outputFile = tempDir.resolve("output.json");
    Files.writeString(inputFile, input.toString(4));

    // Process the file
    formatter.process(inputFile.toString(), outputFile.toString());

    // Verify output exists
    assertTrue("Output file should exist", Files.exists(outputFile));

    // Verify JSON structure is preserved
    JSONObject output = new JSONObject(Files.readString(outputFile));
    assertTrue("Output should have tasks", output.has("tasks"));
    assertTrue("Output should have mappings", output.has("mappings"));

    // Verify PPL queries were processed
    JSONArray tasks = output.getJSONArray("tasks");
    assertEquals("Should have 2 tasks", 2, tasks.length());
  }

  @Test
  public void testProcessDirectory() throws IOException {
    // Create directory structure
    Path subDir = tempDir.resolve("subdir");
    Files.createDirectories(subDir);

    JSONObject input1 = createSampleDataset();
    JSONObject input2 = createSampleDataset();

    Path file1 = tempDir.resolve("file1.json");
    Path file2 = subDir.resolve("file2.json");

    Files.writeString(file1, input1.toString(4));
    Files.writeString(file2, input2.toString(4));

    // Process directory
    Path outputDir = tempDir.resolve("output");
    formatter.process(tempDir.toString(), outputDir.toString());

    // Verify output structure
    assertTrue("Output dir should exist", Files.exists(outputDir));
    assertTrue("Output file1 should exist", Files.exists(outputDir.resolve("file1.json")));
    assertTrue("Output file2 should exist", Files.exists(outputDir.resolve("subdir/file2.json")));
  }

  @Test
  public void testPplQueryFormatting() throws IOException {
    JSONObject input = new JSONObject();
    JSONArray tasks = new JSONArray();

    JSONObject task = new JSONObject();
    task.put("question", "Test query");
    JSONArray pplQueries = new JSONArray();
    // Use uppercase keywords to verify formatting
    pplQueries.put("SOURCE=myindex | STATS COUNT() BY field");
    task.put("ppl", pplQueries);
    tasks.put(task);
    input.put("tasks", tasks);

    Path inputFile = tempDir.resolve("input.json");
    Path outputFile = tempDir.resolve("output.json");
    Files.writeString(inputFile, input.toString(4));

    formatter.process(inputFile.toString(), outputFile.toString());

    JSONObject output = new JSONObject(Files.readString(outputFile));
    JSONArray outputTasks = output.getJSONArray("tasks");
    JSONArray outputPpl = outputTasks.getJSONObject(0).getJSONArray("ppl");

    String formattedQuery = outputPpl.getString(0);
    // Verify keywords are lowercased
    assertTrue(
        "Keywords should be lowercase",
        formattedQuery.contains("source=") || formattedQuery.contains("stats"));
  }

  @Test
  public void testPreservesNonPplData() throws IOException {
    JSONObject input = new JSONObject();
    JSONArray tasks = new JSONArray();

    JSONObject task = new JSONObject();
    task.put("opensearch_version", ">=2.19");
    task.put("question", "Test question");
    task.put("index_patterns", new JSONArray().put("my-index*"));
    task.put("ppl", new JSONArray().put("source=myindex"));
    tasks.put(task);

    input.put("tasks", tasks);
    input.put("mappings", new JSONObject().put("my-index", new JSONObject()));
    input.put("custom_field", "custom_value");

    Path inputFile = tempDir.resolve("input.json");
    Path outputFile = tempDir.resolve("output.json");
    Files.writeString(inputFile, input.toString(4));

    formatter.process(inputFile.toString(), outputFile.toString());

    JSONObject output = new JSONObject(Files.readString(outputFile));

    // Verify non-PPL data is preserved
    assertTrue("Should preserve mappings", output.has("mappings"));
    assertTrue("Should preserve custom_field", output.has("custom_field"));
    assertEquals("custom_value", output.getString("custom_field"));

    JSONObject outputTask = output.getJSONArray("tasks").getJSONObject(0);
    assertEquals(">=2.19", outputTask.getString("opensearch_version"));
    assertEquals("Test question", outputTask.getString("question"));
  }

  @Test
  public void testHandlesJsonWithoutTasks() throws IOException {
    JSONObject input = new JSONObject();
    input.put("mappings", new JSONObject());
    input.put("other_data", "value");

    Path inputFile = tempDir.resolve("input.json");
    Path outputFile = tempDir.resolve("output.json");
    Files.writeString(inputFile, input.toString(4));

    formatter.process(inputFile.toString(), outputFile.toString());

    JSONObject output = new JSONObject(Files.readString(outputFile));
    assertTrue("Should preserve mappings", output.has("mappings"));
    assertTrue("Should preserve other_data", output.has("other_data"));
  }

  @Test
  public void testHandlesTaskWithoutPpl() throws IOException {
    JSONObject input = new JSONObject();
    JSONArray tasks = new JSONArray();

    JSONObject task = new JSONObject();
    task.put("question", "A question without PPL");
    tasks.put(task);
    input.put("tasks", tasks);

    Path inputFile = tempDir.resolve("input.json");
    Path outputFile = tempDir.resolve("output.json");
    Files.writeString(inputFile, input.toString(4));

    formatter.process(inputFile.toString(), outputFile.toString());

    JSONObject output = new JSONObject(Files.readString(outputFile));
    JSONObject outputTask = output.getJSONArray("tasks").getJSONObject(0);
    assertEquals("A question without PPL", outputTask.getString("question"));
    assertFalse("Should not add ppl key", outputTask.has("ppl"));
  }

  @Test
  public void testHandlesMultiplePplQueries() throws IOException {
    JSONObject input = new JSONObject();
    JSONArray tasks = new JSONArray();

    JSONObject task = new JSONObject();
    JSONArray pplQueries = new JSONArray();
    pplQueries.put("source=index1");
    pplQueries.put("source=index2 | stats count()");
    pplQueries.put("source=index3 | where field > 10");
    task.put("ppl", pplQueries);
    tasks.put(task);
    input.put("tasks", tasks);

    Path inputFile = tempDir.resolve("input.json");
    Path outputFile = tempDir.resolve("output.json");
    Files.writeString(inputFile, input.toString(4));

    formatter.process(inputFile.toString(), outputFile.toString());

    JSONObject output = new JSONObject(Files.readString(outputFile));
    JSONArray outputPpl = output.getJSONArray("tasks").getJSONObject(0).getJSONArray("ppl");
    assertEquals("Should have 3 PPL queries", 3, outputPpl.length());
  }

  @Test
  public void testHandlesInvalidPplQuery() throws IOException {
    JSONObject input = new JSONObject();
    JSONArray tasks = new JSONArray();

    JSONObject task = new JSONObject();
    JSONArray pplQueries = new JSONArray();
    // Invalid query should be preserved as-is
    pplQueries.put("I am unable to convert the question into a valid query");
    task.put("ppl", pplQueries);
    tasks.put(task);
    input.put("tasks", tasks);

    Path inputFile = tempDir.resolve("input.json");
    Path outputFile = tempDir.resolve("output.json");
    Files.writeString(inputFile, input.toString(4));

    formatter.process(inputFile.toString(), outputFile.toString());

    JSONObject output = new JSONObject(Files.readString(outputFile));
    String outputQuery =
        output.getJSONArray("tasks").getJSONObject(0).getJSONArray("ppl").getString(0);
    assertEquals(
        "Invalid query should be preserved",
        "I am unable to convert the question into a valid query",
        outputQuery);
  }

  @Test(expected = IOException.class)
  public void testThrowsForNonExistentInput() throws IOException {
    formatter.process("/non/existent/path.json", tempDir.resolve("output.json").toString());
  }

  @Test
  public void testCreatesOutputParentDirectories() throws IOException {
    JSONObject input = createSampleDataset();
    Path inputFile = tempDir.resolve("input.json");
    Path outputFile = tempDir.resolve("nested/deep/output.json");
    Files.writeString(inputFile, input.toString(4));

    formatter.process(inputFile.toString(), outputFile.toString());

    assertTrue("Output file should exist in nested directory", Files.exists(outputFile));
  }

  @Test
  public void testIgnoresNonJsonFiles() throws IOException {
    // Create a mix of JSON and non-JSON files
    Files.writeString(tempDir.resolve("data.json"), createSampleDataset().toString(4));
    Files.writeString(tempDir.resolve("readme.txt"), "This is not JSON");
    Files.writeString(tempDir.resolve("config.yaml"), "key: value");

    Path outputDir = tempDir.resolve("output");
    formatter.process(tempDir.toString(), outputDir.toString());

    // Only JSON file should be processed
    assertTrue("JSON file should be processed", Files.exists(outputDir.resolve("data.json")));
    assertFalse("TXT file should not be copied", Files.exists(outputDir.resolve("readme.txt")));
    assertFalse("YAML file should not be copied", Files.exists(outputDir.resolve("config.yaml")));
  }

  @Test
  public void testHandlesEmptyDirectory() throws IOException {
    Path emptyDir = tempDir.resolve("empty");
    Files.createDirectories(emptyDir);

    Path outputDir = tempDir.resolve("output");

    // Should not throw
    formatter.process(emptyDir.toString(), outputDir.toString());
  }

  @Test
  public void testHandlesJsonCaseInsensitiveExtension() throws IOException {
    JSONObject input = createSampleDataset();

    // Create files with different case extensions
    Files.writeString(tempDir.resolve("file1.JSON"), input.toString(4));
    Files.writeString(tempDir.resolve("file2.Json"), input.toString(4));

    Path outputDir = tempDir.resolve("output");
    formatter.process(tempDir.toString(), outputDir.toString());

    assertTrue(
        "Uppercase .JSON should be processed", Files.exists(outputDir.resolve("file1.JSON")));
    assertTrue(
        "Mixed case .Json should be processed", Files.exists(outputDir.resolve("file2.Json")));
  }

  /** Creates a sample dataset JSON object for testing. */
  private JSONObject createSampleDataset() {
    JSONObject dataset = new JSONObject();

    JSONArray tasks = new JSONArray();

    JSONObject task1 = new JSONObject();
    task1.put("opensearch_version", ">=2.19");
    task1.put("index_patterns", new JSONArray().put("my-index*"));
    task1.put("question", "Test question 1");
    task1.put("ppl", new JSONArray().put("source=my-index*"));
    tasks.put(task1);

    JSONObject task2 = new JSONObject();
    task2.put("opensearch_version", ">=3.1");
    task2.put("index_patterns", new JSONArray().put("my-index-2"));
    task2.put("question", "Test question 2");
    task2.put("ppl", new JSONArray().put("source=my-index-2 | stats count()"));
    tasks.put(task2);

    dataset.put("tasks", tasks);

    JSONObject mappings = new JSONObject();
    mappings.put(
        "my-index",
        new JSONObject()
            .put(
                "mappings",
                new JSONObject()
                    .put(
                        "properties",
                        new JSONObject().put("field", new JSONObject().put("type", "keyword")))));
    dataset.put("mappings", mappings);

    return dataset;
  }
}
