package com.notare.feature.auth.respository;

import com.notare.feature.auth.enums.OtpPurpose;
import com.notare.feature.auth.model.Otp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findTopByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(Long userId, OtpPurpose purpose);
}
