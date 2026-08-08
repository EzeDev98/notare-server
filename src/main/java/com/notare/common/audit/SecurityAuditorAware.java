package com.notare.common.audit;

import com.notare.feature.user.model.UserDetail;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Tells Spring Data JPA who to record in @CreatedBy / @LastModifiedBy.
 *
 * <p>Typed to Long, matching the ownerId convention used throughout. A username
 * would be readable without a join, but usernames are MUTABLE — rename a user
 * and every historical audit row silently starts describing someone else, or
 * nobody. An audit trail that rewrites itself when a profile is edited is worse
 * than one that needs a lookup. Ids don't rot.
 *
 * <p>Lives in common because auditing applies to every module, and it needs the
 * principal type to do that. That's the same allowance common/security already
 * takes. It is NOT the thing Step 2 ruled out — that was a common EXCEPTION
 * carrying a feature DTO, which would have dragged domain types into the error
 * layer that every module depends on. Resolving the current user is
 * infrastructure; carrying a feature's DTO shape is not.
 */
@Component
public class SecurityAuditorAware implements AuditorAware<Long> {

    /**
     * Never returns Optional.empty().
     *
     * <p>That's the point of the sentinel: because every write records SOME
     * auditor, a null in these columns can only mean a row written before
     * auditing existed, or a genuine bug. If empty were returned for system
     * writes, null would mean three different things and be useless as a signal.
     */
    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!isRealUser(authentication)) {
            // No principal: an @Async worker, the scheduler, or registration.
            return Optional.of(SystemAuditor.ID);
        }

        // Pattern matching rather than a cast. The principal is NOT guaranteed
        // to be a UserDetail — Spring uses the String "anonymousUser" for
        // anonymous requests, and other filters can install their own types. A
        // ClassCastException here would be thrown during a JPA flush, surfacing
        // far from anything that looks like its cause and turning an ordinary
        // save into a baffling failure.
        if (authentication.getPrincipal() instanceof UserDetail userDetail) {
            return Optional.of(userDetail.getUser().getId());
        }

        return Optional.of(SystemAuditor.ID);
    }

    /**
     * <p>The AnonymousAuthenticationToken check is the trap in this method and
     * the reason it isn't a one-liner. Spring Security does NOT leave the
     * authentication null on an unauthenticated request — it installs an
     * anonymous token, and that token reports isAuthenticated() == true. So the
     * intuitive {@code authentication != null && authentication.isAuthenticated()}
     * passes for anonymous callers, and you end up trying to read a user id off
     * the string "anonymousUser".
     */
    private boolean isRealUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}