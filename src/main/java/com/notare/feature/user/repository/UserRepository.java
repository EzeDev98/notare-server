package com.notare.feature.user.repository;

import com.notare.feature.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    /**
     * Batch resolve ids to users, for GET /user/lookup.
     *
     * <p>One query for the whole set. The alternative a client is forced into
     * without this endpoint — a request per id — is the N+1 problem moved
     * across the network, where each round trip costs milliseconds rather than
     * microseconds.
     *
     * <p>Ids with no matching row are simply absent from the result. The caller
     * must not zip the returned list against the requested ids by position:
     * IN-clause ordering is not guaranteed and a single missing user shifts
     * every subsequent pairing. Build a Map keyed by id instead — the same
     * batch-and-map rule the rest of this codebase follows.
     */
    List<User> findByIdIn(Collection<Long> ids);

    java.util.Optional<User> findByInternalAuthId(String internalAuthId);

    /**
     * Find people to invite: partial, case-insensitive match on username, email,
     * or full display name (first + last). Excludes the caller (you never need to
     * invite yourself). The CONCAT(firstName, ' ', lastName) clause is what lets
     * "Jane Doe" match — searching the two name columns separately would miss a
     * full name typed with a space.
     *
     * <p>ESCAPE '!' on all three, pairing with LikeEscaper. This query is the
     * one where unescaped wildcards actually mattered: searchUsers returns an
     * empty page for a blank term SPECIFICALLY so the endpoint cannot be used
     * to list the directory. A term of "%" is not blank, so it passed that
     * check and then matched every row — walking around the privacy decision
     * with one character.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.id <> :excludeUserId
              AND (u.userStatus IS NULL OR u.userStatus NOT IN (
                    com.notare.feature.user.enums.UserStatus.SUSPENDED,
                    com.notare.feature.user.enums.UserStatus.DEACTIVATED))
              AND (
                    LOWER(u.username) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '!'
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '!'
                 OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '!'
              )
            """)
    Page<User> searchUsers(@Param("excludeUserId") Long excludeUserId,
                           @Param("term") String term,
                           Pageable pageable);
}