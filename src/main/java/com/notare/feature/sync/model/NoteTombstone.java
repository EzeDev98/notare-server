package com.notare.feature.sync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A marker that a note was permanently deleted, kept after the note itself is
 * gone.
 *
 * <p><b>The gap this closes.</b> Soft delete gives delta sync tombstones for
 * free, because a trashed note is still a row and flows through the ordinary
 * query. Permanent delete leaves NOTHING. A client that was offline across one
 * never hears about it — the note simply stops appearing in the feed, which is
 * indistinguishable from "nothing changed". The ghost note stays in that
 * device's cache forever, and the user sees a note that no longer exists on any
 * other device.
 *
 * <p>Absence is not a synchronizable fact. Anything a client must be told about
 * has to leave a row behind, which is why this table exists at all.
 *
 * <p>It carries no title or content — deliberately. A tombstone's job is
 * eviction, and keeping the body of a permanently deleted note would quietly
 * undo the deletion the user asked for. The id alone is the whole payload.
 *
 * <p>createdAt is set explicitly by the listener rather than by auditing,
 * because it must equal the moment of deletion — the value a client's cursor
 * will be compared against.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "note_tombstones",
        indexes = @Index(
                name = "idx_note_tombstones_owner_deleted_at",
                columnList = "owner_id, deleted_at")
)
public class NoteTombstone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The id the note used to have — what the client evicts from its cache. */
    @Column(name = "note_id", nullable = false, updatable = false)
    private Long noteId;

    /** Whose note it was. Scopes the delta query, same as everywhere else. */
    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    /** When it was destroyed. Compared against the client's cursor. */
    @Column(name = "deleted_at", nullable = false, updatable = false)
    private LocalDateTime deletedAt;
}