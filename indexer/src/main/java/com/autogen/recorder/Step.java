package com.autogen.recorder;

public record Step(
        String kind,
        String tag,
        String text,
        String value,
        String locator,
        String locatorKey,
        String locatorReason
) {
}