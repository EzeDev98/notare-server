package com.notare.common.audit;

import com.notare.feature.user.model.UserDetail;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Tells JPA auditing which user made a change. */
@Component
public class SecurityAuditorAware implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!isRealUser(authentication)) {
            return Optional.of(SystemAuditor.ID);
        }

        if (authentication.getPrincipal() instanceof UserDetail userDetail) {
            return Optional.of(userDetail.getUser().getId());
        }

        return Optional.of(SystemAuditor.ID);
    }

    private boolean isRealUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
