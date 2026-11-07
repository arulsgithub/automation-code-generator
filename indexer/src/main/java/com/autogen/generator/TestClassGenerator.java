package com.autogen.generator;

import com.autogen.indexer.PageIndex;
import com.autogen.matcher.Decision;
import com.autogen.matcher.Plan;
import com.autogen.recorder.Session;
import com.autogen.recorder.Step;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns a plan into the source of a JUnit test class.
 * It follows "which page are we on now" from one line to the next, using each method's return type.
 */
public class TestClassGenerator {

    private final Set<String> pageClasses;
    private final GeneratorConfig config;
    private final PageIndex index;
    private final Confirmer confirmer;
    private final List<String> warnings = new ArrayList<>();
    private Map<String, String> additions = new LinkedHashMap<>();

    public TestClassGenerator(Set<String> pageClasses, GeneratorConfig config, PageIndex index,
                              Confirmer confirmer) {
        this.pageClasses = pageClasses;
        this.config = config;
        this.index = index;
        this.confirmer = confirmer;
    }

    /** page class name -> fields and methods to add to it (available after generate()). */
    public Map<String, String> additions() {
        return additions;
    }

    /** Problems found while generating (for example a step that does not belong on the current page). */
    public List<String> warnings() {
        return warnings;
    }

    public String generate(Plan plan, Session session, String className) {
        List<Step> steps = session.steps();
        NewMethodPlanner planner = new NewMethodPlanner(index, pageClasses, plan, steps, confirmer);
        List<String> body = new ArrayList<>();
        Set<String> imports = new TreeSet<>();

        String currentClass = config.startPageClass();
        String currentVar = config.startPageVariable();
        Set<String> declared = new HashSet<>();
        declared.add(currentVar);
        boolean usesAssertEquals = false;
        boolean usesAssertTrue = false;

        for (int i = 0; i < plan.decisions().size(); i++) {
            Decision d = plan.decisions().get(i);
            List<Step> part = steps.subList(d.start(), d.start() + d.length());
            boolean reuse = d.className() != null;

            NewCall created = reuse ? null : planner.callFor(i, currentClass);
            String method = reuse ? d.methodName() : created.methodName();
            List<String> arguments = reuse ? d.arguments() : created.arguments();
            String returnType = reuse ? d.returnType() : created.returnType();

            boolean wrongPage = reuse && !d.className().equals(currentClass);
            if (wrongPage) {
                String warning = "step " + (d.start() + 1) + ": " + d.className() + "." + method
                        + " expects the " + d.className() + ", but the flow is on " + currentClass + ".";
                warnings.add(warning);
                body.add("// CHECK: " + warning);
            }
            if (!reuse) {
                body.add("// NEW METHOD: " + currentClass + "." + method
                        + " (code is in generated/additions/" + currentClass + ".txt)");
            }
            if (arguments.contains("<masked>")) {
                body.add("// TODO: replace TODO_PASSWORD with real test data");
            }

            String call = currentVar + "." + method + "(" + quoted(arguments) + ")";

            if (!wrongPage && pageClasses.contains(returnType) && !returnType.equals(currentClass)) {
                String variable = Character.toLowerCase(returnType.charAt(0)) + returnType.substring(1);
                if (declared.add(variable)) {
                    imports.add(config.pagesPackage() + "." + returnType);
                    body.add(returnType + " " + variable + " = " + call + ";");
                } else {
                    body.add(variable + " = " + call + ";");
                }
                currentClass = returnType;
                currentVar = variable;
            } else {
                body.add(call + ";");
            }

            // checks recorded with Alt+click between this step and the next one
            int end = (i + 1 < plan.decisions().size()) ? plan.decisions().get(i + 1).start() : steps.size();
            for (int k = d.start() + d.length(); k < end; k++) {
                Step expect = steps.get(k);
                if (!expect.kind().equals("expect")) {
                    continue;
                }
                AssertionCall check = planner.assertionFor(expect, currentClass);
                if (check == null) {
                    body.add("// TODO: a check was recorded here but its element has no safe locator");
                } else if (check.text()) {
                    body.add("assertEquals(" + javaString(expect.value()) + ", "
                            + currentVar + "." + check.methodName() + "());");
                    usesAssertEquals = true;
                } else {
                    body.add("assertTrue(" + currentVar + "." + check.methodName() + "());");
                    usesAssertTrue = true;
                }
            }
        }
        additions = planner.additions();
        return render(className, plan.sessionFile(), imports, body, usesAssertEquals, usesAssertTrue);
    }

    // ---------- helpers ----------

    /** A Java string literal: "text" with quotes, backslashes and line breaks escaped. */
    private String javaString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private String quoted(List<String> arguments) {
        List<String> out = new ArrayList<>();
        for (String argument : arguments) {
            String text = argument.equals("<masked>") ? "TODO_PASSWORD" : argument;
            out.add("\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        return String.join(", ", out);
    }

    private String render(String className, String sessionFile, Set<String> imports, List<String> body,
                          boolean usesAssertEquals, boolean usesAssertTrue) {
        StringBuilder s = new StringBuilder();
        s.append("package ").append(config.testPackage()).append(";\n\n");
        for (String i : imports) {
            s.append("import ").append(i).append(";\n");
        }
        s.append("import org.junit.jupiter.api.DisplayName;\n");
        s.append("import org.junit.jupiter.api.Test;\n");
        if (usesAssertEquals || usesAssertTrue) {
            s.append("\n");
            if (usesAssertEquals) {
                s.append("import static org.junit.jupiter.api.Assertions.assertEquals;\n");
            }
            if (usesAssertTrue) {
                s.append("import static org.junit.jupiter.api.Assertions.assertTrue;\n");
            }
        }
        s.append("\n");
        s.append("/**\n * Generated from the recording ").append(sessionFile)
                .append(".\n * Review it before you use it.\n */\n");
        s.append("class ").append(className).append(" extends ").append(config.baseTest()).append(" {\n\n");
        s.append("    @Test\n    @DisplayName(\"Recorded flow\")\n    void recordedFlow() {\n");
        for (String line : body) {
            s.append("        ").append(line).append("\n");
        }
        s.append("    }\n}\n");
        return s.toString();
    }
}