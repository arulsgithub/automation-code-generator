package com.autogen.indexer;

public record CapabilityCard(
        String className,
        String methodName,
        String sentence,   // the text we will embed
        boolean weak       // true = method had no javadoc
) {
}