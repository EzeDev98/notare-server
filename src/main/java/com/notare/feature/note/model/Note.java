package com.notare.feature.note.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/** A note, with its AI tidy state. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "notes",
        indexes = {
                @Index(name = "idx_notes_owner_id", columnList = "owner_id"),
                @Index(name = "idx_notes_owner_deleted", columnList = "owner_id, deleted"),
                @Index(name = "idx_notes_purge_at", columnList = "purge_at"),
                @Index(name = "idx_notes_owner_updated_at", columnList = "owner_id, updated_at")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notes_owner_client_id",
                columnNames = {"owner_id", "client_id"}
        )
)
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "client_id", length = 64, updatable = false)
    private String clientId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private boolean deleted;

    private LocalDateTime deletedAt;

    private LocalDateTime purgeAt;

    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "tidied_version")
    private Long tidiedVersion;

    @Column(name = "ai_title")
    private String aiTitle;

    @Column(name = "pre_tidy_content", columnDefinition = "TEXT")
    private String preTidyContent;

    @Column(name = "tidy_attempts", nullable = false)
    private int tidyAttempts;

    @Column(name = "tidy_next_at")
    private LocalDateTime tidyNextAt;
}
