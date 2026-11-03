package com.autogen.indexer;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class PageIndexMain {

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.err.println("Usage: provide the folder that holds the page object .java files.");
            return;
        }

        PageIndex index = PageIndex.build(Path.of(args[0]));

        System.out.println("Methods found: " + index.footprints().size());
        System.out.println("\nLocator -> page class(es) that use it\n");

        Map<String, Set<String>> sorted = new TreeMap<>(index.allOwners());
        for (Map.Entry<String, Set<String>> entry : sorted.entrySet()) {
            System.out.printf("  %-60s %s%n", entry.getKey(), entry.getValue());
        }
    }
}