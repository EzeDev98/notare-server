package com.notare.feature.sync.dto.request;

import com.notare.feature.sync.enums.SyncOperationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One queued operation from an offline client.
 *
 * <p><b>Why one flat DTO instead of three.</b> CREATE, UPDATE and DELETE need
 * different fields, so the strictly correct modelling is three subtypes with
 * Jackson polymorphic deserialization (@JsonTypeInfo / @JsonSubTypes). That
 * would let bean validation enforce every rule declaratively — no nullable
 * fields, no service-side checks.
 *
 * <p>It's not what's here, and the reason is pragmatic rather than principled:
 * the union of fields is small, polymorphic binding is fiddly to configure and
 * harder for a client author to read, and the cost is a handful of
 * type-dependent checks in one place in the service. If a fourth or fifth
 * operation type ever appears, that calculation flips and this should become a
 * sealed hierarchy.
 *
 * <p>Consequence worth knowing: the annotations below can only express what is
 * true for EVERY type. "UPDATE must carry a version" is a cross-field rule and
 * lives in the service, surfacing as an INVALID result rather than a 400 —
 * which is the right behaviour anyway, since one malformed operation must not
 * reject the whole batch.
 */
@Getter
@Setter
@NoArgsConstructor
public class SyncOperationRequest {

    /**
     * The client's id for this operation — the idempotency key.
     *
     * <p>Required for every type. Without it a retried batch reapplies
     * everything, and updates come back as phantom conflicts against the user's
     * own writes. @NotBlank rather than merely @NotNull: an empty string would
     * pass a null check and then collide with every other empty string in the
     * ledger's unique constraint.
     */
    @NotBlank(message = "operationId is required.")
    @Size(max = 64, message = "operationId must be at most 64 characters.")
    private String operationId;

    @NotNull(message = "type is required (CREATE, UPDATE or DELETE).")
    private SyncOperationType type;

    /** CREATE only: the client's local id for the new note. Ignored for other types. */
    @Size(max = 64, message = "clientId must be at most 64 characters.")
    private String clientId;

    /** UPDATE and DELETE: which note. Null for CREATE — the note doesn't exist server-side yet. */
    private Long noteId;

    /** CREATE and UPDATE. Null is legitimate here: an empty note has no title. */
    @Size(max = 255, message = "title must be at most 255 characters.")
    private String title;

    /** CREATE and UPDATE. */
    private String content;

    /**
     * UPDATE only: the version the client's edit was based on.
     *
     * <p>This is what makes offline editing safe. It flows into the same
     * requireMatchingVersion check the online PUT uses, so a queued edit built
     * on stale state is caught by exactly the mechanism from Step 2 — one
     * conflict protocol, whether the client was online or has been in a tunnel
     * for an hour.
     */
    private Long version;
}