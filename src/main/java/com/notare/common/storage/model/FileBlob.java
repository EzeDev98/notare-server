package com.notare.common.storage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * `content` is plain `byte[]` mapped via @JdbcTypeCode(SqlTypes.LONGVARBINARY)
 * — NOT `@Lob`, and NOT a hardcoded `columnDefinition` (an earlier version of
 * this class used `columnDefinition = "bytea"`, which is Postgres-only DDL and
 * breaks schema generation against any other dialect — see below). `@Lob
 * byte[]` maps to Hibernate's BLOB sql-type-code, which on Postgres has
 * historically meant the `oid` large-object type (a separate large-object
 * API, pg_largeobject catalog, manual cleanup on delete) — almost certainly
 * not what you want here. LONGVARBINARY is the type code that exists
 * precisely so each dialect renders ITS OWN natural "large binary" column
 * instead of one hardcoded string: Postgres has no native varbinary-with-
 * length type, so its dialect maps LONGVARBINARY (and VARBINARY) straight to
 * `bytea` — the same type db/migration/V1__baseline_schema.sql already
 * declares for this column in production, where ddl-auto=validate means
 * Hibernate never generates DDL itself, only checks the entity against that
 * migration's real schema. MySQL's dialect maps LONGVARBINARY to LONGBLOB —
 * needed over the smaller BLOB/MEDIUMBLOB because MEDIUMBLOB's 16MB ceiling
 * sits under the 20MB voice-note cap (notare.voice.max-size, enforced
 * upstream by VoiceUploadValidator) while LONGBLOB's 4GB ceiling doesn't.
 * This one annotation is what lets dev's ddl-auto=update (against local
 * MySQL — application-dev.properties) and prod's ddl-auto=validate (against
 * Postgres) both work from the same entity, with no per-dialect branching:
 * each dialect just renders its own native type for the same logical type
 * code.
 *
 * Voice notes are capped at 20MB, so one row holding the full byte array is
 * fine — this is never a multi-GB blob.
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

    @Column(name = "content", nullable = false)
    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
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
