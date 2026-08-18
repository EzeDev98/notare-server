package com.notare.feature.voiceshare.model;

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
 * A grant: "voice note V is shared with user U at level P".
 *
 * <p>Mirrors {@link com.notare.feature.share.model.NoteShare} exactly. This
 * row IS the authorization record for a voice note reachable by someone who
 * doesn't own it — the constraints here are doing real security work, not
 * just data hygiene.
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
        name = "voice_shares",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_voice_shares_voice_note_user",
                columnNames = {"voice_note_id", "shared_with_user_id"}
        ),
        indexes = {
                // "Voice notes shared with me" — the recipient's inbox listing.
                @Index(name = "idx_voice_shares_shared_with_user_id", columnList = "shared_with_user_id"),
                // "Who is this voice note shared with?" — the owner's per-voice-note listing.
                @Index(name = "idx_voice_shares_voice_note_id", columnList = "voice_note_id"),
                // Revoking every share to one person when a collaboration ends.
                @Index(name = "idx_voice_shares_owner_shared_with", columnList = "owner_id, shared_with_user_id")
        }
)
public class VoiceShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The voice note being shared. Fixed at creation — re-pointing a grant at a different voice note would be a silent privilege change. */
    @Column(name = "voice_note_id", nullable = false, updatable = false)
    private Long voiceNoteId;

    /**
     * The voice note's owner, copied onto the grant.
     *
     * <p>Denormalized on purpose, same reasoning as {@code NoteShare.ownerId}:
     * keeps this module's queries self-contained without joining into the
     * voicenote module's table. Safe because a voice note's owner never
     * changes.
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
     * <p>Reuses {@link SharePermission} from the note-share module rather than
     * duplicating it — confirmed reusable (READ/WRITE, no note-specific
     * semantics).
     *
     * <p>STRING, not ORDINAL: storing the name means reordering the constants
     * can never silently reinterpret existing rows.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SharePermission permission = SharePermission.READ;

    /** When the voice note was first shared with this user. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last permission change. */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // No @Version, same reasoning as NoteShare: the only writer to a grant is
    // the owner, from a UI where permission changes are rare and deliberate.
}
