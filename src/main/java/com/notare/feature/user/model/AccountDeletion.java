package com.notare.feature.user.model;

import com.notare.feature.user.enums.AccountDeletionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Progress of one account deletion, so a deletion that stops halfway can be finished. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "account_deletions")
public class AccountDeletion {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "internal_auth_id")
    private String internalAuthId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountDeletionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_error")
    private String lastError;

    public static AccountDeletion begin(Long userId, String internalAuthId) {
        AccountDeletion deletion = new AccountDeletion();
        deletion.userId = userId;
        deletion.internalAuthId = internalAuthId;
        deletion.status = AccountDeletionStatus.STARTED;
        deletion.startedAt = LocalDateTime.now();
        deletion.updatedAt = deletion.startedAt;
        return deletion;
    }
}
