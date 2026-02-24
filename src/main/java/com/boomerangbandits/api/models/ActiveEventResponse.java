package com.boomerangbandits.api.models;

import lombok.Data;

import java.util.List;

@Data
public class ActiveEventResponse {
    private boolean success;
    private boolean active;
    private List<EventDetails> events;
    private String error;
}
