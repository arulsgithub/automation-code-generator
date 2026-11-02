package com.autogen.indexer;

import java.nio.file.Path;
import java.util.List;

public class FootprintMain {

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            System.err.println("Usage: provide the path to a page object .java file.");
            return;
        }

        List<MethodFootprint> footprints = new FootprintScanner().scan(Path.of(args[0]));

        for (MethodFootprint footprint : footprints) {
            System.out.println(footprint.methodName() + "()  -> returns " + footprint.returnType());
            if (footprint.actions().isEmpty()) {
                System.out.println("      (touches no elements)");
            }
            for (Action action : footprint.actions()) {
                System.out.println("      " + action.name() + "  " + action.locator());
            }
        }
    }
}