package com.autogen.indexer;

public record IndexEntry(
        MethodInfo method,
        CapabilityCard card,
        double[] vector
) {
}