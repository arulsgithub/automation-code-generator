package com.autogen.indexer;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class EmbeddingClient {

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final String embedUrl;

    public EmbeddingClient(String baseUrl) {
        this.embedUrl = baseUrl + "/embed";
    }

    public double[][] embed(List<String> texts) throws IOException, InterruptedException {

        // 1. build the body: {"texts": ["...", "..."]}
        String body = json.writeValueAsString(Map.of("texts", texts));

        // 2. build the POST request
        HttpRequest request = HttpRequest.newBuilder(URI.create(embedUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        // 3. send it and wait for the answer
        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("AI service returned "
                    + response.statusCode() + ": " + response.body());
        }

        // 4. read {"vectors": [[...], [...]], "dimensions": 384}
        return json.readValue(response.body(), EmbedResponse.class).vectors();
    }

    // matches the JSON that Python's EmbedResponse sends back
    record EmbedResponse(double[][] vectors, int dimensions) {
    }
}