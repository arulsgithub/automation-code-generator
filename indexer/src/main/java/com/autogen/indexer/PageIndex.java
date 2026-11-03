package com.autogen.indexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public class PageIndex {

    private final List<MethodFootprint> footprints = new ArrayList<>();
    private final Map<String, Set<String>> ownersByLocator = new HashMap<>();

    /** Scan every .java file under the folder. */
    public static PageIndex build(Path folder) throws IOException {
        PageIndex index = new PageIndex();
        FootprintScanner scanner = new FootprintScanner();

        List<Path> files;
        try (Stream<Path> walk = Files.walk(folder)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }

        for (Path file : files) {
            List<MethodFootprint> scanned;
            try {
                scanned = scanner.scan(file);
            } catch (RuntimeException problem) {
                // one file we cannot read must not stop the whole scan
                System.err.println("Skipped " + file.getFileName() + ": " + problem.getMessage());
                continue;
            }
            for (MethodFootprint footprint : scanned) {
                index.add(footprint);
            }
        }
        return index;
    }

    private void add(MethodFootprint footprint) {
        footprints.add(footprint);
        for (Action action : footprint.actions()) {
            if (action.locator().startsWith("by:")) {
                continue;   // dynamic locators cannot be compared, so they own nothing
            }
            ownersByLocator
                    .computeIfAbsent(action.locator(), key -> new TreeSet<>())
                    .add(footprint.className());
        }
    }

    public List<MethodFootprint> footprints() {
        return footprints;
    }

    /** Which page classes use this locator? Empty if none. */
    public Set<String> ownersOf(String locatorKey) {
        return ownersByLocator.getOrDefault(locatorKey, Collections.emptySet());
    }

    public Map<String, Set<String>> allOwners() {
        return ownersByLocator;
    }
}