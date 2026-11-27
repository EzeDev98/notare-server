package com.notare.feature.sync.dto.request;

import com.notare.feature.sync.enums.SyncOperationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One queued operation from an offline client. */
@Getter
@Setter
@NoArgsConstructor
public class SyncOperationRequest {

    @NotBlank(message = "operationId is required.")
    @Size(max = 64, message = "operationId must be at most 64 characters.")
    private String operationId;

    @NotNull(message = "type is required (CREATE, UPDATE, DELETE or CLIP_DELETE).")
    private SyncOperationType type;

    @Size(max = 64, message = "clientId must be at most 64 characters.")
    private String clientId;

    private Long noteId;

    private String content;

    private Long clipId;
}
