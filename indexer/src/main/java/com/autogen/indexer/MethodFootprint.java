package com.autogen.indexer;

import java.util.List;

public record MethodFootprint(
        String className,
        String methodName,
        String returnType,
        List<Action> actions
) {
}