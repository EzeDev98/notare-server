package com.notare.feature.user.repository;

import com.notare.feature.user.model.AccountDeletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AccountDeletionRepository extends JpaRepository<AccountDeletion, Long> {

    /** Deletions that have not moved for a while: what the recovery job picks up. */
    List<AccountDeletion> findByUpdatedAtBefore(LocalDateTime before);
}
