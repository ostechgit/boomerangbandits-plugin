package com.boomerangbandits.api.models;

import lombok.Getter;
import lombok.Setter;

/**
 * Points breakdown from player profile.
 * Nested inside PlayerProfile.points
 */
@Setter
@Getter
public class PointsBreakdown {
    private int total;
    private int lifetime;
    private int loot;
    private int skill;
    private int pvm;
    private int event;
    private int misc;

}
