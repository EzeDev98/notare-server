package com.notare.feature.voicenote.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "voice_notes",
        indexes = {
                @Index(name = "idx_voice_notes_owner_id", columnList = "owner_id"),
                @Index(name = "idx_voice_notes_note_id", columnList = "note_id")
        }
)
public class VoiceNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    /**
     * Optional link to a text note. Null = standalone voice note. A plain Long
     * (not a @ManyToOne Note) for the same modulith-decoupling reason ownerId is
     * a Long: the voicenote module doesn't reach into the note module's entity.
     */
    @Column(name = "note_id")
    private Long noteId;

    /** The client's original filename, reused for the download filename. */
    @Column(name = "original_filename")
    private String originalFilename;

    /** MIME type of the stored audio, e.g. "audio/mpeg". Validated on upload. */
    @Column(name = "content_type", nullable = false)
    private String contentType;

    /**
     * Where the bytes live, as understood by the storage backend: a relative
     * path for local storage, or an object key for S3. The rest of the app never
     * parses this string — only the storage service interprets it. Immutable
     * once written.
     */
    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    /** Size of the stored file in bytes. */
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /**
     * Duration in seconds, supplied by the client's recorder (the client already
     * knows it). Nullable so an upload without it still succeeds; server-side
     * extraction is a later, optional enhancement.
     */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /** Upload date. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}