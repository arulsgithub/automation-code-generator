package com.autogen.matcher;

import com.autogen.indexer.PageIndex;
import com.autogen.recorder.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class PlanMain {

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.err.println("Usage: <pages folder> <session.json>");
            return;
        }

        Path pagesFolder = Path.of(args[0]);
        Path sessionPath = Path.of(args[1]);

        PageIndex index = PageIndex.build(pagesFolder);
        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Session session = json.readValue(sessionPath.toFile(), Session.class);

        List<Segment> segments = new SessionMatcher(index).match(session);

        boolean anyUnmatched = segments.stream().anyMatch(Segment::isUnmatched);
        MeaningFallback fallback = anyUnmatched ? SessionMatchMain.tryBuildFallback(pagesFolder) : null;

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        List<Decision> decisions = new Decider(in, System.out).decide(segments, session.steps(), fallback);

        // save the plan: plans/<same file name as the session>
        Path folder = Path.of("plans");
        Files.createDirectories(folder);
        Path planFile = folder.resolve(sessionPath.getFileName());
        json.writeValue(planFile.toFile(), new Plan(sessionPath.getFileName().toString(), decisions));

        summarise(decisions, planFile);
    }

    private static void summarise(List<Decision> decisions, Path planFile) {
        long reused = decisions.stream().filter(d -> d.className() != null).count();
        long created = decisions.size() - reused;

        System.out.println();
        System.out.println("Plan saved: " + planFile.toAbsolutePath());
        System.out.println("  reuse an existing method : " + reused);
        System.out.println("  create a new method      : " + created);

        boolean needsData = decisions.stream()
                .anyMatch(d -> d.arguments().stream().anyMatch(a -> a.equals("<masked>")));
        if (needsData) {
            System.out.println("  note: a password was hidden (<masked>). Real test data is needed for it.");
        }
    }
}