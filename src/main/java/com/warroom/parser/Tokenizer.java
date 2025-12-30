package com.warroom.parser;

import java.util.ArrayList;
import java.util.List;

import static com.warroom.parser.Token.Type.*;


public class Tokenizer {
    private final String s;
    private int i = 0;

    public Tokenizer(String s) {
        this.s = s;
    }

    public List<Token> tokenize() {
        List<Token> out = new ArrayList<>();
        while (true){
            skipWhiteSpace();
            if (i >= s.length()) {
                out.add(new Token(EOF, "", i));
                return out;
            }

            char c = s.charAt(i);

            if (c == '{') {
                out.add(new Token(LBRACE, "{", i++));
                continue;
            }
            if (c == '}') {
                out.add(new Token(RBRACE, "}", i++));
                continue;
            }
            if (c == '=') {
                out.add(new Token(EQUALS, "=", i++));
                continue;
            }

            if (c == '"') {
                out.add(readString());
                continue;
            }

            if (isNumberStart(c)){
                out.add(readNumber());
                continue;
            }

            if (isIdentStart(c)){
                out.add(readIdent());
                continue;
            }

            i++;
        }
    }

    private void skipWhiteSpace() {
        while (i < s.length()) {
            char c = s.charAt(i);

            if (c == '#') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
                continue;
            }

            if (!Character.isWhitespace(c)) return ;
            i++;
        }
    }

    private Token readString() {
        int start = i;
        i++;

        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);

            if (c == '"') break;

            if (c == '\\' && i < s.length()) {
                char next = s.charAt(i++);
                sb.append(next);
            } else {
                sb.append(c);
            }
        }

        return new Token(STRING, sb.toString(), start);
    }

    private Token readNumber() {
        int start = i;
        int j = i;
        if (s.charAt(j) == '-') j++;
        while (j < s.length() && Character.isDigit(s.charAt(j))) j++;

        if (j < s.length() && s.charAt(j) == '.') {
            j++;
            while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
        }
        String num = s.substring(i, j);
        i = j;
        return new Token(NUMBER, num, start);
    }

    private Token readIdent() {
        int start = i;
        int j = i;
        while (j < s.length()) {
            char c = s.charAt(j);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' ) {
                j++;
            } else {
                break;
            }
        }
        String ident = s.substring(i, j);
        i = j;
        return new Token(IDENT, ident, start);
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' ;
    }

    private static boolean isNumberStart(char c) {
        return Character.isDigit(c) || c == '-';
    }
}
