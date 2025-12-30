package com.warroom.parser;

public record Token(Type type, String text, int pos) {
    public enum Type {
        IDENT, STRING, NUMBER,
        LBRACE, RBRACE, EQUALS,
        EOF
    }
}

