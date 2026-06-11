package com.nkd.nexbridge.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class NexError {
    private String code;
    private String message;
    @JsonProperty("http_status")
    private int httpStatus;
}
