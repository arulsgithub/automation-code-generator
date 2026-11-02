package com.autogen.recorder;

import java.util.List;

public record Session(
        String startUrl,
        String recordedAt,
        List<Step> steps
) {
}