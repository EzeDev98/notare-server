package com.notare.feature.sync.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * A client's queue of offline operations, replayed in one round trip.
 *
 * <p>ORDER IS SIGNIFICANT and the server preserves it. A client that created a
 * note offline and then edited it twice sends CREATE, UPDATE, UPDATE — and
 * processing those out of order would try to update a note that doesn't exist
 * yet. This is the reason it's a List rather than a Set, and the reason the
 * service must not parallelise the loop for speed.
 */
@Getter
@Setter
@NoArgsConstructor
public class SyncBatchRequest {

    /**
     * The operations, oldest first.
     *
     * <p><b>The @Size cap is not a formality.</b> A batch endpoint without an
     * upper bound is an open invitation: every operation here becomes a
     * database transaction, so a single request carrying fifty thousand
     * operations holds a connection for minutes and can exhaust the pool for
     * everyone else. It's also a memory risk before any code runs, since
     * Jackson materialises the whole list first.
     *
     * <p>200 is a working number, not a law — it should comfortably clear a
     * normal offline session while staying well inside a sane request timeout.
     * A client with more queued work pages through it in several batches, which
     * is fine: the ledger makes each batch independently retriable, so a
     * failure part-way through costs one batch, not the queue.
     *
     * <p>@Valid cascades validation into each element. Without it the
     * annotations on SyncOperationRequest are silently ignored — a classic
     * omission, because nothing warns you and everything appears to work.
     */
    @NotEmpty(message = "operations must not be empty.")
    @Size(max = 200, message = "A batch may contain at most 200 operations.")
    @Valid
    private List<SyncOperationRequest> operations;
}