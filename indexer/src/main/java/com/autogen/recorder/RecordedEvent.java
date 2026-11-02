package com.autogen.recorder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RecordedEvent(
        String kind,
        String tag,
        String id,
        String name,
        String type,
        String text,
        String value,
        String testIdAttr,
        String testId,
        int idCount,
        int testIdCount,
        int nameCount,
        int textCount
) {
}