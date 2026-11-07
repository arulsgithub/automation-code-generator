package com.autogen.matcher;

import com.autogen.indexer.Match;
import com.autogen.indexer.MethodFootprint;
import com.autogen.indexer.MethodInfo;
import com.autogen.recorder.Step;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class Decider {

    /** One thing the tester can pick. */
    private record Option(String className, String methodName, String returnType,
                          List<String> paramNames, String note) {

        String label() {
            return className + "." + methodName + "(" + String.join(", ", paramNames) + ")"
                    + "  -> " + returnType + note;
        }
    }

    private final BufferedReader in;
    private final PrintStream out;

    public Decider(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public List<Decision> decide(List<Segment> segments, List<Step> steps, MeaningFallback fallback)
            throws IOException {

        List<Decision> decisions = new ArrayList<>();

        for (Segment segment : segments) {
            out.println();
            out.println(range(segment) + ":  " + describe(steps, segment));

            // 1. exactly one method fits: use it, no question
            if (segment.isMatched()) {
                Option only = fromFootprint(segment.options().get(0));
                out.println("  -> " + only.label() + "   (automatic: same elements)");
                decisions.add(decision(segment, only, segment.arguments(), "automatic"));
                continue;
            }

            // 2. several fit, or none fits: collect what we can offer, then ask
            List<Option> options = new ArrayList<>();
            if (segment.isAmbiguous()) {
                for (MethodFootprint footprint : segment.options()) {
                    options.add(fromFootprint(footprint));
                }
            } else if (fallback != null) {
                options.addAll(suggestionsFor(fallback, steps.get(segment.start())));
            }

            if (options.isEmpty()) {
                out.println("  -> no method found: a new method will be created");
                decisions.add(decision(segment, null, segment.arguments(), "new method"));
                continue;
            }

            Option chosen = choose(options);
            if (chosen == null) {
                decisions.add(decision(segment, null, segment.arguments(), "new method"));
            } else {
                List<String> arguments = fillArguments(chosen, segment.arguments());
                decisions.add(decision(segment, chosen, arguments, "tester"));
            }
        }
        return decisions;
    }

    // ---------- asking ----------

    /** Show the options and read the tester's choice. Returns null for "new method". */
    private Option choose(List<Option> options) throws IOException {
        for (int i = 0; i < options.size(); i++) {
            out.printf("  %d) %s%n", i + 1, options.get(i).label());
        }
        out.println("  n) none of these - create a new method");

        while (true) {
            out.print("Your choice: ");
            out.flush();
            String line = in.readLine();
            if (line == null) {
                return null;                      // no more input: treat as "new method"
            }
            line = line.trim().toLowerCase();
            if (line.equals("n")) {
                return null;
            }
            try {
                int number = Integer.parseInt(line);
                if (number >= 1 && number <= options.size()) {
                    return options.get(number - 1);
                }
            } catch (NumberFormatException ignored) {
                // fall through to the message below
            }
            out.println("  Please type a number from the list, or n.");
        }
    }

    /** If the method takes more values than the tester typed, ask for the rest. */
    private List<String> fillArguments(Option option, List<String> typed) throws IOException {
        List<String> arguments = new ArrayList<>(typed);
        for (int i = arguments.size(); i < option.paramNames().size(); i++) {
            out.print("  value for " + option.paramNames().get(i) + ": ");
            out.flush();
            String line = in.readLine();
            arguments.add(line == null ? "" : line.trim());
        }
        return arguments;
    }

    private List<Option> suggestionsFor(MeaningFallback fallback, Step step) {
        List<Option> options = new ArrayList<>();
        try {
            for (Match match : fallback.suggest(step, 3)) {
                MethodInfo method = match.entry().method();
                options.add(new Option(method.className(), method.name(), method.returnType(),
                        paramNames(method.params()), String.format("   (score %.2f)", match.score())));
            }
        } catch (IOException | InterruptedException problem) {
            out.println("  (meaning search failed: " + problem.getMessage() + ")");
        }
        return options;
    }

    // ---------- helpers ----------

    private Option fromFootprint(MethodFootprint footprint) {
        return new Option(footprint.className(), footprint.methodName(), footprint.returnType(),
                footprint.params(), "");
    }

    /** ["String username", "String password"] -> ["username", "password"] */
    private List<String> paramNames(List<String> typedParams) {
        List<String> names = new ArrayList<>();
        for (String param : typedParams) {
            names.add(param.substring(param.lastIndexOf(' ') + 1));
        }
        return names;
    }

    private Decision decision(Segment segment, Option option, List<String> arguments, String source) {
        return new Decision(segment.start(), segment.length(),
                option == null ? null : option.className(),
                option == null ? null : option.methodName(),
                option == null ? null : option.returnType(),
                arguments, source);
    }

    private String range(Segment segment) {
        return (segment.length() == 1)
                ? "step " + (segment.start() + 1)
                : "steps " + (segment.start() + 1) + "-" + (segment.start() + segment.length());
    }

    /** type "standard_user", type "<masked>", click "Login" */
    private String describe(List<Step> steps, Segment segment) {
        List<String> parts = new ArrayList<>();
        for (int k = 0; k < segment.length(); k++) {
            Step step = steps.get(segment.start() + k);
            if (step.kind().equals("input")) {
                parts.add("type \"" + step.value() + "\"");
            } else {
                String name = (step.text() != null && !step.text().isBlank())
                        ? step.text() : step.locatorKey();
                parts.add("click \"" + name + "\"");
            }
        }
        return String.join(", ", parts);
    }
}