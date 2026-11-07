package com.autogen.matcher;

import java.util.List;

public record Plan(String sessionFile, List<Decision> decisions) {
}