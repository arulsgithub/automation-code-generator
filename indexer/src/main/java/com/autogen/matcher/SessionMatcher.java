package com.autogen.matcher;

import com.autogen.indexer.Action;
import com.autogen.indexer.MethodFootprint;
import com.autogen.indexer.PageIndex;
import com.autogen.recorder.Session;
import com.autogen.recorder.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SessionMatcher {

    private record Doing(String kind, String locator) {
    }

    private final PageIndex index;
    private final Set<String> pageClasses = new HashSet<>();
    private final Map<String, Set<String>> ownersByCanonicalLocator = new HashMap<>();

    public SessionMatcher(PageIndex index) {
        this.index = index;

        for (MethodFootprint footprint : index.footprints()) {
            pageClasses.add(footprint.className());
        }
        for (Map.Entry<String, Set<String>> entry : index.allOwners().entrySet()) {
            ownersByCanonicalLocator
                    .computeIfAbsent(LocatorKeys.canonical(entry.getKey()), key -> new TreeSet<>())
                    .addAll(entry.getValue());
        }
    }

    public List<Segment> match(Session session) {
        List<Step> steps = session.steps();
        List<Segment> result = new ArrayList<>();

        int position = 0;
        while (position < steps.size()) {

            // expectations and steps with no locator are not matched here
            if (!isDoingStep(steps.get(position))) {
                position++;
                continue;
            }

            Segment best = bestSegmentAt(steps, position);
            result.add(best);
            position += best.length();
        }
        return result;
    }

    /** Try every method at this position and keep the longest one(s) that pass all checks. */
    private Segment bestSegmentAt(List<Step> steps, int start) {
        int bestLength = 0;
        List<MethodFootprint> bestOptions = new ArrayList<>();

        for (MethodFootprint method : index.footprints()) {
            List<Doing> doing = doingActions(method);
            if (doing.isEmpty()) {
                continue;
            }
            // check 1: same elements, same order
            if (!sameElements(steps, start, doing)) {
                continue;
            }
            int length = doing.size();

            // check 2: as many parameters as typed values
            if (method.params().size() != countInputs(steps, start, length)) {
                continue;
            }
            // check 3: ends on the same page the method returns
            if (!endsOnReturnedPage(steps, start + length, method)) {
                continue;
            }

            if (length > bestLength) {
                bestLength = length;
                bestOptions = new ArrayList<>();
            }
            if (length == bestLength) {
                bestOptions.add(method);
            }
        }

        if (bestLength == 0) {
            return new Segment(start, 1, List.of(), typedValues(steps, start, 1));
        }
        return new Segment(start, bestLength, bestOptions, typedValues(steps, start, bestLength));
    }

    // ---------- check 1 ----------

    private boolean sameElements(List<Step> steps, int start, List<Doing> doing) {
        if (start + doing.size() > steps.size()) {
            return false;
        }
        for (int k = 0; k < doing.size(); k++) {
            Step step = steps.get(start + k);
            if (!isDoingStep(step)) {
                return false;
            }
            if (!step.kind().equals(doing.get(k).kind())) {
                return false;
            }
            if (!LocatorKeys.canonical(step.locatorKey()).equals(doing.get(k).locator())) {
                return false;
            }
        }
        return true;
    }

    // ---------- check 2 ----------

    private int countInputs(List<Step> steps, int start, int length) {
        int count = 0;
        for (int k = 0; k < length; k++) {
            if (steps.get(start + k).kind().equals("input")) {
                count++;
            }
        }
        return count;
    }

    // ---------- check 3 ----------

    /**
     * The step right after the match shows where the tester is: the page that owns its element.
     * If the method returns a page and we know where the tester is, the two must agree.
     * If we do not know, we do not reject.
     */
    private boolean endsOnReturnedPage(List<Step> steps, int nextIndex, MethodFootprint method) {
        if (nextIndex >= steps.size()) {
            return true;
        }
        Step next = steps.get(nextIndex);
        if (next.locatorKey() == null) {
            return true;
        }
        Set<String> owners = ownersByCanonicalLocator
                .getOrDefault(LocatorKeys.canonical(next.locatorKey()), Set.of());

        boolean returnsAPage = pageClasses.contains(method.returnType());
        if (!returnsAPage || owners.isEmpty()) {
            return true;
        }
        return owners.contains(method.returnType());
    }

    // ---------- helpers ----------

    private boolean isDoingStep(Step step) {
        return step.locatorKey() != null
                && (step.kind().equals("input") || step.kind().equals("click"));
    }

    private List<String> typedValues(List<Step> steps, int start, int length) {
        List<String> values = new ArrayList<>();
        for (int k = 0; k < length && start + k < steps.size(); k++) {
            Step step = steps.get(start + k);
            if (step.kind().equals("input")) {
                values.add(step.value());
            }
        }
        return values;
    }

    /**
     * The "doing" part of a method: typing and clicking, in order.
     * Reading actions (getText, isDisplayed ...) are skipped.
     * A method with a run-time locator cannot be compared, so it gets no actions.
     */
    private List<Doing> doingActions(MethodFootprint method) {
        List<Doing> doing = new ArrayList<>();
        for (Action action : method.actions()) {
            if (action.locator().startsWith("by:")) {
                return List.of();
            }
            String kind = kindOf(action.name());
            if (kind != null) {
                doing.add(new Doing(kind, LocatorKeys.canonical(action.locator())));
            }
        }
        return doing;
    }

    private String kindOf(String actionName) {
        String name = actionName.toLowerCase();
        if (name.contains("click")) {
            return "click";
        }
        if (name.contains("type") || name.contains("sendkeys") || name.contains("enter")
                || name.contains("fill") || name.contains("select") || name.contains("settext")) {
            return "input";
        }
        return null;
    }
}