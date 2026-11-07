package com.autogen.generator;

import com.autogen.recorder.Step;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Makes readable Java names out of what the tester clicked and typed. */
public final class MethodNaming {

    private static final Set<String> NOISE =
            Set.of("css", "id", "name", "xpath", "data", "test", "testid", "qa", "cy");

    private MethodNaming() {
    }

    /** "Cancel" -> clickCancel, "Open Menu" -> clickOpenMenu, type + click -> enterFirstNameAndClickContinue */
    public static String forNewMethod(List<Step> steps) {
        Step firstInput = null;
        Step lastClick = null;
        for (Step step : steps) {
            if (step.kind().equals("input") && firstInput == null) {
                firstInput = step;
            }
            if (step.kind().equals("click")) {
                lastClick = step;
            }
        }
        if (firstInput != null && lastClick != null) {
            return "enter" + pascal(label(firstInput)) + "AndClick" + pascal(label(lastClick));
        }
        if (firstInput != null) {
            return "enter" + pascal(label(firstInput));
        }
        return "click" + pascal(label(lastClick));
    }

    /** A name for a typed value / parameter, such as firstName. */
    public static String parameterName(Step inputStep) {
        String p = pascal(label(inputStep));
        return p.isEmpty() ? "value" : Character.toLowerCase(p.charAt(0)) + p.substring(1);
    }

    /** A name for a locator field, such as cancelButton or firstNameField. */
    public static String fieldName(Step step) {
        String p = pascal(label(step));
        String base = p.isEmpty() ? "element" : Character.toLowerCase(p.charAt(0)) + p.substring(1);
        if (step.kind().equals("input")) {
            return base + "Field";
        }
        return "button".equals(step.tag()) ? base + "Button" : base + "Element";
    }

    /** The best human words for a step: its text, or else words from its locator. */
    static String label(Step step) {
        String text = step.text() == null ? "" : step.text().trim();
        if (!text.isEmpty() && !text.matches("[\\d\\W]+")) {
            return text;
        }
        String key = step.locatorKey() == null ? "" : step.locatorKey();
        int cut = Math.max(key.lastIndexOf('='), key.lastIndexOf(':'));
        return key.substring(cut + 1);
    }

    /** "Open Menu" / "open-menu" / "shopping_cart_link" -> OpenMenu / OpenMenu / ShoppingCartLink (max 4 words) */
    static String pascal(String text) {
        return pascal(text, 4);
    }

    static String pascal(String text, int maxWords) {
        String spaced = text.replaceAll("([a-z])([A-Z])", "$1 $2");
        List<String> words = new ArrayList<>();
        for (String word : spaced.split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty() && !NOISE.contains(word.toLowerCase())) {
                words.add(word);
            }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(words.size(), maxWords); i++) {
            String w = words.get(i);
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return out.toString();
    }
}