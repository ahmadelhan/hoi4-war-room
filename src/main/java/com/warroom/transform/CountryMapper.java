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
            java.util.Map<String, String> divisionTemplateNames,
            java.util.Map<String, String> equipmentIdToName
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
        var stockpilesTop10 = extractMarketStockpileTop10(countryObj, equipmentIdToName);

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
                divisionsByTemplate,
                stockpilesTop10
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

        if (v instanceof ClausewitzParser.ObjVal(java.util.Map<String, ClausewitzParser.Value> map)) {

            String templateId = extractTemplateId(map.get("division_template_id"));
            if (templateId != null) {
                out.merge(templateId, 1, Integer::sum);
            }

            for (var child : map.values()) {
                collectDivisionTemplateIds(child, out);
            }
            return;
        }

        if (v instanceof ClausewitzParser.ListVal(java.util.List<ClausewitzParser.Value> list)) {
            for (var item : list) {
                collectDivisionTemplateIds(item, out);
            }
        }
    }

    private static String extractTemplateId(ClausewitzParser.Value v) {
        if (!(v instanceof ClausewitzParser.ObjVal(java.util.Map<String, ClausewitzParser.Value> map))) return null;

        var inner = map.get("id");
        if (inner instanceof ClausewitzParser.ObjVal(java.util.Map<String, ClausewitzParser.Value> map1)) {
            var idVal = map1.get("id");
            if (idVal instanceof ClausewitzParser.NumVal(double v1)) {
                return String.format(Locale.US, "%.0f", v1);
            }
        }

        var directId = map.get("id");
        if (directId instanceof ClausewitzParser.NumVal(double v1)) {
            return String.format(Locale.US, "%.0f", v1);
        }
        return null;
    }

    private static java.util.List<com.warroom.model.EquipmentAmount> extractMarketStockpileTop10(
            ClausewitzParser.ObjVal countryObj,
            java.util.Map<String, String> equipmentIdToName
    ) {
        var marketObj = com.warroom.parser.Clausewitz.path(countryObj, "equipment_market")
                .flatMap(com.warroom.parser.Clausewitz::obj)
                .orElse(null);
        if (marketObj == null) return java.util.List.of();

        var stockObj = com.warroom.parser.Clausewitz.path(marketObj, "market_stockpile")
                .flatMap(com.warroom.parser.Clausewitz::obj)
                .orElse(null);
        if (stockObj == null) return java.util.List.of();

        var equipsObj = com.warroom.parser.Clausewitz.path(stockObj, "equipments")
                .flatMap(com.warroom.parser.Clausewitz::obj)
                .orElse(null);
        if (equipsObj == null) return java.util.List.of();

        java.util.Map<String, Double> totals = new java.util.HashMap<>();

        var eqVal = equipsObj.map().get("equipment");
        for (var eqEntry : com.warroom.parser.Clausewitz.asObjList(eqVal)) {
            String eqId = extractEqIdFromEquipmentEntry(eqEntry);
            Double amt = extractAmountFromEntry(eqEntry);
            if (eqId != null && amt != null && amt > 0) {
                totals.merge(eqId, amt, Double::sum);
            }
        }

        java.util.List<com.warroom.model.EquipmentAmount> list = new java.util.ArrayList<>();
        for (var e : totals.entrySet()) {
            String id = e.getKey();
            double amt = e.getValue();
            String name = equipmentIdToName.getOrDefault(id, "equipment_id_" + id);
            list.add(new com.warroom.model.EquipmentAmount(name, amt));
        }

        list.sort((a, b) -> Double.compare(b.amount(), a.amount()));
        if (list.size() > 10) list = list.subList(0, 10);
        return list;
    }

    private static Double extractAmountFromEntry(ClausewitzParser.ObjVal obj) {
        var v = obj.map().get("amount");
        if (v instanceof ClausewitzParser.NumVal nv) return nv.v();
        return null;
    }

    private static String extractEqIdFromEquipmentEntry(ClausewitzParser.ObjVal obj) {
        // entry looks like: id={ id=123 type=70 }
        var idBlock = obj.map().get("id");
        if (!(idBlock instanceof ClausewitzParser.ObjVal idObj)) return null;

        var typeV = idObj.map().get("type");
        var idV = idObj.map().get("id");

        if (!(typeV instanceof ClausewitzParser.NumVal tnv)) return null;
        if ((int) tnv.v() != 70) return null;

        if (idV instanceof ClausewitzParser.NumVal inv) {
            return String.format(java.util.Locale.US, "%.0f", inv.v());
        }
        return null;
    }

}
