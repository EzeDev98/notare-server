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

/** A voice clip's transcription job and its result. */
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

    @Column(name = "voice_note_id", nullable = false, updatable = false)
    private Long voiceNoteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TranscriptionStatus status = TranscriptionStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    private Double confidence;

    @Column(length = 16)
    private String language;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "requested_language", nullable = false, length = 16)
    private String requestedLanguage = "auto";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Transcription(Long id, Long ownerId, Long voiceNoteId, TranscriptionStatus status, String transcript,
                         Double confidence, String language, String errorMessage,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.voiceNoteId = voiceNoteId;
        this.status = status;
        this.transcript = transcript;
        this.confidence = confidence;
        this.language = language;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
