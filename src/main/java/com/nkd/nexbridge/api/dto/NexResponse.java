package com.nkd.nexbridge.api.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class NexResponse<T> {
    private boolean success;
    private T data;
    private NexError error;
    private NexMeta meta;

    public static <T> NexResponse<T> ok(T data, NexMeta meta) {
        return NexResponse.<T>builder()
                .success(true)
                .data(data)
                .meta(meta)
                .build();
    }

    public static <T> NexResponse<T> error(NexError error, NexMeta meta) {
        return NexResponse.<T>builder()
                .success(false)
                .error(error)
                .meta(meta)
                .build();
    }
}
