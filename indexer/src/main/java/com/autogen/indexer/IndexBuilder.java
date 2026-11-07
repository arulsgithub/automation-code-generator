package com.autogen.indexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class IndexBuilder {

    public VectorStore build(Path folder, EmbeddingClient client)
            throws IOException, InterruptedException {

        MethodScanner scanner = new MethodScanner();
        CardBuilder cardBuilder = new CardBuilder();

        List<Path> files;
        try (Stream<Path> walk = Files.walk(folder)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }

        List<MethodInfo> methods = new ArrayList<>();
        for (Path file : files) {
            try {
                methods.addAll(scanner.scan(file));
            } catch (RuntimeException problem) {
                System.err.println("Skipped " + file.getFileName() + ": " + problem.getMessage());
            }
        }

        List<CapabilityCard> cards = new ArrayList<>();
        List<String> sentences = new ArrayList<>();
        for (MethodInfo method : methods) {
            CapabilityCard card = cardBuilder.build(method);
            cards.add(card);
            sentences.add(card.sentence());
        }

        double[][] vectors = client.embed(sentences);

        VectorStore store = new VectorStore();
        for (int i = 0; i < methods.size(); i++) {
            store.add(new IndexEntry(methods.get(i), cards.get(i), vectors[i]));
        }
        return store;
    }
}