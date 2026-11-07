package com.autogen.matcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocatorKeys {

    private static final Pattern ID_ATTRIBUTE = Pattern.compile("css:\\[id='([^']+)']");

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
}