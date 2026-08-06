package com.notare.feature.share.model;

import com.notare.feature.share.enums.SharePermission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * A grant: "note N is shared with user U at level P".
 *
 * <p>This row IS the authorization record. Steps 1-6 could get away with no
 * permission model at all because every note query was scoped by owner_id —
 * ownership was the only access anyone had. This table is the first thing that
 * makes a note reachable by someone who doesn't own it, so the constraints on
 * it are doing real security work, not just data hygiene.
 *
 * <p>Ids are plain Longs, not @ManyToOne relationships — same module-decoupling
 * rule as Note, VoiceNote, Transcription and Collaboration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "note_shares",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_note_shares_note_user",
                columnNames = {"note_id", "shared_with_user_id"}
        ),
        indexes = {
                // "Notes shared with me" — the recipient's inbox listing.
                @Index(name = "idx_note_shares_shared_with_user_id", columnList = "shared_with_user_id"),
                // "Who is this note shared with?" — the owner's per-note listing.
                @Index(name = "idx_note_shares_note_id", columnList = "note_id"),
                // Revoking every share to one person when a collaboration ends (7F).
                @Index(name = "idx_note_shares_owner_shared_with", columnList = "owner_id, shared_with_user_id")
        }
)
public class NoteShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The note being shared. Fixed at creation — re-pointing a grant at a different note would be a silent privilege change. */
    @Column(name = "note_id", nullable = false, updatable = false)
    private Long noteId;

    /**
     * The note's owner, copied onto the grant.
     *
     * <p>Denormalized on purpose. It's derivable by joining notes, but every
     * owner-side query ("my shares", "revoke everything I gave this person")
     * would then need that join, and the 7F cleanup would have to reach into
     * the note module's table to find its own rows. Storing it keeps this
     * module's queries self-contained. The duplication is safe because a note's
     * owner never changes — Note.ownerId is itself updatable = false.
     */
    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    /** The recipient. Also fixed: moving a grant to another user is a revoke plus a new share, not an edit. */
    @Column(name = "shared_with_user_id", nullable = false, updatable = false)
    private Long sharedWithUserId;

    /**
     * The one genuinely mutable field — the owner can promote READ to WRITE or
     * demote back without dropping and recreating the grant.
     *
     * <p>STRING, not ORDINAL, like every other enum column here: storing the
     * name means reordering the constants can never silently reinterpret
     * existing rows. On a permissions column that would be a privilege bug, not
     * just bad data.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SharePermission permission = SharePermission.READ;

    /** When the note was first shared with this user. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last permission change. */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // No @Version. The only writer to a grant is the owner, from a UI where
    // permission changes are rare and deliberate — there's no concurrent-edit
    // scenario to protect against. Note itself keeps its @Version; that's what
    // guards the note CONTENT when a collaborator and the owner type at once.
}