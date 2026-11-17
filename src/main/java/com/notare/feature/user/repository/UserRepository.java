package com.notare.feature.user.repository;

import com.notare.feature.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Login's lookup: match either column.
     *
     * <p>The parameter names were previously (email, username), which is the
     * reverse of what they bind to. A derived query binds POSITIONALLY against
     * the property order in the method name — findByUsernameOrEmail means the
     * first argument is compared to username and the second to email — so the
     * old names described the opposite of the behaviour. Callers happened to
     * pass them in the correct order anyway, so nothing was broken; renamed so
     * the next reader is not misled into "fixing" a working call site.
     */
    Optional<User> findByUsernameOrEmail(String username, String email);

    /**
     * Exact email lookup, used to resolve the identifier on OTP verify and
     * resend. Emails are stored lower-cased at registration, so callers must
     * normalise before calling.
     */
    Optional<User> findByEmail(String email);

    boolean existsByNotarianId(String notarianId);

    Optional<User> findByUsername(String username);

    Optional<User> findByNotarianId(String notarianId);

    java.util.Optional<User> findByInternalAuthId(String internalAuthId);
}
