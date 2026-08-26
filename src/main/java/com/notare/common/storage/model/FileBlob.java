package com.notare.common.storage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Backing store for {@link com.notare.common.storage.PostgresFileStorageService}.
 *
 * One row per stored file. The primary key IS the storage key that
 * {@code FileStorageService.store()} hands back to callers — the same opaque
 * string that {@code LocalFileStorageService} uses as a filename and
 * {@code R2FileStorageService} uses as an S3 object key. VoiceNote and every
 * other caller of FileStorageService never knows this table exists; they only
 * ever see the storageKey string, exactly as the Strategy interface intends.
 *
 * `content` is plain `byte[]` with an explicit columnDefinition, NOT `@Lob`.
 * On Postgres, Hibernate maps `@Lob byte[]` to the `oid` large-object type
 * (a separate large-object API, pg_largeobject catalog, manual cleanup on
 * delete) — almost certainly not what you want here. Plain `byte[]` with
 * `columnDefinition = "bytea"` maps to a normal `bytea` column: it lives in
 * the row, is deleted when the row is deleted, and needs no special handling.
 *
 * Voice notes are capped at 20MB (notare.voice.max-size, enforced upstream by
 * VoiceUploadValidator before this is ever reached), so one row holding the
 * full byte array is fine — this is never a multi-GB blob.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file_blobs")
public class FileBlob {

    /** Same opaque key FileStorageService.store() returns to its caller. */
    @Id
    @Column(name = "storage_key", nullable = false, updatable = false, length = 100)
    private String storageKey;

    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}