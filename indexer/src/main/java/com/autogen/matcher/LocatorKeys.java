package com.autogen.matcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocatorKeys {

    private static final Pattern ID_ATTRIBUTE = Pattern.compile("css:\\[id='([^']+)']");
    private static final Pattern ATTRIBUTE_VALUE = Pattern.compile("css:\\[[\\w-]+='([^']+)']");


    private LocatorKeys() {
    }

    public static String canonical(String key) {
        if (key == null) {
            return "";
        }
        if (key.startsWith("id:")) {
            return "css:#" + key.substring(3);
        }
        if (key.startsWith("name:")) {
            return "css:[name='" + key.substring(5) + "']";
        }
        Matcher idAttribute = ID_ATTRIBUTE.matcher(key);
        if (idAttribute.matches()) {
            return "css:#" + idAttribute.group(1);
        }
        return key;
    }

    /**
     * Guesses the item name hidden in a locator.
     * Example: label "Add to cart" + css:[data-test='add-to-cart-sauce-labs-backpack'] -> "Sauce Labs Backpack".
     * Returns null when the locator does not look like  label-slug + item-slug.
     */
    public static String itemNameFor(String label, String locatorKey) {
        if (label == null || locatorKey == null) {
            return null;
        }
        String key = canonical(locatorKey);
        String value = null;
        Matcher attribute = ATTRIBUTE_VALUE.matcher(key);
        if (attribute.matches()) {
            value = attribute.group(1);
        } else if (key.startsWith("css:#")) {
            value = key.substring(5);
        }
        String slug = label.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "") + "-";
        if (value == null || slug.length() < 3 || !value.startsWith(slug) || value.length() <= slug.length()) {
            return null;
        }
        StringBuilder name = new StringBuilder();
        for (String word : value.substring(slug.length()).split("[-_]+")) {
            if (!word.isEmpty()) {
                if (name.length() > 0) {
                    name.append(' ');
                }
                name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return name.length() == 0 ? null : name.toString();
    }
}