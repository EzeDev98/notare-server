package com.notare.feature.user.repository;

import com.notare.feature.user.model.AccountDeletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/** Data access for account deletions. */
public interface AccountDeletionRepository extends JpaRepository<AccountDeletion, Long> {

    List<AccountDeletion> findByUpdatedAtBefore(LocalDateTime before);
}
