package com.autogen.generator;

import com.autogen.indexer.MethodFootprint;
import com.autogen.indexer.PageIndex;
import com.autogen.matcher.Plan;
import com.autogen.recorder.Session;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads a saved plan and writes the test class into the "generated" folder.
 *
 * <pre>
 *   mvn compile exec:java "-Dexec.mainClass=com.autogen.generator.GenerateMain"
 *       "-Dexec.args=path/to/pages plans/session-xxx.json [ClassName]"
 * </pre>
 */
public class GenerateMain {

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.err.println("Usage: <pages folder> <plan.json> [TestClassName]");
            return;
        }

        Path pagesFolder = Path.of(args[0]);
        Path planPath = Path.of(args[1]);
        String className = args.length > 2 ? args[2] : "RecordedFlowTest";

        ObjectMapper json = new ObjectMapper();
        Plan plan = json.readValue(planPath.toFile(), Plan.class);
        Session session = json.readValue(Path.of("sessions").resolve(plan.sessionFile()).toFile(), Session.class);

        PageIndex index = PageIndex.build(pagesFolder);
        Set<String> pageClasses = new HashSet<>();
        for (MethodFootprint footprint : index.footprints()) {
            pageClasses.add(footprint.className());
        }

        if (pageClasses.isEmpty()) {
            System.err.println("No page classes found in " + pagesFolder + ". Check the pages folder path.");
            return;
        }

        GeneratorConfig config = GeneratorConfig.load(Path.of("generator.properties"));
        TestClassGenerator generator = new TestClassGenerator(pageClasses, config, index, asker());
        String source = generator.generate(plan, session, className);

        Path folder = Path.of("generated");
        Files.createDirectories(folder);
        Path file = folder.resolve(className + ".java");
        Files.writeString(file, source);

        // the new fields and methods, one text file per page class
        Path additionsFolder = folder.resolve("additions");
        Files.createDirectories(additionsFolder);
        for (var addition : generator.additions().entrySet()) {
            Files.writeString(additionsFolder.resolve(addition.getKey() + ".txt"), addition.getValue());
            System.out.println("=== " + addition.getKey() + " (generated/additions/" + addition.getKey() + ".txt) ===");
            System.out.println(addition.getValue());
        }

        System.out.println(source);
        System.out.println("Written: " + file.toAbsolutePath());
        for (String warning : generator.warnings()) {
            System.out.println("WARNING: " + warning);
        }
    }

    /** Asks a yes/no question on the console. */
    private static Confirmer asker() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        return question -> {
            try {
                while (true) {
                    System.out.print(question);
                    System.out.flush();
                    String line = in.readLine();
                    if (line == null) {
                        return false;
                    }
                    line = line.trim().toLowerCase();
                    if (line.equals("y")) {
                        return true;
                    }
                    if (line.equals("n")) {
                        return false;
                    }
                    System.out.println("  Please type y or n.");
                }
            } catch (java.io.IOException e) {
                return false;
            }
        };
    }
}