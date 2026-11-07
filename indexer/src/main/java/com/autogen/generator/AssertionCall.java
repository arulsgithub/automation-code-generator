package com.autogen.generator;

/**
 * A check the generated test makes after a step.
 *
 * @param methodName the page method that reads the value (existing, or a new one)
 * @param text       true = compare its text with assertEquals; false = assertTrue(isDisplayed...)
 */
public record AssertionCall(String methodName, boolean text) {
}