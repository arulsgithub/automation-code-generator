package com.autogen.matcher;

import java.util.List;

public record Decision(
        int start,
        int length,
        String className,      // the page object to call, or null = create a NEW method
        String methodName,
        String returnType,
        List<String> arguments,
        String source          // "automatic", "tester" or "new method"
) {
}