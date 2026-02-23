package com.boomerangbandits.api.models;

import lombok.Data;

@Data
public class EventDetails {
    private String id;
    private String name;
    private String organiserRsn;
    private String eventType;
    private String description;
    private String eventPassword;
    private String challengePassword;
    private String location;
    private int world;
    private String startTime; // ISO 8601 UTC timestamp
    private String endTime;   // ISO 8601 UTC timestamp
}
