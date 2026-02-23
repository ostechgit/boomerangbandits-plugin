package com.boomerangbandits.api.models;

import lombok.Getter;

import java.util.List;

/**
 * Response from POST /api/wom/names/bulk (200 OK).
 */
@Getter
public class NameChangesResponse {
    private String status;
    private String code;
    private Data data;

    @Getter
    public static class Data {
        private int processed;
        private int renamed;
        private int skipped;
        private List<NameChangeError> errors;

    }

    @Getter
    public static class NameChangeError {
        private int index;
        private String oldName;
        private String newName;
        private String error;

    }
}
