/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.ppl.formatter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Application for formatting PPL queries contained within JSON dataset files.
 *
 * <p>This application processes JSON files that contain PPL queries in the "tasks" array under the
 * "ppl" key. It formats each PPL query using {@link PPLQueryFormatter} and outputs the result while
 * preserving the rest of the JSON structure.
 *
 * <p>Usage: java DatasetQueryFormatter &lt;input_path&gt; &lt;output_path&gt;
 *
 * <p>The input path can be:
 *
 * <ul>
 *   <li>A single JSON file - output_path should be a JSON file path
 *   <li>A directory containing JSON files (recursively) - output_path should be a directory path
 * </ul>
 */
public class DatasetQueryFormatter {

  private static final String JSON_EXTENSION = ".json";
  private static final String TASKS_KEY = "tasks";
  private static final String PPL_KEY = "ppl";

  private final PPLQueryFormatter queryFormatter;

  public DatasetQueryFormatter() {
    this.queryFormatter = new PPLQueryFormatter();
  }

  /**
   * Main entry point for the application.
   *
   * @param args command line arguments: &lt;input_path&gt; &lt;output_path&gt;
   */
  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println("Usage: DatasetQueryFormatter <input_path> <output_path>");
      System.err.println("  input_path  - path to a JSON file or directory containing JSON files");
      System.err.println("  output_path - path to output JSON file or directory");
      System.exit(1);
    }

    String inputPath = args[0];
    String outputPath = args[1];

    DatasetQueryFormatter formatter = new DatasetQueryFormatter();
    try {
      formatter.process(inputPath, outputPath);
      System.out.println("Formatting completed successfully.");
    } catch (IOException e) {
      System.err.println("Error processing files: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Processes the input path and writes formatted output to the output path.
   *
   * @param inputPath path to input JSON file or directory
   * @param outputPath path to output JSON file or directory
   * @throws IOException if an I/O error occurs
   */
  public void process(String inputPath, String outputPath) throws IOException {
    Path input = Paths.get(inputPath);
    Path output = Paths.get(outputPath);

    if (!Files.exists(input)) {
      throw new IOException("Input path does not exist: " + inputPath);
    }

    if (Files.isRegularFile(input)) {
      processFile(input, output);
    } else if (Files.isDirectory(input)) {
      processDirectory(input, output);
    } else {
      throw new IOException("Input path is neither a file nor a directory: " + inputPath);
    }
  }

  /**
   * Processes a single JSON file.
   *
   * @param inputFile path to the input JSON file
   * @param outputFile path to the output JSON file
   * @throws IOException if an I/O error occurs
   */
  private void processFile(Path inputFile, Path outputFile) throws IOException {
    System.out.println("Processing file: " + inputFile);

    String content = Files.readString(inputFile);
    JSONObject json = new JSONObject(content);

    formatPplQueries(json);

    Path parentDir = outputFile.getParent();
    if (parentDir != null && !Files.exists(parentDir)) {
      Files.createDirectories(parentDir);
    }

    Files.writeString(outputFile, json.toString(4));
    System.out.println("  -> Written to: " + outputFile);
  }

  /**
   * Processes a directory containing JSON files recursively.
   *
   * @param inputDir path to the input directory
   * @param outputDir path to the output directory
   * @throws IOException if an I/O error occurs
   */
  private void processDirectory(Path inputDir, Path outputDir) throws IOException {
    System.out.println("Processing directory: " + inputDir);

    List<Path> jsonFiles = findJsonFiles(inputDir);

    if (jsonFiles.isEmpty()) {
      System.out.println("No JSON files found in directory: " + inputDir);
      return;
    }

    System.out.println("Found " + jsonFiles.size() + " JSON file(s)");

    for (Path jsonFile : jsonFiles) {
      Path relativePath = inputDir.relativize(jsonFile);
      Path outputFile = outputDir.resolve(relativePath);

      processFile(jsonFile, outputFile);
    }
  }

  /**
   * Finds all JSON files in a directory recursively.
   *
   * @param directory the directory to search
   * @return list of paths to JSON files
   * @throws IOException if an I/O error occurs
   */
  private List<Path> findJsonFiles(Path directory) throws IOException {
    List<Path> jsonFiles = new ArrayList<>();

    Files.walkFileTree(
        directory,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (file.toString().toLowerCase().endsWith(JSON_EXTENSION)) {
              jsonFiles.add(file);
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) {
            System.err.println("Warning: Failed to access file: " + file);
            return FileVisitResult.CONTINUE;
          }
        });

    return jsonFiles;
  }

  /**
   * Formats PPL queries in the JSON object's tasks array.
   *
   * @param json the JSON object containing tasks with PPL queries
   */
  private void formatPplQueries(JSONObject json) {
    if (!json.has(TASKS_KEY)) {
      return;
    }

    Object tasksObj = json.get(TASKS_KEY);
    if (!(tasksObj instanceof JSONArray)) {
      return;
    }

    JSONArray tasks = (JSONArray) tasksObj;

    for (int i = 0; i < tasks.length(); i++) {
      Object taskObj = tasks.get(i);
      if (!(taskObj instanceof JSONObject)) {
        continue;
      }

      JSONObject task = (JSONObject) taskObj;

      if (!task.has(PPL_KEY)) {
        continue;
      }

      Object pplObj = task.get(PPL_KEY);
      if (!(pplObj instanceof JSONArray)) {
        continue;
      }

      JSONArray pplQueries = (JSONArray) pplObj;
      JSONArray formattedQueries = new JSONArray();

      for (int j = 0; j < pplQueries.length(); j++) {
        Object queryObj = pplQueries.get(j);
        if (queryObj instanceof String) {
          String query = (String) queryObj;
          String formatted = queryFormatter.format(query);
          formattedQueries.put(formatted);
        } else {
          formattedQueries.put(queryObj);
        }
      }

      task.put(PPL_KEY, formattedQueries);
    }
  }
}
