package com.autogen.indexer;

import java.util.ArrayList;
import java.util.List;

public class CardBuilder {

    public CapabilityCard build(MethodInfo method) {

        String doc =firstSentence(method.javadoc());
        String nameWords = splitCamelCase(method.name());
        String paramNames = parameterNames(method.params());

        String inside = nameWords;
        if (!paramNames.isEmpty()) {
            inside += "; takes " + paramNames;
        }

        boolean weak = doc.isEmpty();
        String sentence = weak
                ? inside
                : doc + " (" + inside + ")";

        return new CapabilityCard(method.className(), method.name(), sentence, weak);
    }

    private String firstSentence(String javadoc) {
        String oneLine = javadoc.replaceAll("\\s+", " ").strip();   // join wrapped lines
        int end = oneLine.indexOf(". ");                            // end of 1st sentence
        return end == -1 ? oneLine : oneLine.substring(0, end + 1);
    }

    private String splitCamelCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase();
    }

    private String parameterNames(List<String> params) {
        List<String> names = new ArrayList<>();
        for (String param : params) {
            names.add(param.substring(param.lastIndexOf(' ') + 1));
        }
        return String.join(", ", names);
    }
}