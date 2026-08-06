package com.notare.feature.transcription.model;

import com.notare.feature.transcription.enums.TranscriptionStatus;
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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "transcriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_transcriptions_voice_note_id",
                columnNames = "voice_note_id"
        ),
        indexes = @Index(name = "idx_transcriptions_owner_id", columnList = "owner_id")
)
public class Transcription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    /**
     * The voice note this transcribes. A plain Long (decoupled, like everywhere
     * else). The unique constraint above enforces one transcription per voice
     * note — a re-transcribe updates this row rather than inserting a second.
     */
    @Column(name = "voice_note_id", nullable = false, updatable = false)
    private Long voiceNoteId;

    /**
     * EnumType.STRING is deliberate: the status name ("PENDING") is stored, not
     * its ordinal position (0). With ORDINAL, reordering or inserting an enum
     * constant later would silently remap every existing row to the wrong
     * status. STRING is stable against enum edits.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TranscriptionStatus status = TranscriptionStatus.PENDING;

    /** Populated when COMPLETED. */
    @Column(columnDefinition = "TEXT")
    private String transcript;

    /** Engine confidence 0.0–1.0, populated when COMPLETED. */
    private Double confidence;

    /** Detected/So language code, e.g. "en". Populated when COMPLETED. */
    @Column(length = 16)
    private String language;

    /** Populated when FAILED, to explain what went wrong. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** When the job was submitted. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last status change. */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}