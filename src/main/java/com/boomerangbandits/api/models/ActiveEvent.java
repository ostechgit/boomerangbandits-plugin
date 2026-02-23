package com.boomerangbandits.api.models;

import lombok.Data;
import java.util.List;

@Data
public class ActiveEvent {
    private boolean active;
    private List<EventDetails> events;
}
