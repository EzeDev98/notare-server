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

/** Deletes an account and everything it owns, in steps that can be repeated. */
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

    public void delete(User user, String confirmation) {
        if (confirmation == null || !user.getUsername().equals(confirmation.trim())) {
            throw new InvalidAccountRequestException("The username does not match.");
        }
        if (!deletions.existsById(user.getId())) {
            deletions.save(AccountDeletion.begin(user.getId(), user.getInternalAuthId()));
        }
        resume(user.getId());
    }

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
            return;
        }
        try {
            authClient.deleteUser(internalAuthId);
        } catch (InternalAuthCallException ex) {
            if (ex.getStatus() != HttpStatus.NOT_FOUND) {
                throw ex;
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
