package com.notare.feature.collab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * The compacted state of a collaborative note (step 15b, D-032).
 *
 * <p>One row per note, keyed by the note's id. {@code state} is a Yjs state a
 * new device can start from; every update up to {@code upToSeq} is already
 * folded into it and has been deleted from note_updates.
 *
 * <p>{@code plainText} is what search, notifications and sync read for a COLLAB
 * note, never notes.content. The server cannot read Yjs, so this is whatever
 * the last client SNAPSHOT supplied and can be stale until the next one.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "note_snapshots")
public class NoteSnapshot {

    /** The note's id. Assigned, not generated: there is at most one snapshot per note. */
    @Id
    @Column(name = "note_id", nullable = false, updatable = false)
    private Long noteId;

    @Column(name = "state", nullable = false)
    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    private byte[] state;

    @Column(name = "up_to_seq", nullable = false)
    private Long upToSeq;

    /** TEXT for the same reason as Note.content: understood by MySQL, PostgreSQL and H2. */
    @Column(name = "plain_text", columnDefinition = "TEXT")
    private String plainText;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
