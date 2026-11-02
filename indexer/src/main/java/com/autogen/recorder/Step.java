package com.autogen.recorder;

public record Step(
        String kind,           // "click", "input" or "expect"
        String tag,            // "button", "input" ...
        String text,           // visible label
        String value,          // typed text, or the expected text
        String locator,        // Java code like By.id("x"), or null if none was safe
        String locatorReason   // why it was chosen, or null
) {
}