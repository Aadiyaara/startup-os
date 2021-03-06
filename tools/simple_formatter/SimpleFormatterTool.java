/*
 * Copyright 2018 The StartupOS Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.startupos.tools.simple_formatter;

import com.google.common.collect.ImmutableMap;
import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.startupos.common.flags.Flag;
import com.google.startupos.common.flags.FlagDesc;
import com.google.startupos.common.flags.Flags;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class SimpleFormatterTool {

  static String readFile(Path path) throws IOException {
    return String.join(System.lineSeparator(), Files.readAllLines(path));
  }

  static void execute(String cmd) throws IOException {
    Runtime.getRuntime().exec(cmd);
  }

  interface BaseFormatter {
    void format(Path path) throws IOException;
  }

  static class JavaFormatter implements BaseFormatter {

    static Formatter javaFormatter = new Formatter();

    static JavaFormatter init() {
      return new JavaFormatter();
    }

    @Override
    public void format(Path path) throws IOException {
      try {
        File tempFile = File.createTempFile("prefix", "suffix");
        Path tempFilePath = Paths.get(tempFile.getAbsolutePath());
        // write formatted text to temporary file first
        Files.write(
            tempFilePath, Collections.singleton(javaFormatter.formatSource(readFile(path))));
        // move temporary file to original
        Files.move(tempFilePath, path, StandardCopyOption.REPLACE_EXISTING);
      } catch (FormatterException e) {
        e.printStackTrace();
      }
    }
  }

  static class PythonFormatter implements BaseFormatter {

    static PythonFormatter init() {
      return new PythonFormatter();
    }

    @Override
    public void format(Path path) throws IOException {
      execute("yapf -i " + path.toAbsolutePath().toString());
    }
  }

  static class ClangFormatter implements BaseFormatter {

    static ClangFormatter init() {
      return new ClangFormatter();
    }

    @Override
    public void format(Path path) throws IOException {
      execute("clang-format -i " + path.toAbsolutePath().toString());
    }
  }

  @FlagDesc(name = "path", description = "Format files in this path, recursively")
  private static final Flag<String> path = Flag.create(".");

  @FlagDesc(name = "dry_run", description = "Don't actually do anything")
  private static final Flag<Boolean> dryRun = Flag.create(false);

  @FlagDesc(name = "java", description = "Format java files")
  private static final Flag<Boolean> java = Flag.create(false);

  @FlagDesc(name = "proto", description = "Format proto files")
  private static final Flag<Boolean> proto = Flag.create(false);

  @FlagDesc(name = "python", description = "Format python files")
  private static final Flag<Boolean> python = Flag.create(false);

  @FlagDesc(name = "cpp", description = "Format C++ (*.cc) files")
  private static final Flag<Boolean> cpp = Flag.create(false);

  @FlagDesc(name = "ignore_directories", description = "Ignored directories, split by comma")
  private static final Flag<String> ignoreDirectories = Flag.create("");

  private static boolean isJava(Path file) {
    return getExtension(file).equals(".java");
  }

  private static boolean isPython(Path file) {
    return getExtension(file).equals(".py");
  }

  private static boolean isProto(Path file) {
    return getExtension(file).equals(".proto");
  }

  private static boolean isCpp(Path file) {
    return getExtension(file).equals(".cc");
  }

  private static String getExtension(Path file) {
    String filename = file.getFileName().toString();
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex == -1) {
      return "";
    }
    return filename.substring(dotIndex);
  }

  private static boolean shouldFormatFile(Path file, Set<Path> ignoredDirectories) {
    boolean formatByExtension =
        ((isJava(file) && java.get())
            || (isProto(file) && proto.get())
            || (isPython(file) && python.get())
            || (isCpp(file) && cpp.get()));
    boolean inIgnoredDirectory =
        ignoredDirectories.stream().anyMatch(ignoredDir -> file.startsWith(ignoredDir));
    return formatByExtension && !inIgnoredDirectory;
  }

  private static Map<String, BaseFormatter> formatters =
      ImmutableMap.of(
          ".java", JavaFormatter.init(),
          ".proto", ClangFormatter.init(),
          ".py", PythonFormatter.init(),
          ".cc", ClangFormatter.init());

  public static void main(String[] args) {
    Flags.parse(args, SimpleFormatterTool.class.getPackage());

    Set<Path> ignoredDirectories =
        Stream.of(ignoreDirectories.get().split(","))
            .map(path -> Paths.get(path))
            .collect(Collectors.toSet());

    try (Stream<Path> stream = Files.walk(Paths.get(path.get()).toAbsolutePath())) {
      Iterable<Path> paths = () -> stream.filter(Files::isRegularFile).iterator();

      for (Path path : paths) {
        try {
          if (shouldFormatFile(path, ignoredDirectories)) {
            if (dryRun.get()) {
              System.out.println(
                  String.format("Intending to format: %s", path.toAbsolutePath().toString()));
            } else {
              System.out.println(String.format("Formatting: %s", path.toAbsolutePath().toString()));
              formatters.get(getExtension(path)).format(path);
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}

