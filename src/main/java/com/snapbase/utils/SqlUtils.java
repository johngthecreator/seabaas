package com.snapbase.utils;

import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlUtils {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final Pattern FILTER = Pattern.compile("^(>=|<=|!=|!~|~|>|<|=)\\s*.+$");

    private SqlUtils() {}

    public record FilterParts(String operator, String value) {}

    public static String validateIdentifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + name);
        }
        return name;
    }

    public static String quoteIdentifier(String name) {
        return "\"" + validateIdentifier(name) + "\"";
    }

    public static int parseIntParam(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer parameter: " + value);
        }
    }

    public static String validateFilter(String input) {
        if (input == null || !FILTER.matcher(input).matches()) {
            throw new IllegalArgumentException("Invalid filter expression: " + input);
        }
        return input;
    }

    public static FilterParts parseFilter(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Invalid filter expression: null");
        }
        Matcher m = FILTER.matcher(input);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid filter expression: " + input);
        }
        String operator = m.group(1);
        String value = input.substring(operator.length()).trim();
        return new FilterParts(operator, value);
    }

    public static String generateId() {
        var chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        var random = new SecureRandom();
        var sb = new StringBuilder(15);
        for (int i = 0; i < 15; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
