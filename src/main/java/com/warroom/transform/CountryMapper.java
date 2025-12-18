package com.warroom.transform;

import com.warroom.model.CountrySnapshot;
import com.warroom.parser.ClausewitzParser;

import java.util.Locale;

public final class CountryMapper {

    private CountryMapper() {}

    public static CountrySnapshot from(
            String tag,
            String saveDate,
            ClausewitzParser.ObjVal countryObj,
            java.util.Map<String, String> divisionTemplateNames
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

        Double manpower = firstNum(
                countryObj,
                "manpower",
                "manpower_pool",
                "total_manpower",
                "manpower_total"
        );

        Double civ = firstNum(
                countryObj,
                "civilian_factories",
                "num_of_civilian_factories",
                "civ_factory_count",
                "civilian_factory_count"
        );

        Double mil = firstNum(
                countryObj,
                "military_factories",
                "num_of_military_factories",
                "mil_factory_count",
                "military_factory_count"
        );

        Double docks = firstNum(
                countryObj,
                "dockyards",
                "num_of_dockyards",
                "dockyard_count"
        );

        var divisionsByTemplate = extractDivisionsByTemplate(countryObj, divisionTemplateNames);

        return new CountrySnapshot(
                tag,
                saveDate,
                ideology,
                rulingParty,

                manpower,
                civ,
                mil,
                docks,

                politicalPower,
                stability,
                warSupport,
                commandPower,
                researchSlots,
                capital,
                major,

                divisionsByTemplate
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

    private static Double firstNum(ClausewitzParser.ObjVal obj, String... keys) {
        for (String k : keys) {
            Double v = getNum(obj, k);
            if (v != null) return v;
        }
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

    private static java.util.Map<String, Integer> extractDivisionsByTemplate(
            ClausewitzParser.ObjVal countryObj,
            java.util.Map<String, String> templateNames
    ) {
        ClausewitzParser.Value units = countryObj.map().get("units");
        if (units == null) return java.util.Map.of();

        java.util.Map<String, Integer> countsById = new java.util.HashMap<>();
        collectDivisionTemplateIds(units, countsById);

        java.util.Map<String, Integer> countsByName = new java.util.TreeMap<>();
        for (var e : countsById.entrySet()) {
            String id = e.getKey();
            String name = templateNames.getOrDefault(id, "Template " + id);
            countsByName.merge(name, e.getValue(), Integer::sum);
        }

        return countsByName;
    }


    private static void collectDivisionTemplateIds(ClausewitzParser.Value v,
                                                   java.util.Map<String, Integer> out) {
        if (v == null) return;

        if (v instanceof ClausewitzParser.ObjVal obj) {

            String templateId = extractTemplateId(obj.map().get("division_template_id"));
            if (templateId != null) {
                out.merge(templateId, 1, Integer::sum);
            }

            for (var child : obj.map().values()) {
                collectDivisionTemplateIds(child, out);
            }
            return;
        }

        if (v instanceof ClausewitzParser.ListVal lv) {
            for (var item : lv.list()) {
                collectDivisionTemplateIds(item, out);
            }
        }
    }

    private static String extractTemplateId(ClausewitzParser.Value v) {
        if (!(v instanceof ClausewitzParser.ObjVal outer)) return null;

        var inner = outer.map().get("id");
        if (inner instanceof ClausewitzParser.ObjVal innerObj) {
            var idVal = innerObj.map().get("id");
            if (idVal instanceof ClausewitzParser.NumVal nv) {
                return String.format(Locale.US, "%.0f", nv.v());
            }
        }

        var directId = outer.map().get("id");
        if (directId instanceof ClausewitzParser.NumVal nv2) {
            return String.format(Locale.US, "%.0f", nv2.v());
        }
        return null;
    }


    private static String getString(ClausewitzParser.ObjVal obj, String key) {
        var v = obj.map().get(key);
        if (v instanceof ClausewitzParser.StrVal sv) return sv.v();
        if (v instanceof ClausewitzParser.BoolVal bv) return String.valueOf(bv.v());
        if (v instanceof ClausewitzParser.NumVal nv) return String.format(Locale.US, "%s", nv.v());
        return null;
    }



}
