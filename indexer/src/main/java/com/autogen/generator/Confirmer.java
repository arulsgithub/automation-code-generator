package com.autogen.generator;

/** Asks the tester a yes/no question. */
@FunctionalInterface
public interface Confirmer {
    boolean confirm(String question);
}