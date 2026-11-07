package com.autogen.generator;

import com.autogen.indexer.Action;
import com.autogen.indexer.MethodFootprint;
import com.autogen.indexer.PageIndex;
import com.autogen.matcher.Decision;
import com.autogen.matcher.LocatorKeys;
import com.autogen.matcher.Plan;
import com.autogen.recorder.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides how every "NEW METHOD" in the plan is written:
 * <ul>
 *   <li>one fixed method (for example clickCancel) that uses a field, or</li>
 *   <li>ONE method with an item name (for example removeItem) when the same click was recorded
 *       on several similar items, such as the Remove buttons of different products.</li>
 * </ul>
 * It also collects the text to add to each page class.
 */
public class NewMethodPlanner {

    private static final Pattern ATTRIBUTE = Pattern.compile("css:\\[([\\w-]+)='([^']+)']");
    private static final Pattern ID = Pattern.compile("css:#(.+)");

    /** The shared part of locators that differ only at the end, e.g. remove-  +  sauce-labs-backpack */
    private record Family(String methodName, String kind, String attribute, String prefix) {
    }

    /** What we need to write one page class addition. */
    private static class Addition {
        final Map<String, String> fields = new LinkedHashMap<>();   // field name -> @FindBy line
        final Map<String, String> methods = new LinkedHashMap<>();  // method name -> source
        boolean needsBy;
    }

    private final PageIndex index;
    private final Set<String> pageClasses;
    private final Plan plan;
    private final List<Step> steps;

    private final Map<Integer, Family> familyOf = new HashMap<>();   // decision index -> family
    private final Set<Integer> distinct = new HashSet<>();           // tester said "separate methods"
    private final Map<String, Addition> additions = new LinkedHashMap<>();

    public NewMethodPlanner(PageIndex index, Set<String> pageClasses, Plan plan, List<Step> steps,
                            Confirmer confirmer) {
        this.index = index;
        this.pageClasses = pageClasses;
        this.plan = plan;
        this.steps = steps;
        findFamilies(confirmer);
    }

    // ---------- 1. find clicks that belong together ----------

    private void findFamilies(Confirmer confirmer) {
        Map<String, List<Integer>> byLabel = new LinkedHashMap<>();
        for (int i = 0; i < plan.decisions().size(); i++) {
            Decision d = plan.decisions().get(i);
            if (d.className() != null || d.length() != 1) {
                continue;
            }
            Step step = steps.get(d.start());
            if (!step.kind().equals("click") || parse(step) == null) {
                continue;
            }
            byLabel.computeIfAbsent(MethodNaming.label(step), k -> new ArrayList<>()).add(i);
        }

        for (Map.Entry<String, List<Integer>> entry : byLabel.entrySet()) {
            List<Integer> members = entry.getValue();
            List<String> values = new ArrayList<>();
            for (int i : members) {
                String value = parse(stepOf(i))[2];
                if (!values.contains(value)) {
                    values.add(value);
                }
            }
            if (values.size() < 2) {
                continue;                          // same button twice: one fixed method is right
            }
            String[] first = parse(stepOf(members.get(0)));
            boolean sameShape = members.stream().allMatch(i -> {
                String[] p = parse(stepOf(i));
                return p[0].equals(first[0]) && p[1].equals(first[1]);
            });
            String prefix = labelPrefix(entry.getKey(), values);
            if (prefix == null) {
                prefix = commonPrefix(values);
            }
            if (!sameShape || prefix == null) {
                continue;
            }

            String label = entry.getKey();
            String stepNumbers = String.join(", ",
                    members.stream().map(i -> String.valueOf(plan.decisions().get(i).start() + 1)).toList());
            boolean one = confirmer.confirm("Steps " + stepNumbers + " click \"" + label
                    + "\" on different items. Make ONE method with an item name? (y/n): ");
            if (one) {
                String name = lowerFirst(MethodNaming.pascal(label)) + "Item";
                Family family = new Family(name, first[0], first[1], prefix);
                members.forEach(i -> familyOf.put(i, family));
            } else {
                distinct.addAll(members);
            }
        }
    }

