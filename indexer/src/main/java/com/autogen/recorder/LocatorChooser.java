package com.autogen.recorder;

import java.util.Optional;
import java.util.regex.Pattern;

public class LocatorChooser {

    private static final Pattern GENERATED_ID =
            Pattern.compile(".*\\d{3,}.*|.*:.*|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*");

    public Optional<Locator> choose(RecordedEvent e) {

        if (has(e.testId()) && e.testIdCount() == 1 && safe(e.testId())) {
            return Optional.of(new Locator(
                    "By.cssSelector(\"[" + e.testIdAttr() + "='" + e.testId() + "']\")",
                    "unique " + e.testIdAttr()));
        }

        if (has(e.id()) && e.idCount() == 1 && safe(e.id()) && !looksGenerated(e.id())) {
            return Optional.of(new Locator(
                    "By.id(\"" + e.id() + "\")",
                    "unique id"));
        }

        if (has(e.name()) && e.nameCount() == 1 && safe(e.name())) {
            return Optional.of(new Locator(
                    "By.name(\"" + e.name() + "\")",
                    "unique name"));
        }

        if (e.textCount() == 1 && has(e.text()) && safe(e.text())
                && (e.tag().equals("button") || e.tag().equals("a"))) {
            return Optional.of(new Locator(
                    "By.xpath(\"//" + e.tag() + "[normalize-space()='" + e.text() + "']\")",
                    "unique button/link text"));
        }

        return Optional.empty();
    }

    private boolean has(String s) {
        return s != null && !s.isEmpty();
    }

    private boolean safe(String s) {
        return s.indexOf('"') < 0 && s.indexOf('\'') < 0 && s.indexOf('\\') < 0;
    }

    private boolean looksGenerated(String id) {
        return GENERATED_ID.matcher(id).matches();
    }
}