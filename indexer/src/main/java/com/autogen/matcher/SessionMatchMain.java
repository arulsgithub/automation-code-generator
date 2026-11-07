package com.autogen.matcher;

import com.autogen.indexer.EmbeddingClient;
import com.autogen.indexer.IndexBuilder;
import com.autogen.indexer.Match;
import com.autogen.indexer.MethodFootprint;
import com.autogen.indexer.PageIndex;
import com.autogen.recorder.Session;
import com.autogen.recorder.Step;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class SessionMatchMain {

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.err.println("Usage: <pages folder> <session.json>");
            return;
        }

        Path pagesFolder = Path.of(args[0]);
        PageIndex index = PageIndex.build(pagesFolder);
        Session session = new ObjectMapper().readValue(Path.of(args[1]).toFile(), Session.class);

        List<Step> steps = session.steps();
        List<Segment> segments = new SessionMatcher(index).match(session);

        boolean anyUnmatched = segments.stream().anyMatch(Segment::isUnmatched);
        MeaningFallback fallback = anyUnmatched ? tryBuildFallback(pagesFolder) : null;

        for (Segment segment : segments) {
            String range = (segment.length() == 1)
                    ? "step " + (segment.start() + 1)
                    : "steps " + (segment.start() + 1) + "-" + (segment.start() + segment.length());

            if (segment.isMatched()) {
                MethodFootprint method = segment.options().get(0);
                System.out.printf("%-10s MATCH      %s.%s(%s)%n", range,
                        method.className(), method.methodName(), String.join(", ", segment.arguments()));

            } else if (segment.isAmbiguous()) {
                System.out.printf("%-10s AMBIGUOUS  ask the tester:%n", range);
                for (MethodFootprint option : segment.options()) {
                    System.out.printf("%-10s            - %s.%s(%s)  returns %s%n", "",
                            option.className(), option.methodName(),
                            String.join(", ", segment.arguments()), option.returnType());
                }

            } else {
                Step step = steps.get(segment.start());
                System.out.printf("%-10s NO MATCH   %s \"%s\"  %s%n", range,
                        step.kind(), step.text(), step.locatorKey());
                printMeaningSuggestions(fallback, step);
            }
        }
    }

    private static void printMeaningSuggestions(MeaningFallback fallback, Step step) {
        if (fallback == null) {
            return;
        }
        try {
            for (Match match : fallback.suggest(step, 2)) {
                System.out.printf("%-10s            closest by meaning: %.2f  %s.%s%n", "",
                        match.score(),
                        match.entry().method().className(), match.entry().method().name());
            }
        } catch (IOException | InterruptedException problem) {
            System.out.println("            (meaning search failed: " + problem.getMessage() + ")");
        }
    }

    /** Needs the Python service. If it is not running we say so and carry on without it. */
    static MeaningFallback tryBuildFallback(Path pagesFolder) {
        try {
            EmbeddingClient client = new EmbeddingClient("http://localhost:8000");
            return new MeaningFallback(new IndexBuilder().build(pagesFolder, client), client);
        } catch (IOException | InterruptedException problem) {
            String reason = (problem.getMessage() != null)
                    ? problem.getMessage() : problem.getClass().getSimpleName();
            System.out.println("(Meaning search skipped: is the Python service running? "
                    + reason + ")\n");
            return null;
        }
    }
}