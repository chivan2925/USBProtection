package com.group.usbshield.agent.usbguard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-closed check for the complete rule lines returned by USBGuard list-rules.
 * This validates the syntax subset the reconciler can reason about. Unsupported
 * conditions or attributes require operator review rather than silent mutation.
 */
final class UsbGuardRuleSyntax {
    private static final Pattern HEADER = Pattern.compile("^\\s*(\\d+):\\s*(.*)$");
    private static final Pattern DEVICE_ID = Pattern.compile("(?:[0-9a-fA-F]{4}|\\*):(?:[0-9a-fA-F]{4}|\\*)");
    private static final Pattern INTERFACE = Pattern.compile("(?:[0-9a-fA-F]{2}|\\*):(?:[0-9a-fA-F]{2}|\\*):(?:[0-9a-fA-F]{2}|\\*)");
    private static final Set<String> OPERATORS = Set.of("all-of", "one-of", "none-of", "equals", "equals-ordered");
    private static final Set<String> CONNECT_TYPES = Set.of("hotplug", "hardwired", "unknown");

    private UsbGuardRuleSyntax() {
    }

    static int validateList(String output) {
        if (output == null) {
            throw new IllegalStateException("USBGuard list-rules returned null");
        }
        int count = 0;
        for (String line : output.lines().toList()) {
            if (line.isBlank()) {
                continue;
            }
            Matcher header = HEADER.matcher(line);
            if (!header.matches()) {
                throw new IllegalStateException("Unrecognized USBGuard list-rules line; refusing policy changes");
            }
            try {
                Long.parseLong(header.group(1));
                validateDefinition(header.group(2));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalStateException("Malformed USBGuard numeric rule header or definition; refusing policy changes", invalid);
            }
            count++;
        }
        return count;
    }

    private static void validateDefinition(String definition) {
        List<Token> tokens = tokenize(definition);
        Cursor cursor = new Cursor(tokens);
        String target = cursor.word();
        if (!Set.of("allow", "block", "reject").contains(target.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported USBGuard rule target");
        }
        Set<String> seen = new HashSet<>();
        if (cursor.hasNext() && DEVICE_ID.matcher(cursor.peek().value()).matches()) {
            cursor.word();
            seen.add("id");
        }
        while (cursor.hasNext()) {
            String name = cursor.word().toLowerCase(Locale.ROOT);
            if (!seen.add(name)) {
                throw new IllegalArgumentException("Repeated USBGuard attribute");
            }
            switch (name) {
                case "id" -> {
                    if (!DEVICE_ID.matcher(cursor.word()).matches()) {
                        throw new IllegalArgumentException("Invalid USBGuard device ID");
                    }
                }
                case "serial", "hash", "name", "parent-hash", "label" -> cursor.quoted();
                case "via-port" -> valueOrSet(cursor, true);
                case "with-interface" -> valueOrSet(cursor, false);
                case "with-connect-type" -> connectValueOrSet(cursor);
                default -> throw new IllegalArgumentException("Unknown or unsupported USBGuard rule attribute");
            }
        }
    }

    private static void valueOrSet(Cursor cursor, boolean quoted) {
        String operator = cursor.hasNext() ? cursor.peek().value() : "";
        if (OPERATORS.contains(operator)) {
            cursor.word();
        }
        if (cursor.take("{")) {
            int count = 0;
            while (!cursor.take("}")) {
                if (quoted) {
                    cursor.quoted();
                } else if (!INTERFACE.matcher(cursor.word()).matches()) {
                    throw new IllegalArgumentException("Invalid USBGuard interface");
                }
                count++;
            }
            if (count == 0) {
                throw new IllegalArgumentException("Empty USBGuard attribute set");
            }
        } else if (OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("Missing USBGuard attribute set");
        } else if (quoted) {
            cursor.quoted();
        } else if (!INTERFACE.matcher(cursor.word()).matches()) {
            throw new IllegalArgumentException("Invalid USBGuard interface");
        }
    }

    private static void connectValueOrSet(Cursor cursor) {
        String operator = cursor.hasNext() ? cursor.peek().value() : "";
        if (OPERATORS.contains(operator)) {
            cursor.word();
        }
        if (cursor.take("{")) {
            int count = 0;
            while (!cursor.take("}")) {
                String value = cursor.quoted();
                if (!CONNECT_TYPES.contains(value.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Invalid USBGuard connect type");
                }
                count++;
            }
            if (count == 0) {
                throw new IllegalArgumentException("Empty USBGuard connect type set");
            }
        } else if (OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("Missing USBGuard connect type set");
        } else {
            String value = cursor.quoted();
            if (!CONNECT_TYPES.contains(value.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Invalid USBGuard connect type");
            }
        }
    }

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        for (int i = 0; i < input.length();) {
            char start = input.charAt(i);
            if (Character.isWhitespace(start)) {
                i++;
                continue;
            }
            if (Character.isISOControl(start)) {
                throw new IllegalArgumentException("Control character in USBGuard rule");
            }
            if (start == '{' || start == '}') {
                tokens.add(new Token(String.valueOf(start), false));
                i++;
                continue;
            }
            if (start == '"') {
                StringBuilder quoted = new StringBuilder();
                boolean closed = false;
                i++;
                while (i < input.length()) {
                    char ch = input.charAt(i++);
                    if (Character.isISOControl(ch)) {
                        throw new IllegalArgumentException("Control character in USBGuard quote");
                    }
                    if (ch == '"') {
                        closed = true;
                        break;
                    }
                    if (ch == '\\') {
                        if (i >= input.length()) {
                            throw new IllegalArgumentException("Truncated USBGuard escape");
                        }
                        ch = input.charAt(i++);
                        if (ch != '"' && ch != '\\') {
                            throw new IllegalArgumentException("Unsupported USBGuard escape");
                        }
                    }
                    quoted.append(ch);
                }
                if (!closed) {
                    throw new IllegalArgumentException("Unterminated USBGuard quote");
                }
                tokens.add(new Token(quoted.toString(), true));
                continue;
            }
            int from = i;
            while (i < input.length() && !Character.isWhitespace(input.charAt(i))
                    && input.charAt(i) != '{' && input.charAt(i) != '}' && input.charAt(i) != '"') {
                if (Character.isISOControl(input.charAt(i))) {
                    throw new IllegalArgumentException("Control character in USBGuard word");
                }
                i++;
            }
            if (from == i) {
                throw new IllegalArgumentException("Unexpected quote in USBGuard word");
            }
            tokens.add(new Token(input.substring(from, i), false));
        }
        return List.copyOf(tokens);
    }

    private record Token(String value, boolean quoted) {
    }

    private static final class Cursor {
        private final List<Token> tokens;
        private int index;

        Cursor(List<Token> tokens) {
            this.tokens = tokens;
        }

        boolean hasNext() {
            return index < tokens.size();
        }

        Token peek() {
            if (!hasNext()) {
                throw new IllegalArgumentException("Truncated USBGuard rule");
            }
            return tokens.get(index);
        }

        String word() {
            Token token = peek();
            if (token.quoted() || token.value().equals("{") || token.value().equals("}")) {
                throw new IllegalArgumentException("Expected USBGuard word");
            }
            index++;
            return token.value();
        }

        String quoted() {
            Token token = peek();
            if (!token.quoted()) {
                throw new IllegalArgumentException("Expected USBGuard quoted value");
            }
            index++;
            return token.value();
        }

        boolean take(String value) {
            if (hasNext() && !peek().quoted() && peek().value().equals(value)) {
                index++;
                return true;
            }
            return false;
        }
    }
}
