package com.notare.feature.user.repository;

import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        repository.saveAll(List.of(
                user("Ada", "Lovelace", "adal", "NTR-001", "ada@example.com"),
                user("Grace", "Hopper", "ghopper", "NTR-002", "grace@example.com"),
                user("Alan", "Turing", "aturing", "NTR-003", "alan@example.com")
        ));
    }

    @Test
    void findByUsernameOrEmail_matches_either_identifier() {
        assertThat(repository.findByUsernameOrEmail("adal", "missing@example.com"))
                .isPresent()
                .get()
                .extracting(User::getEmail)
                .isEqualTo("ada@example.com");

        assertThat(repository.findByUsernameOrEmail("missing", "grace@example.com"))
                .isPresent()
                .get()
                .extracting(User::getUsername)
                .isEqualTo("ghopper");
    }

    @Test
    void exact_and_unique_lookups_work() {
        assertThat(repository.findByEmail("ada@example.com")).isPresent();
        assertThat(repository.findByUsername("aturing")).isPresent();
        assertThat(repository.findByNotarianId("NTR-002")).isPresent();

        assertThat(repository.existsByNotarianId("NTR-003")).isTrue();
        assertThat(repository.existsByNotarianId("NTR-999")).isFalse();
    }

    @Test
    void findByIdIn_returns_matching_users_only() {
        List<User> users = repository.findAll(Sort.by("id"));

        Long firstId = users.get(0).getId();
        Long thirdId = users.get(2).getId();

        assertThat(repository.findByIdIn(List.of(firstId, thirdId, 999999L)))
                .extracting(User::getId)
                .containsExactlyInAnyOrder(firstId, thirdId);
    }

    @Test
    void searchUsers_is_case_insensitive_partial_and_excludes_caller() {
        Long adaId = repository.findByUsername("adal")
                .orElseThrow()
                .getId();

        Page<User> result = repository.searchUsers(
                adaId,
                "GRACE",
                PageRequest.of(0, 10, Sort.by("id"))
        );

        assertThat(result.getContent())
                .extracting(User::getUsername)
                .containsExactly("ghopper");

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void searchUsers_matches_full_display_name() {
        Long graceId = repository.findByUsername("ghopper")
                .orElseThrow()
                .getId();

        Page<User> result = repository.searchUsers(
                graceId,
                "ada lov",
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
                .extracting(User::getUsername)
                .containsExactly("adal");
    }

    private static User user(
            String firstName,
            String lastName,
            String username,
            String notarianId,
            String email
    ) {
        return new User(
                null,
                null,
                firstName,
                lastName,
                username,
                notarianId,
                email,
                "encoded-password",
                UserStatus.ACTIVE,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
