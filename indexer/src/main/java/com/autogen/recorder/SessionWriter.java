package com.autogen.recorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SessionWriter {

    private final ObjectMapper json = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);   // readable, not one long line

    /** Save the session as sessions/session-<date-time>.json and return the file path. */
    public Path write(Session session, Path folder) throws IOException {
        Files.createDirectories(folder);

        String stamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = folder.resolve("session-" + stamp + ".json");

        json.writeValue(file.toFile(), session);
        return file;
    }
}