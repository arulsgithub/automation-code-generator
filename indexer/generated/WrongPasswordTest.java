package com.sample.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Generated from the recording session-20261107-224613.json.
 * Review it before you use it.
 */
class WrongPasswordTest extends BaseTest {

    @Test
    @DisplayName("Recorded flow")
    void recordedFlow() {
        // TODO: replace TODO_PASSWORD with real test data
        loginPage.submitCredentials("standard_user", "TODO_PASSWORD");
        assertEquals("Epic sadface: Username and password do not match any user in this service", loginPage.getErrorText());
    }
}
