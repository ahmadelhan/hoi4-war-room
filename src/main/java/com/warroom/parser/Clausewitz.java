package com.warroom.parser;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Clausewitz {

    private Clausewitz() {}

    public static Optional<ClausewitzParser.Value> get(ClausewitzParser.ObjVal obj, String key) {
        if (obj == null || key == null) return Optional.empty();
        return Optional.ofNullable(obj.map().get(key));
    }

    public static Optional<ClausewitzParser.ObjVal> obj(ClausewitzParser.Value v) {
        return (v instanceof ClausewitzParser.ObjVal o) ? Optional.of(o) : Optional.empty();
    }

    public static Optional<ClausewitzParser.ListVal> list(ClausewitzParser.Value v) {
        return (v instanceof ClausewitzParser.ListVal l) ? Optional.of(l) : Optional.empty();
    }

    public static Optional<String> str(ClausewitzParser.Value v) {
        if (v instanceof ClausewitzParser.StrVal s) return Optional.ofNullable(s.v());
        if (v instanceof ClausewitzParser.BoolVal b) return Optional.of(String.valueOf(b.v()));
        if (v instanceof ClausewitzParser.NumVal n) return Optional.of(String.valueOf(n.v()));
        return Optional.empty();
    }

    public static Optional<Double> num(ClausewitzParser.Value v) {
        if (v instanceof ClausewitzParser.NumVal n) return Optional.of(n.v());
        return Optional.empty();
    }

    public static Optional<Boolean> bool(ClausewitzParser.Value v) {
        if (v instanceof ClausewitzParser.BoolVal b) return Optional.of(b.v());
        return Optional.empty();
    }

    public static Optional<ClausewitzParser.Value> path(ClausewitzParser.ObjVal root, String... keys) {
        ClausewitzParser.Value cur = root;
        for (String k : keys) {
            if (!(cur instanceof ClausewitzParser.ObjVal o)) return Optional.empty();
            cur = o.map().get(k);
            if (cur == null) return Optional.empty();
        }
        return Optional.of(cur);
    }

    public static Optional<Double> pathNum(ClausewitzParser.ObjVal root, String... keys) {
        return path(root, keys).flatMap(Clausewitz::num);
    }

    public static Optional<String> pathStr(ClausewitzParser.ObjVal root, String... keys) {
        return path(root, keys).flatMap(Clausewitz::str);
    }

    public static Optional<Boolean> pathBool(ClausewitzParser.ObjVal root, String... keys) {
        return path(root, keys).flatMap(Clausewitz::bool);
    }

    public static List<ClausewitzParser.ObjVal> asObjList(ClausewitzParser.Value v) {
        if (v instanceof ClausewitzParser.ObjVal one) return List.of(one);
        if (v instanceof ClausewitzParser.ListVal lv) {
            return lv.list().stream()
                    .filter(it -> it instanceof ClausewitzParser.ObjVal)
                    .map(it -> (ClausewitzParser.ObjVal) it)
                    .toList();
        }
        return List.of();
    }
}
