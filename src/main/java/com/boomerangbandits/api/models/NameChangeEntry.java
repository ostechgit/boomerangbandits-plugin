package com.boomerangbandits.api.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/**
 * A single name change entry for bulk submission to the backend.
 * Detected via RuneLite's NameableNameChanged event (friends/ignore list).
 */
@Data
public class NameChangeEntry {
    @SerializedName("oldName")
    private final String oldName;

    @SerializedName("newName")
    private final String newName;
}
