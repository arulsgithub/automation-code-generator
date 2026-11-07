package com.autogen.generator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Facts about the tester's framework that the tool cannot guess.
 * Read from generator.properties (all keys are optional; the defaults match the sample project).
 */
public record GeneratorConfig(
        String pagesPackage,     // package of the page objects
        String testPackage,      // package of the generated test class
        String baseTest,         // class the test extends
        String startPageClass,   // page the base test has already opened
        String startPageVariable // field of the base test that holds that page
) {

    public static GeneratorConfig load(Path file) throws IOException {
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            }
        }
        return new GeneratorConfig(
                p.getProperty("pagesPackage", "com.sample.pages"),
                p.getProperty("testPackage", "com.sample.tests"),
                p.getProperty("baseTest", "BaseTest"),
                p.getProperty("startPageClass", "LoginPage"),
                p.getProperty("startPageVariable", "loginPage"));
    }
}