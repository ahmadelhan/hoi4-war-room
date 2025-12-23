package com.warroom.model;

import java.util.List;
import java.util.Map;

public record CountrySnapshot(
        String tag,
        String saveDate,
        String ideology,
        String rulingParty,

        Double manpower,
        Double civilianFactories,
        Double militaryFactories,
        Double dockyards,

        Double politicalPower,
        Double stability,
        Double warSupport,
        Double commandPower,
        Double researchSlots,
        Double capitalStateId,
        Boolean major,

        Map<String, Integer> divisionsByTemplate,
        List<EquipmentAmount> stockpilesTop10

        ) { }
