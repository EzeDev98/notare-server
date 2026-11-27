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

/** Marks a note that was deleted for good, so offline devices learn it is gone. */
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

    @Column(name = "note_id", nullable = false, updatable = false)
    private Long noteId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "deleted_at", nullable = false, updatable = false)
    private LocalDateTime deletedAt;
}
