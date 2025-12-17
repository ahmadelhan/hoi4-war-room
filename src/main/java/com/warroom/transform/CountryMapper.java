package com.warroom.transform;

import com.warroom.model.CountrySnapshot;
import com.warroom.parser.ClausewitzParser;

import java.util.Locale;

public final class CountryMapper {

    private CountryMapper() {}

    public static CountrySnapshot from(
            String tag,
            String saveDate,
            ClausewitzParser.ObjVal countryObj
    ) {
        String rulingParty = getPathString(countryObj, "politics", "ruling_party");
        String ideology = rulingParty;
        Double politicalPower = getPathNum(countryObj, "politics", "political_power");
        Double stability = getNum(countryObj, "stability");
        Double warSupport = getNum(countryObj, "war_support");
        Double commandPower = getNum(countryObj, "command_power");
        Double researchSlots = getNum(countryObj, "research_slot");
        Double capital = getNum(countryObj, "capital");
        Boolean major = getBool(countryObj, "major");

        return new CountrySnapshot(
                tag,
                saveDate,
                ideology,
                rulingParty,
                politicalPower,
                stability,
                warSupport,
                commandPower,
                researchSlots,
                capital,
                major
        );
    }

    private static ClausewitzParser.Value getPath(ClausewitzParser.ObjVal root, String... path) {
        ClausewitzParser.Value cur = root;
        for (String key : path) {
            if (!(cur instanceof ClausewitzParser.ObjVal(java.util.Map<String, ClausewitzParser.Value> map))) return null;
            cur = map.get(key);
            if (cur == null) return null;
        }
        return cur;
    }

    private static String getPathString(ClausewitzParser.ObjVal root, String... path) {
        var v = getPath(root, path);
        if (v instanceof ClausewitzParser.StrVal(String v1)) return v1;
        if (v instanceof ClausewitzParser.BoolVal(boolean v1)) return String.valueOf(v1);
        if (v instanceof ClausewitzParser.NumVal(double v1)) return String.format(Locale.US, "%s", v1);
        return null;
    }

    private static Double getPathNum(ClausewitzParser.ObjVal root, String... path) {
        var v = getPath(root, path);
        if (v instanceof ClausewitzParser.NumVal(double v1)) return v1;
        return null;
    }

    private static Double getNum(ClausewitzParser.ObjVal obj, String key) {
        var v = obj.map().get(key);
        if (v instanceof ClausewitzParser.NumVal(double v1)) return v1;
        return null;
    }

    private static Boolean getBool(ClausewitzParser.ObjVal obj, String key) {
        var v = obj.map().get(key);
        if (v instanceof ClausewitzParser.BoolVal(boolean v1)) return v1;
        return null;
    }
}
