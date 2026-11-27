package com.notare.feature.user.repository;

import com.notare.feature.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/** Data access for users, including AI tidy's daily title allowance. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameOrEmail(String username, String email);

    Optional<User> findByEmail(String email);

    boolean existsByNotarianId(String notarianId);

    Optional<User> findByUsername(String username);

    Optional<User> findByNotarianId(String notarianId);

    java.util.Optional<User> findByInternalAuthId(String internalAuthId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE User u
            SET u.tidyTitlesUsed = CASE WHEN u.tidyTitleDay = :today THEN u.tidyTitlesUsed + 1 ELSE 1 END,
                u.tidyTitleDay = :today
            WHERE u.id = :id
              AND (u.tidyTitleDay IS NULL OR u.tidyTitleDay <> :today OR u.tidyTitlesUsed < :cap)
            """)
    int takeTidyTitle(@Param("id") Long id, @Param("today") LocalDate today, @Param("cap") int cap);
}
