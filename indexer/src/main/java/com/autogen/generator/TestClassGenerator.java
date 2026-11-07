package com.autogen.generator;

import com.autogen.matcher.Decision;
import com.autogen.matcher.Plan;
import com.autogen.recorder.Session;
import com.autogen.recorder.Step;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Turns a plan into the source of a JUnit test class.
 * It follows "which page are we on now" from one line to the next, using each method's return type.
 */
public class TestClassGenerator {

    private final Set<String> pageClasses;
    private final GeneratorConfig config;
    private final List<String> warnings = new ArrayList<>();

    public TestClassGenerator(Set<String> pageClasses, GeneratorConfig config) {
        this.pageClasses = pageClasses;
        this.config = config;
    }

    /** Problems found while generating (for example a step that does not belong on the current page). */
    public List<String> warnings() {
        return warnings;
    }

    public String generate(Plan plan, Session session, String className) {
        List<Step> steps = session.steps();
        List<String> body = new ArrayList<>();
        Set<String> imports = new TreeSet<>();

        String currentClass = config.startPageClass();
        String currentVar = config.startPageVariable();
        Set<String> declared = new HashSet<>();
        declared.add(currentVar);

        for (Decision d : plan.decisions()) {
            List<Step> part = steps.subList(d.start(), d.start() + d.length());
            boolean reuse = d.className() != null;

            String method = reuse ? d.methodName() : MethodNaming.forNewMethod(part);
            List<String> arguments = reuse ? d.arguments() : typedValues(part);
            String returnType = reuse ? d.returnType() : "void";

            boolean wrongPage = reuse && !d.className().equals(currentClass);
            if (wrongPage) {
                String warning = "step " + (d.start() + 1) + ": " + d.className() + "." + method
                        + " expects the " + d.className() + ", but the flow is on " + currentClass + ".";
                warnings.add(warning);
                body.add("// CHECK: " + warning);
            }
            if (!reuse) {
                body.add("// NEW METHOD: add " + method + "(" + arguments.size()
                        + " value(s)) to " + currentClass);
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
        }
        return render(className, plan.sessionFile(), imports, body);
    }

    // ---------- helpers ----------

    private List<String> typedValues(List<Step> part) {
        List<String> values = new ArrayList<>();
        for (Step step : part) {
            if (step.kind().equals("input")) {
                values.add(step.value());
            }
        }
        return values;
    }

    private String quoted(List<String> arguments) {
        List<String> out = new ArrayList<>();
        for (String argument : arguments) {
            String text = argument.equals("<masked>") ? "TODO_PASSWORD" : argument;
            out.add("\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        return String.join(", ", out);
    }

    private String render(String className, String sessionFile, Set<String> imports, List<String> body) {
        StringBuilder s = new StringBuilder();
        s.append("package ").append(config.testPackage()).append(";\n\n");
        for (String i : imports) {
            s.append("import ").append(i).append(";\n");
        }
        s.append("import org.junit.jupiter.api.DisplayName;\n");
        s.append("import org.junit.jupiter.api.Test;\n\n");
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