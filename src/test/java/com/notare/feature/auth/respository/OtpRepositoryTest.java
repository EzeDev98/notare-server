package com.notare.feature.auth.respository;

import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.auth.model.Otp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class OtpRepositoryTest {

    @Autowired
    private OtpRepository repository;

    private final LocalDateTime base =
            LocalDateTime.of(2026, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        repository.deleteAll();

        repository.saveAll(List.of(
                otp(
                        1L,
                        "old-reset",
                        OtpPurpose.PASSWORD_RESET,
                        base.plusMinutes(1),
                        false
                ),
                otp(
                        1L,
                        "latest-reset",
                        OtpPurpose.PASSWORD_RESET,
                        base.plusMinutes(5),
                        false
                ),
                otp(
                        1L,
                        "consumed-reset",
                        OtpPurpose.PASSWORD_RESET,
                        base.plusMinutes(10),
                        true
                ),
                otp(
                        1L,
                        "verification",
                        OtpPurpose.EMAIL_VERIFICATION,
                        base.plusMinutes(20),
                        false
                ),
                otp(
                        2L,
                        "other-user",
                        OtpPurpose.PASSWORD_RESET,
                        base.plusMinutes(30),
                        false
                )
        ));

        repository.flush();
    }

    @Test
    void returns_latest_unconsumed_otp_for_user_and_purpose() {
        assertThat(
                repository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        1L,
                        OtpPurpose.PASSWORD_RESET
                )
        )
                .isPresent()
                .get()
                .extracting(Otp::getCodeHash)
                .isEqualTo("latest-reset");
    }

    @Test
    void consumed_otp_is_ignored() {
        assertThat(
                repository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        1L,
                        OtpPurpose.PASSWORD_RESET
                )
        )
                .isPresent()
                .get()
                .extracting(Otp::getCodeHash)
                .isNotEqualTo("consumed-reset");
    }

    @Test
    void purpose_is_part_of_lookup_scope() {
        assertThat(
                repository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        1L,
                        OtpPurpose.EMAIL_VERIFICATION
                )
        )
                .isPresent()
                .get()
                .extracting(Otp::getCodeHash)
                .isEqualTo("verification");
    }

    @Test
    void user_is_part_of_lookup_scope() {
        assertThat(
                repository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        2L,
                        OtpPurpose.PASSWORD_RESET
                )
        )
                .isPresent()
                .get()
                .extracting(Otp::getCodeHash)
                .isEqualTo("other-user");
    }

    @Test
    void returns_empty_when_no_unconsumed_matching_otp_exists() {
        assertThat(
                repository.findTopByUserIdAndOtpPurposeAndConsumedFalseOrderByCreatedAtDesc(
                        999L,
                        OtpPurpose.PASSWORD_RESET
                )
        ).isEmpty();
    }

    private static Otp otp(
            Long userId,
            String codeHash,
            OtpPurpose purpose,
            LocalDateTime createdAt,
            boolean consumed
    ) {
        return new Otp(
                null,
                userId,
                codeHash,
                purpose,
                createdAt.plusHours(1),
                0,
                consumed,
                createdAt
        );
    }
}
