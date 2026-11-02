package com.autogen.indexer;

import java.util.List;

/** What we extract about one method: name, parameters, return type, javadoc. */
public record MethodInfo(
        String className,
        String name,
        List<String> params,
        String returnType,
        String javadoc
) {
}