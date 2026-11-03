package com.autogen.recorder;

import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.WebDriverException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RecorderMain {

    public static void main(String[] args) throws Exception {

        String url = args.length > 0 ? args[0] : "https://www.saucedemo.com";
        LocatorChooser chooser = new LocatorChooser();
        List<Step> steps = new ArrayList<>();

        try (BrowserSession browser = new BrowserSession()) {
            browser.open(url);
            Recorder recorder = new Recorder(browser.driver());
            System.out.println("Recording at " + url + ".");
            System.out.println("Alt+click = expectation. Press ENTER here to stop and save.\n");

            while (System.in.available() == 0) {          // until the tester presses Enter
                try {
                    collect(recorder, chooser, steps);
                } catch (NoSuchWindowException closed) {
                    System.out.println("Browser window was closed.");
                    break;
                } catch (WebDriverException busy) {
                    // page is in the middle of loading; try again next round
                }
                Thread.sleep(500);
            }

            // one last read, so nothing from the final half second is lost
            try {
                collect(recorder, chooser, steps);
            } catch (WebDriverException ignored) {
                // the window may already be gone
            }
        }

        Session recording = new Session(url, Instant.now().toString(), steps);
        Path file = new SessionWriter().write(recording, Path.of("sessions"));
        System.out.println("\nSaved " + steps.size() + " steps to " + file.toAbsolutePath());
    }

    private static void collect(Recorder recorder, LocatorChooser chooser, List<Step> steps)
            throws IOException {
        for (RecordedEvent event : recorder.poll()) {
            Step step = toStep(event, chooser);
            steps.add(step);
            print(step);
        }
    }

    private static Step toStep(RecordedEvent e, LocatorChooser chooser) {
        Locator locator = chooser.choose(e).orElse(null);
        return new Step(e.kind(), e.tag(), e.text(), e.value(),
                locator == null ? null : locator.code(),
                locator == null ? null : locator.key(),
                locator == null ? null : locator.reason());
    }

    private static void print(Step s) {
        System.out.printf("  %-6s %s text=\"%s\" value=\"%s\"%n",
                s.kind().toUpperCase(), s.tag(), s.text(), s.value());
        String locator = (s.locator() == null)
                ? "NONE - no stable unique attribute; ask the dev to add data-testid"
                : s.locator() + "   (" + s.locatorReason() + ")";
        System.out.println("         locator: " + locator);
    }
}