    /** [kind, attribute, value] for css:[attr='value'] or css:#value, else null. */
    private String[] parse(Step step) {
        if (step.locatorKey() == null) {
            return null;
        }
        String key = LocatorKeys.canonical(step.locatorKey());
        Matcher attribute = ATTRIBUTE.matcher(key);
        if (attribute.matches()) {
            return new String[]{"attr", attribute.group(1), attribute.group(2)};
        }
        Matcher id = ID.matcher(key);
        if (id.matches()) {
            return new String[]{"id", "", id.group(1)};
        }
        return null;
    }

    /** The label as it appears in the locators: "Remove" -> "remove-", "Add to cart" -> "add-to-cart-". */
    private String labelPrefix(String label, List<String> values) {
        String slug = label.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") + "-";
        if (slug.length() < 3) {
            return null;
        }
        for (String value : values) {
            if (!value.startsWith(slug) || value.length() <= slug.length()) {
                return null;
            }
        }
        return slug;
    }

    /** "remove-sauce-labs-backpack" + "remove-sauce-labs-bike-light" -> "remove-" (or null if none). */
    private String commonPrefix(List<String> values) {
        String prefix = values.get(0);
        for (String value : values) {
            int n = 0;
            while (n < prefix.length() && n < value.length() && prefix.charAt(n) == value.charAt(n)) {
                n++;
            }
            prefix = prefix.substring(0, n);
        }
        int cut = Math.max(prefix.lastIndexOf('-'), prefix.lastIndexOf('_'));
        if (cut < 1) {
            return null;
        }
        prefix = prefix.substring(0, cut + 1);
        for (String value : values) {
            if (value.length() <= prefix.length()) {
                return null;
            }
        }
        return prefix;
    }

    // ---------- 2. describe the call, and remember what to write ----------

    /** Called while the test is generated; "owner" is the page the test is on at this step. */
    public NewCall callFor(int decisionIndex, String owner) {
        Decision d = plan.decisions().get(decisionIndex);
        List<Step> part = steps.subList(d.start(), d.start() + d.length());
        String returnType = returnTypeFor(decisionIndex, owner);
        Addition addition = additions.computeIfAbsent(owner, k -> new Addition());

        Family family = familyOf.get(decisionIndex);
        if (family != null) {
            return parameterised(family, part.get(0), owner, returnType, addition);
        }
        return fixed(decisionIndex, part, owner, returnType, addition);
    }

    private NewCall parameterised(Family family, Step step, String owner, String returnType,
                                  Addition addition) {
        String[] parsed = parse(step);
        String remainder = parsed[2].substring(family.prefix().length());
        String name = family.methodName();

        if (!addition.methods.containsKey(name)) {
            char separator = family.prefix().charAt(family.prefix().length() - 1);
            StringBuilder source = new StringBuilder();
            source.append("    public ").append(returnType).append(' ').append(name)
                    .append("(String itemName) {\n");
            source.append("        String id = \"").append(family.prefix())
                    .append("\" + itemName.toLowerCase().replace(' ', '").append(separator).append("');\n");
            if (family.kind().equals("id")) {
                source.append("        click(driver.findElement(By.id(id)));\n");
            } else {
                source.append("        click(driver.findElement(By.cssSelector(\"[")
                        .append(family.attribute()).append("='\" + id + \"']\")));\n");
            }
            source.append(returnLine(returnType, owner));
            source.append("    }\n");
            addition.methods.put(name, source.toString());
            addition.needsBy = true;
        }
        return new NewCall(name, List.of(titleCase(remainder)), returnType);
    }

