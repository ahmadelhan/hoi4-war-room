package com.warroom.transform;
import com.warroom.parser.Clausewitz;

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
        String rulingParty = Clausewitz.pathStr(countryObj, "politics", "ruling_party").orElse(null);
        String ideology = rulingParty;
        Double politicalPower = Clausewitz.pathNum(countryObj, "politics", "political_power").orElse(null);
        Double stability = Clausewitz.get(countryObj, "stability").flatMap(Clausewitz::num).orElse(null);
        Double warSupport = Clausewitz.get(countryObj, "war_support").flatMap(Clausewitz::num).orElse(null);
        Double commandPower = Clausewitz.get(countryObj, "command_power").flatMap(Clausewitz::num).orElse(null);
        Double researchSlots = Clausewitz.pathNum(countryObj, "research_slot").orElse(null);
        Double capital = Clausewitz.get(countryObj, "capital").flatMap(Clausewitz::num).orElse(null);
        Boolean major = Clausewitz.get(countryObj, "major").flatMap(Clausewitz::bool).orElse(null);

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




}
