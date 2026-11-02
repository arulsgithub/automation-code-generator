package com.autogen.recorder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Recorder {

    private final JavascriptExecutor js;
    private final ObjectMapper json = new ObjectMapper();
    private final String listenerScript;

    public Recorder(WebDriver driver) throws IOException {
        this.js = (JavascriptExecutor) driver;
        try (InputStream in = Recorder.class.getResourceAsStream("/recorder.js")) {
            if (in == null) {
                throw new IOException("recorder.js not found in resources");
            }
            this.listenerScript = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Inject the listener (safe to repeat) and return the events recorded since the last poll. */
    public List<RecordedEvent> poll() throws IOException {
        js.executeScript(listenerScript);

        Object raw = js.executeScript(
                "var e = sessionStorage.getItem('__recorderEvents') || '[]';"
                        + "sessionStorage.removeItem('__recorderEvents');"
                        + "return e;");

        return json.readValue((String) raw, new TypeReference<List<RecordedEvent>>() { });
    }
}