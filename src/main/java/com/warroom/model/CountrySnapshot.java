package com.warroom.model;

public record CountrySnapshot(
        String tag,
        String saveDate,
        String ideology,
        String rulingParty,
        Double politicalPower,
        Double stability,
        Double warSupport,
        Double commandPower,
        Double researchSlots,
        Double capitalStateId,
        Boolean major
) { }
