package com.notare.integration.support;

import com.notare.feature.auth.service.EmailService;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only EmailService that captures OTP codes in memory instead of
 * calling Mailjet. @Primary under the "test" profile so it replaces
 * MailjetEmailService in every @SpringBootTest that loads the full context.
 */
@Service
@Primary
@Profile("test")
public class CapturingEmailService implements EmailService {

    private final ConcurrentHashMap<String, String> codesByEmail = new ConcurrentHashMap<>();

    @Override
    public void sendEmail(String toEmail, String recipient, String otpCode) {
        codesByEmail.put(toEmail.toLowerCase(), otpCode);
    }

    /**
     * Blocks until a code has been captured for the given email, or throws
     * after the timeout. Needed because OTP dispatch happens via
     * UserRegisteredEventListener, which is @Async and fires AFTER_COMMIT —
     * the code may not exist yet the instant registration's HTTP response
     * returns.
     */
    public String awaitCode(String email) throws InterruptedException {
        String key = email.toLowerCase();
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            String code = codesByEmail.get(key);
            if (code != null) {
                return code;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("No OTP code captured for " + email + " within timeout.");
    }
}
