package com.autogen.indexer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: mvn exec:java -Dexec.args=\"path/to/LoginPage.java\"");
            return;
        }
        Path javaFile = Path.of(args[0]);
        System.out.println("Reading: " + javaFile + "\n");

        MethodScanner scanner = new MethodScanner();
        List<MethodInfo> methods = scanner.scan(javaFile);

        System.out.println("Found " + methods.size() + " public method(s):\n");
        CardBuilder builder = new CardBuilder();

        for (MethodInfo method : methods) {
            CapabilityCard card = builder.build(method);

            if (card.weak()) {
                System.out.println("  WARNING: " + card.methodName()
                        + " has no javadoc, matching may be weak");
            }
            System.out.println("  " + card.sentence());
        }

        List<String> sentences = new ArrayList<>();
        for (MethodInfo method : methods) {
            sentences.add(builder.build(method).sentence());
        }

        EmbeddingClient client = new EmbeddingClient("http://localhost:8000");
        double[][] vectors = client.embed(sentences);

        VectorStore store = new VectorStore();
        for (int i = 0; i < methods.size(); i++) {
            CapabilityCard card = builder.build(methods.get(i));
            store.add(new IndexEntry(methods.get(i), card, vectors[i]));
        }
        System.out.println("\nStored " + store.size() + " methods.");

        // pretend this is what the tester just did in the browser
        String action = "log in as the admin user";
        double[] actionVector = client.embed(List.of(action))[0];

        System.out.println("\nAction: \"" + action + "\"");
        for (Match match : store.search(actionVector, 3)) {
            System.out.printf("  %.2f  %s.%s%n", match.score(),
                    match.entry().method().className(),
                    match.entry().method().name());
        }

        for (int i = 0; i < vectors.length; i++) {
            System.out.printf("  vector %d starts: %.3f, %.3f, %.3f ...%n",
                    i, vectors[i][0], vectors[i][1], vectors[i][2]);
        }
    }
}