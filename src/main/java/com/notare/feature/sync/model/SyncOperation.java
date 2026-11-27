package com.notare.feature.sync.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/** Records that one client operation was applied, so a retry is not applied twice. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "sync_operations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_sync_operations_owner_operation",
                columnNames = {"owner_id", "operation_id"}
        ),
        indexes = {
                @Index(name = "idx_sync_operations_created_at", columnList = "created_at")
        }
)
public class SyncOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "operation_id", nullable = false, length = 64, updatable = false)
    private String operationId;

    @Column(name = "note_id", nullable = false, updatable = false)
    private Long noteId;

    @Column(name = "result_version", nullable = false, updatable = false)
    private Long resultVersion;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

}
