package com.autogen.matcher;

import com.autogen.indexer.MethodFootprint;

import java.util.List;

public record Segment(int start, int length, List<MethodFootprint> options, List<String> arguments) {

    public boolean isMatched() {
        return options.size() == 1;
    }

    public boolean isAmbiguous() {
        return options.size() > 1;
    }

    public boolean isUnmatched() {
        return options.isEmpty();
    }
}