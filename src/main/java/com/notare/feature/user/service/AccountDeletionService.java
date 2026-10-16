package com.notare.feature.user.service;

import com.notare.client.service.InternalAuthClient;
import com.notare.common.exception.InternalAuthCallException;
import com.notare.common.exception.InvalidAccountRequestException;
import com.notare.feature.user.enums.AccountDeletionStatus;
import com.notare.feature.user.event.UserDeletionRequested;
import com.notare.feature.user.event.UserDeletionSweep;
import com.notare.feature.user.model.AccountDeletion;
import com.notare.feature.user.model.User;
import com.notare.feature.user.repository.AccountDeletionRepository;
import com.notare.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Deletes an account for good (step 17g, B-03), in steps that can be repeated: each step runs in its
 * own transaction and is recorded in account_deletions before the next begins.
 *
 * <pre>
 * STARTED       publish UserDeletionRequested: every module deletes what it owns      -> DATA_DELETED
 * DATA_DELETED  publish UserDeletionSweep: clear what the deletes left behind          -> SWEPT
 * SWEPT         delete the auth user (already gone counts as done)                     -> AUTH_DELETED
 * AUTH_DELETED  delete the local user and this progress row
 * </pre>
 *
 * <p>A failure keeps the progress row. Calling again, or the recovery job, continues from the step
 * that failed. This class holds no transaction of its own: each step opens one, so a failure never
 * rolls back a step that already finished.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private static final int ERROR_MAX = 500;

    private final UserRepository users;
    private final AccountDeletionRepository deletions;
    private final InternalAuthClient authClient;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    /** The person asked to delete their account. Typing the username is the confirmation. */
    public void delete(User user, String confirmation) {
        if (confirmation == null || !user.getUsername().equals(confirmation.trim())) {
            throw new InvalidAccountRequestException("The username does not match.");
        }
        if (!deletions.existsById(user.getId())) {
            deletions.save(AccountDeletion.begin(user.getId(), user.getInternalAuthId()));
        }
        resume(user.getId());
    }

    /** Continue a deletion from its recorded step. Safe to call again. */
    public void resume(Long userId) {
        try {
            AccountDeletion progress = deletions.findById(userId).orElse(null);
            while (progress != null && progress.getStatus() != null) {
                step(progress);
                progress = deletions.findById(userId).orElse(null);
            }
        } catch (RuntimeException ex) {
            recordError(userId, ex);
            throw ex;
        }
    }

    private void step(AccountDeletion progress) {
        Long userId = progress.getUserId();
        switch (progress.getStatus()) {
            case STARTED -> tx.executeWithoutResult(status -> {
                events.publishEvent(new UserDeletionRequested(userId));
                advance(userId, AccountDeletionStatus.DATA_DELETED);
            });
            case DATA_DELETED -> tx.executeWithoutResult(status -> {
                events.publishEvent(new UserDeletionSweep(userId));
                advance(userId, AccountDeletionStatus.SWEPT);
            });
            case SWEPT -> {
                deleteAuthUser(progress.getInternalAuthId());
                tx.executeWithoutResult(status -> advance(userId, AccountDeletionStatus.AUTH_DELETED));
            }
            case AUTH_DELETED -> tx.executeWithoutResult(status -> {
                users.deleteById(userId);
                deletions.deleteById(userId);
                log.info("Account {} deleted", userId);
            });
        }
    }

    private void advance(Long userId, AccountDeletionStatus next) {
        deletions.findById(userId).ifPresent(progress -> {
            progress.setStatus(next);
            progress.setUpdatedAt(LocalDateTime.now());
            progress.setLastError(null);
            deletions.save(progress);
        });
    }

    private void deleteAuthUser(String internalAuthId) {
        if (internalAuthId == null) {
            return; // Never linked to an auth user.
        }
        try {
            authClient.deleteUser(internalAuthId);
        } catch (InternalAuthCallException ex) {
            if (ex.getStatus() != HttpStatus.NOT_FOUND) {
                throw ex; // Not found: a previous attempt already deleted it.
            }
        }
    }

    private void recordError(Long userId, RuntimeException ex) {
        String message = String.valueOf(ex.getMessage());
        String shortened = message.length() > ERROR_MAX ? message.substring(0, ERROR_MAX) : message;
        tx.executeWithoutResult(status -> deletions.findById(userId).ifPresent(progress -> {
            progress.setLastError(shortened);
            progress.setUpdatedAt(LocalDateTime.now());
            deletions.save(progress);
        }));
        log.warn("Account deletion for {} stopped at a step and will be retried: {}", userId, shortened);
    }
}
