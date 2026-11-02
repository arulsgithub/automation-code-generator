package com.autogen.recorder;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Owns the Chrome window that the tester will use.
 * AutoCloseable lets us use try-with-resources, so Chrome always closes.
 */
public class BrowserSession implements AutoCloseable {

    private final WebDriver driver;

    public BrowserSession() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        this.driver = new ChromeDriver(options);
    }

    public void open(String url) {
        driver.get(url);
    }

    public WebDriver driver() {
        return driver;
    }

    @Override
    public void close() {
        driver.quit();
    }
}