package com.warroom.parser;

import java.util.*;

import static com.warroom.parser.Token.Type.*;

public class ClausewitzParser {

    public sealed interface Value permits StrVal, NumVal, BoolVal, ObjVal, ListVal {}

    public record StrVal(String v) implements Value {}
    public record NumVal(double v) implements Value {}
    public record BoolVal(boolean v) implements Value {}
    public record ObjVal(Map<String, Value> map) implements Value {}
    public record ListVal(List<Value> list) implements Value {}

    private final List<Token> tokens;
    private int p = 0;

    public ClausewitzParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public ObjVal parseRoot() {
        Map<String, Value> root = new LinkedHashMap<>();

        while (!peek(EOF)) {
            if ((peek(IDENT) || peek(NUMBER)) && peekNext(EQUALS)) {
                String key = peek(IDENT) ? expect(IDENT).text() : expect(NUMBER).text();
                expect(EQUALS);
                try {
                    Value val = parseValue();
                    putHandlingDuplicates(root, key, val);
                } catch (RuntimeException ex) {
                }
                continue;
            }
            p++;
        }
        return new ObjVal(root);
    }

    private Value parseValue() {
        if(peek(LBRACE)) return parseObject();
        if(peek(STRING)) return new StrVal(expect(STRING).text());
        if(peek(NUMBER)) return new NumVal(Double.parseDouble(expect(NUMBER).text()));
        if(peek(IDENT)) {
            String t = expect(IDENT).text();
            if (t.equalsIgnoreCase("yes")) return new BoolVal(true);
            if (t.equalsIgnoreCase("no")) return new BoolVal(false);
            return new StrVal(t);
        }
        throw new RuntimeException("Unexpected token: " + current());
    }

    private ObjVal parseObject() {
        expect(LBRACE);
        Map<String, Value> obj = new LinkedHashMap<>();

        while (!peek(RBRACE)) {

            if ((peek(IDENT) || peek(NUMBER)) && peekNext(EQUALS)) {
                String key = peek(IDENT) ? expect(IDENT).text() : expect(NUMBER).text();
                expect(EQUALS);
                try {
                    Value val = parseValue();
                    putHandlingDuplicates(obj, key, val);
                } catch (RuntimeException ex) {
                }
                continue;
            }

            if (peek(LBRACE) || peek(STRING) || peek(NUMBER) || peek(IDENT)) {
                try {
                    Value v = parseValue();
                    addAnonymous(obj, v);
                } catch (RuntimeException ex) {
                    p++;
                }
                continue;
            }

            p++;
        }



        expect(RBRACE);
        return new ObjVal(obj);
    }


    private void addAnonymous(Map<String, Value> map, Value val) {
        String key = "__items";
        Value existing = map.get(key);

        if (existing == null) {
            List<Value> list = new ArrayList<>();
            list.add(val);
            map.put(key, new ListVal(list));
            return;
        }

        if (existing instanceof ListVal lv) {
            lv.list().add(val);
            return;
        }

        // Shouldn't happen, but be safe
        List<Value> list = new ArrayList<>();
        list.add(existing);
        list.add(val);
        map.put(key, new ListVal(list));
    }


    private void putHandlingDuplicates(Map<String, Value> map, String key, Value val) {
        Value existing = map.get(key);

        if (existing == null) {
            map.put(key, val);
            return;
        }

        if (existing instanceof ListVal lv) {
            lv.list().add(val);
            return;
        }

        List<Value> list = new ArrayList<>();
        list.add(existing);
        list.add(val);
        map.put(key, new ListVal(list));
    }


    private Token expect(Token.Type type) {
        Token t = current();
        if (t.type() != type) {
            throw new RuntimeException("Expected " + type + " but got " + t.type() + " at pos " + t.pos());
        }
        p++;
        return t;
    }

    private boolean peek(Token.Type type) {
        return current().type() == type;
    }

    private boolean peekNext(Token.Type type) {
        if (p + 1 >= tokens.size()) return false;
        return tokens.get(p + 1).type() == type;
    }

    private Token current() {
        return tokens.get(Math.min(p, tokens.size() - 1));
    }
}