    private NewCall fixed(int decisionIndex, List<Step> part, String owner, String returnType,
                          Addition addition) {
        String name = MethodNaming.forNewMethod(part);
        if (distinct.contains(decisionIndex)) {
            name = name + MethodNaming.pascal(distinctWords(part.get(part.size() - 1)), 8);
        }
        name = freeMethodName(owner, name, addition);

        List<String> parameterNames = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        List<String> bodyLines = new ArrayList<>();

        for (Step step : part) {
            String field = fieldFor(owner, step, addition, distinct.contains(decisionIndex));
            if (step.kind().equals("input")) {
                String parameter = uniqueName(MethodNaming.parameterName(step), parameterNames);
                parameterNames.add(parameter);
                arguments.add(step.value());
                bodyLines.add("        type(" + field + ", " + parameter + ");");
            } else {
                bodyLines.add("        click(" + field + ");");
            }
        }

        if (!addition.methods.containsKey(name)) {
            List<String> declared = new ArrayList<>();
            for (String parameter : parameterNames) {
                declared.add("String " + parameter);
            }
            StringBuilder source = new StringBuilder();
            source.append("    public ").append(returnType).append(' ').append(name)
                    .append('(').append(String.join(", ", declared)).append(") {\n");
            bodyLines.forEach(line -> source.append(line).append('\n'));
            source.append(returnLine(returnType, owner));
            source.append("    }\n");
            addition.methods.put(name, source.toString());
        }
        return new NewCall(name, arguments, returnType);
    }

    // ---------- fields ----------

    /** The name of the field for this element: an existing one if the class already has it. */
    private String fieldFor(String owner, Step step, Addition addition, boolean distinctName) {
        String key = step.locatorKey() == null ? null : LocatorKeys.canonical(step.locatorKey());

        for (Map.Entry<String, String> existing : index.fieldsOf(owner).entrySet()) {
            if (key != null && LocatorKeys.canonical(existing.getValue()).equals(key)) {
                return existing.getKey();               // reuse: the class already knows this element
            }
        }
        String base = MethodNaming.fieldName(step);
        if (distinctName) {
            base = lowerFirst(MethodNaming.pascal(MethodNaming.label(step) + " " + distinctWords(step), 8))
                    + (step.kind().equals("input") ? "Field" : "Button");
        }
        String name = base;
        int n = 2;
        while (index.fieldsOf(owner).containsKey(name)
                || (addition.fields.containsKey(name) && !addition.fields.get(name).equals(findBy(key, name)))) {
            name = base + n++;
        }
        addition.fields.put(name, findBy(key, name));
        return name;
    }

    private String findBy(String key, String name) {
        String how = "css";
        String value = "TODO";
        if (key != null) {
            int colon = key.indexOf(':');
            how = key.substring(0, colon);
            value = key.substring(colon + 1);
        }
        return "    @FindBy(" + how + " = \"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                + "\")\n    private WebElement " + name + ";\n";
    }

    // ---------- return type ----------

    /** Where does the test end up after this step? Look at the next decision. */
    private String returnTypeFor(int i, String owner) {
        if (i + 1 >= plan.decisions().size()) {
            return "void";
        }
        Decision next = plan.decisions().get(i + 1);
        if (next.className() != null && pageClasses.contains(next.className())) {
            return next.className();
        }
        return owner;
    }

    private String returnLine(String returnType, String owner) {
        if (returnType.equals("void")) {
            return "";
        }
        return returnType.equals(owner)
                ? "        return this;\n"
                : "        return new " + returnType + "(driver);\n";
    }

    // ---------- names and text ----------

    private String freeMethodName(String owner, String wanted, Addition addition) {
        String name = wanted;
        int n = 2;
        while (methodExists(owner, name) && !addition.methods.containsKey(name)) {
            name = wanted + n++;
        }
        return name;
    }

