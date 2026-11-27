package com.notare.feature.sync.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** A client's queue of offline operations. */
@Getter
@Setter
@NoArgsConstructor
public class SyncBatchRequest {

    @NotEmpty(message = "operations must not be empty.")
    @Size(max = 200, message = "A batch may contain at most 200 operations.")
    @Valid
    private List<SyncOperationRequest> operations;
}
