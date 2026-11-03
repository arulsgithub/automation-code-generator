package com.autogen.recorder;

/**
 * A chosen way to find an element.
 *
 * @param code   Java source ready to paste, e.g. By.id("user-name")
 * @param key    the same locator in a comparable form, e.g. id:user-name
 * @param reason why it was chosen, shown to the tester
 */
public record Locator(String code, String key, String reason) {
}