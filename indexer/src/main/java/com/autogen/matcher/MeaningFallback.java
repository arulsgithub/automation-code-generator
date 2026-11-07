package com.autogen.matcher;

import com.autogen.indexer.EmbeddingClient;
import com.autogen.indexer.Match;
import com.autogen.indexer.VectorStore;
import com.autogen.recorder.Step;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class MeaningFallback {

    private static final Set<String> NOISE =
            Set.of("css", "id", "name", "xpath", "data", "test", "testid");

    private final VectorStore store;
    private final EmbeddingClient client;

    public MeaningFallback(VectorStore store, EmbeddingClient client) {
        this.store = store;
        this.client = client;
    }

    public List<Match> suggest(Step step, int topK) throws IOException, InterruptedException {
        double[] vector = client.embed(List.of(describe(step)))[0];
        return store.search(vector, topK);
    }

    /** click "Add to cart": add to cart sauce labs backpack */
    static String describe(Step step) {
        String action = step.kind().equals("input") ? "type into" : "click";

        StringBuilder sentence = new StringBuilder(action);
        if (step.text() != null && !step.text().isBlank()) {
            sentence.append(" \"").append(step.text()).append("\"");
        }

        // the words inside the locator often name the thing: add-to-cart-sauce-labs-backpack
        StringBuilder words = new StringBuilder();
        for (String word : step.locatorKey().split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty() && !NOISE.contains(word.toLowerCase())) {
                words.append(' ').append(word.toLowerCase());
            }
        }
        if (words.length() > 0) {
            sentence.append(":").append(words);
        }
        return sentence.toString();
    }
}