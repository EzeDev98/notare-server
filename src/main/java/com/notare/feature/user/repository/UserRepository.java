package com.notare.feature.user.repository;

import com.notare.feature.user.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameOrEmail(String email, String username);

    boolean existsByNotarianId(String notarianId);

    Optional<User> findByUsername(String username);

    Optional<User> findByNotarianId(String notarianId);

    /**
     * Find people to invite: partial, case-insensitive match on username, email,
     * or full display name (first + last). Excludes the caller (you never need to
     * invite yourself). The CONCAT(firstName, ' ', lastName) clause is what lets
     * "Jane Doe" match — searching the two name columns separately would miss a
     * full name typed with a space.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.id <> :excludeUserId
              AND (
                    LOWER(u.username) LIKE LOWER(CONCAT('%', :term, '%'))
                 OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%'))
                 OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :term, '%'))
              )
            """)
    Page<User> searchUsers(@Param("excludeUserId") Long excludeUserId,
                           @Param("term") String term,
                           Pageable pageable);
}