    private boolean methodExists(String owner, String name) {
        for (MethodFootprint footprint : index.footprints()) {
            if (footprint.className().equals(owner) && footprint.methodName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String uniqueName(String wanted, List<String> used) {
        String name = wanted;
        int n = 2;
        while (used.contains(name)) {
            name = wanted + n++;
        }
        return name;
    }

    /** The part of the locator that tells similar items apart: remove-sauce-labs-backpack -> sauce-labs-backpack */
    private String distinctWords(Step step) {
        String[] parsed = parse(step);
        String tail = parsed != null ? parsed[2] : locatorTail(step);
        String slug = MethodNaming.label(step).toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-";
        return tail.startsWith(slug) ? tail.substring(slug.length()) : tail;
    }

    private String locatorTail(Step step) {
        String key = step.locatorKey() == null ? "" : step.locatorKey();
        int cut = Math.max(key.lastIndexOf('='), Math.max(key.lastIndexOf(':'), key.lastIndexOf('#')));
        return key.substring(cut + 1);
    }

    private String titleCase(String slug) {
        StringBuilder out = new StringBuilder();
        for (String word : slug.split("[-_]+")) {
            if (!word.isEmpty()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    private String lowerFirst(String text) {
        return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
    }

    private Step stepOf(int decisionIndex) {
        return steps.get(plan.decisions().get(decisionIndex).start());
    }

    // ---------- 3. the text to paste into each page class ----------

    /** page class name -> the fields and methods to add to it */
    public Map<String, String> additions() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Addition> entry : additions.entrySet()) {
            Addition addition = entry.getValue();
            if (addition.fields.isEmpty() && addition.methods.isEmpty()) {
                continue;
            }
            Set<String> imports = new LinkedHashSet<>();
            if (!addition.fields.isEmpty()) {
                imports.add("org.openqa.selenium.WebElement");
                imports.add("org.openqa.selenium.support.FindBy");
            }
            if (addition.needsBy) {
                imports.add("org.openqa.selenium.By");
            }
            StringBuilder text = new StringBuilder();
            text.append("// Add this to ").append(entry.getKey()).append(".java\n");
            if (!imports.isEmpty()) {
                text.append("// Imports needed (if missing): ").append(String.join(", ", imports)).append("\n");
            }
            text.append('\n');
            addition.fields.values().forEach(field -> text.append(field).append('\n'));
            addition.methods.values().forEach(method -> text.append(method).append('\n'));
            result.put(entry.getKey(), text.toString());
        }
        return result;
    }

    // ---------- checks (from Alt+click expectations) ----------

    /**
     * Finds the page method that reads the expected element, or plans a new one.
     * With text: a method with no parameters that returns String and calls getText on that element.
     * Without text: a boolean method that calls isDisplayed on it.
     * Returns null when the recording has no safe locator for the element.
     */
    public AssertionCall assertionFor(Step expect, String owner) {
        if (expect.locatorKey() == null) {
            return null;
        }
        String key = LocatorKeys.canonical(expect.locatorKey());
        boolean wantsText = expect.value() != null && !expect.value().isBlank();
        String actionName = wantsText ? "getText" : "isDisplayed";
        String returnType = wantsText ? "String" : "boolean";

        for (MethodFootprint footprint : index.footprints()) {
            if (!footprint.className().equals(owner) || !footprint.params().isEmpty()
                    || !footprint.returnType().equals(returnType)) {
                continue;
            }
            for (Action action : footprint.actions()) {
                if (action.name().equals(actionName)
                        && LocatorKeys.canonical(action.locator()).equals(key)) {
                    return new AssertionCall(footprint.methodName(), wantsText);
                }
            }
        }

        // nothing reads this element yet: plan a new getter
        Step shape = new Step("expect", expect.tag(), "", expect.value(), expect.locator(),
                expect.locatorKey(), null);
        Addition addition = additions.computeIfAbsent(owner, k -> new Addition());
        String field = fieldFor(owner, shape, addition, false);
        String words = MethodNaming.pascal(MethodNaming.label(shape));
        String name = freeMethodName(owner,
                wantsText ? "get" + words + "Text" : "is" + words + "Displayed", addition);

        if (!addition.methods.containsKey(name)) {
            String source = wantsText
                    ? "    public String " + name + "() {\n        return " + field + ".getText();\n    }\n"
                    : "    public boolean " + name + "() {\n        return isDisplayed(" + field + ");\n    }\n";
            addition.methods.put(name, source);
        }
        return new AssertionCall(name, wantsText);
    }
}