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

/** A file stored in the database. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file_blobs")
public class FileBlob {

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
