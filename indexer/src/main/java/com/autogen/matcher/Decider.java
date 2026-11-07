package com.autogen.matcher;

import com.autogen.indexer.Match;
import com.autogen.indexer.MethodFootprint;
import com.autogen.indexer.MethodInfo;
import com.autogen.recorder.Step;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the matcher's answers into final decisions, asking the tester when the tool is not sure.
 *
 * <ul>
 *   <li>MATCH: used automatically (the elements are exactly the same)</li>
 *   <li>AMBIGUOUS: the tester picks one of the methods that fit</li>
 *   <li>NO MATCH: the tester picks one of the methods that sound close, or "new method"</li>
 * </ul>
 * At the end the tester sees the whole plan and can change any step.
 */
public class Decider {

    /** One thing the tester can pick. */
    private record Option(String className, String methodName, String returnType,
                          List<String> paramNames, String note) {

        String label() {
            return className + "." + methodName + "(" + String.join(", ", paramNames) + ")"
                    + "  -> " + returnType + note;
        }
    }

    /** What the tester chose for an earlier click, so we can offer "same as step 4?". */
    private record Remembered(int stepNumber, Option option) {
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
        Map<String, Remembered> memory = new HashMap<>();

        for (Segment segment : segments) {
            out.println();
            out.println(range(segment) + ":  " + describe(steps, segment));
            decisions.add(decideOne(segment, steps, fallback, memory));
        }

        review(segments, steps, fallback, decisions);
        return decisions;
    }

    /** Decide one part. "memory" may be null (then we never offer "same as step N"). */
    private Decision decideOne(Segment segment, List<Step> steps, MeaningFallback fallback,
                               Map<String, Remembered> memory) throws IOException {

        // 1. exactly one method fits: use it, no question
        if (segment.isMatched()) {
            Option only = fromFootprint(segment.options().get(0));
            out.println("  -> " + only.label() + "   (automatic: same elements)");
            return decision(segment, only, segment.arguments(), "automatic");
        }

        Step first = steps.get(segment.start());
        String key = first.kind() + "|" + first.text();

        // 2. the same kind of click as before: offer the earlier answer
        if (memory != null && segment.length() == 1 && memory.containsKey(key)) {
            Remembered earlier = memory.get(key);
            if (askYesNo("  Same as step " + earlier.stepNumber() + " ("
                    + earlier.option().className() + "." + earlier.option().methodName() + ")? (y/n): ")) {
                List<String> arguments = fillArguments(earlier.option(), segment.arguments());
                return decision(segment, earlier.option(), arguments, "tester");
            }
        }

        // 3. several fit, or none fits: collect what we can offer, then ask
        List<Option> options = new ArrayList<>();
        if (segment.isAmbiguous()) {
            for (MethodFootprint footprint : segment.options()) {
                options.add(fromFootprint(footprint));
            }
        } else if (fallback != null) {
            options.addAll(suggestionsFor(fallback, first));
        }

        if (options.isEmpty()) {
            out.println("  -> no method found: a new method will be created");
            return decision(segment, null, segment.arguments(), "new method");
        }

        Option chosen = choose(options);
        if (chosen == null) {
            return decision(segment, null, segment.arguments(), "new method");
        }
        List<String> arguments = fillArguments(chosen, segment.arguments());
        if (memory != null && segment.length() == 1) {
            memory.put(key, new Remembered(segment.start() + 1, chosen));
        }
        return decision(segment, chosen, arguments, "tester");
    }

    // ---------- review ----------

    /** Show the whole plan and let the tester change any step before it is saved. */
    private void review(List<Segment> segments, List<Step> steps, MeaningFallback fallback,
                        List<Decision> decisions) throws IOException {
        while (true) {
            out.println();
            out.println("=== Your plan ===");
            for (int i = 0; i < segments.size(); i++) {
                out.println(String.format("%-11s %-34s -> %s",
                        range(segments.get(i)) + ":",
                        shorten(describe(steps, segments.get(i)), 34),
                        summary(decisions.get(i))));
            }
            out.print("Change a step? Type its number, or press Enter to save: ");
            out.flush();
            String line = in.readLine();
            if (line == null || line.trim().isEmpty()) {
                return;
            }
            int stepNumber;
            try {
                stepNumber = Integer.parseInt(line.trim());
            } catch (NumberFormatException e) {
                out.println("  Please type a step number, or just press Enter.");
                continue;
            }
            int index = indexOfStep(segments, stepNumber);
            if (index < 0) {
                out.println("  There is no step " + stepNumber + ".");
                continue;
            }
            out.println();
            out.println(range(segments.get(index)) + ":  " + describe(steps, segments.get(index)));
            decisions.set(index, decideOne(segments.get(index), steps, fallback, null));
        }
    }

    private int indexOfStep(List<Segment> segments, int stepNumber) {
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            if (stepNumber >= s.start() + 1 && stepNumber <= s.start() + s.length()) {
                return i;
            }
        }
        return -1;
    }

    /** InventoryPage.addItemToCart("bag")  or  NEW METHOD */
    private String summary(Decision d) {
        if (d.className() == null) {
            return "NEW METHOD";
        }
        List<String> quoted = new ArrayList<>();
        for (String argument : d.arguments()) {
            quoted.add("\"" + argument + "\"");
        }
        return d.className() + "." + d.methodName() + "(" + String.join(", ", quoted) + ")";
    }

    private String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    // ---------- asking ----------

    private boolean askYesNo(String question) throws IOException {
        while (true) {
            out.print(question);
            out.flush();
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
            out.println("  Please type y or n.");
        }
    }

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