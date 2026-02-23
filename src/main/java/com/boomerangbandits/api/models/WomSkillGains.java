package com.boomerangbandits.api.models;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Skill-specific gains from WiseOldMan API.
 * Shows which skills the clan is training today.
 *
 * WOM API: GET /groups/{id}/gained?period=day&metric={skill}
 */
@Getter
public class WomSkillGains {
    private String skill;
    @Setter
    private List<WomGroupStatistics.PlayerGain> gains;
    @Setter
    private long totalGained;
    @Setter
    private int activeMembers;

    public WomSkillGains(String skill) {
        this.skill = skill;
    }

}
