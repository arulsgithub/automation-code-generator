package com.autogen.generator;

import java.util.List;

/** How the test calls a new method: its name, the values to pass, and what it returns. */
public record NewCall(String methodName, List<String> arguments, String returnType) {
}