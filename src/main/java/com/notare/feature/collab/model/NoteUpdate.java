package com.notare.feature.collab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One Yjs update, stored as the opaque bytes a client sent (step 15b, D-032).
 *
 * <p>The server never reads {@code payload}: it assigns the next {@code seq},
 * stores the bytes, acknowledges the sender and forwards them to the other
 * sessions of the note. Clients merge. Ids are plain Longs, not relationships,
 * for the same module-decoupling reason as Note and NoteShare.
 *
 * <p>The unique (note_id, seq) constraint is the guard against two writers
 * being handed the same seq, and its index serves "updates after seq N".
 *
 * <p>{@code payload} uses LONGVARBINARY for the same reason FileBlob.content
 * does: each dialect renders its own natural binary column (bytea on Postgres,
 * LONGBLOB on MySQL), so dev's ddl-auto=update and prod's ddl-auto=validate
 * both work from this one entity.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "note_updates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_note_updates_note_seq",
                columnNames = {"note_id", "seq"}
        )
)
public class NoteUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false, updatable = false)
    private Long noteId;

    /** Per note, increasing. Assigned by the server, never by the client. */
    @Column(name = "seq", nullable = false, updatable = false)
    private Long seq;

    @Column(name = "payload", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    private byte[] payload;

    /** The user whose session sent this update. */
    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
