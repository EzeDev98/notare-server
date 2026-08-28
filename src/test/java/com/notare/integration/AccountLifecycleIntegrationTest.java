package com.notare.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.integration.support.CapturingEmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class AccountLifecycleIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CapturingEmailService emailService;

    @Test
    void account_can_register_login_access_protected_resource_and_logout() throws Exception {
        String username =
                "integration_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        String email =
                username + "@example.com";

        String password =
                "IntegrationPassword123!";

        String registrationRequest = """
                {
                  "firstName": "Integration",
                  "lastName": "Tester",
                  "username": "%s",
                  "email": "%s",
                  "password": "%s",
                  "confirmPassword": "%s"
                }
                """.formatted(username, email, password, password);

        ResponseEntity<String> registration =
                restTemplate.postForEntity(
                        url("/api/v1/user/register"),
                        json(registrationRequest),
                        String.class
                );

        assertThat(registration.getStatusCode())
                .isIn(
                        HttpStatus.OK,
                        HttpStatus.CREATED
                );

        String otpCode = emailService.awaitCode(email);

        String verifyOtpRequest = """
                {
                  "notarianIdOrEmail": "%s",
                  "code": "%s"
                }
                """.formatted(email, otpCode);

        ResponseEntity<String> verifyOtp =
                restTemplate.postForEntity(
                        url("/api/v1/otp/verify-otp"),
                        json(verifyOtpRequest),
                        String.class
                );

        assertThat(verifyOtp.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String loginRequest = """
                {
                  "usernameOrEmail": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);

        ResponseEntity<String> login =
                restTemplate.postForEntity(
                        url("/api/v1/login/login"),
                        json(loginRequest),
                        String.class
                );

        assertThat(login.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String token =
                extractToken(login.getBody());

        assertThat(token)
                .isNotBlank();

        HttpHeaders authenticatedHeaders =
                new HttpHeaders();

        authenticatedHeaders.setBearerAuth(token);

        ResponseEntity<String> authenticatedResponse =
                restTemplate.exchange(
                        url("/api/v1/user/me"),
                        HttpMethod.GET,
                        new HttpEntity<>(authenticatedHeaders),
                        String.class
                );

        assertThat(authenticatedResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> logout =
                restTemplate.exchange(
                        url("/api/v1/logout/logout"),
                        HttpMethod.POST,
                        new HttpEntity<>(authenticatedHeaders),
                        String.class
                );

        assertThat(logout.getStatusCode())
                .isIn(
                        HttpStatus.OK,
                        HttpStatus.NO_CONTENT
                );

        ResponseEntity<String> afterLogout =
                restTemplate.exchange(
                        url("/api/v1/user/me"),
                        HttpMethod.GET,
                        new HttpEntity<>(authenticatedHeaders),
                        String.class
                );

        assertThat(afterLogout.getStatusCode())
                .isIn(
                        HttpStatus.UNAUTHORIZED,
                        HttpStatus.FORBIDDEN
                );
    }

    @Test
    void protected_resource_rejects_request_without_authentication() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        url("/api/v1/user/me"),
                        String.class
                );

        assertThat(response.getStatusCode())
                .isIn(
                        HttpStatus.UNAUTHORIZED,
                        HttpStatus.FORBIDDEN
                );
    }

    @Test
    void login_rejects_invalid_credentials() {
        String loginRequest = """
                {
                  "username": "definitely-not-a-real-user",
                  "password": "WrongPassword123!"
                }
                """;

        ResponseEntity<String> response =
                restTemplate.postForEntity(
                        url("/api/v1/login/login"),
                        json(loginRequest),
                        String.class
                );

        assertThat(response.getStatusCode())
                .isIn(
                        HttpStatus.UNAUTHORIZED,
                        HttpStatus.BAD_REQUEST
                );
    }

    private HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                org.springframework.http.MediaType.APPLICATION_JSON
        );

        return new HttpEntity<>(body, headers);
    }

    private String extractToken(String body) throws Exception {
        assertThat(body).isNotBlank();

        JsonNode json =
                objectMapper.readTree(body);

        if (json.hasNonNull("token")) {
            return json.get("token").asText();
        }

        if (json.hasNonNull("accessToken")) {
            return json.get("accessToken").asText();
        }

        if (json.has("data")
                && json.get("data").hasNonNull("token")) {
            return json.get("data").get("token").asText();
        }

        if (json.has("data")
                && json.get("data").hasNonNull("accessToken")) {
            return json.get("data").get("accessToken").asText();
        }

        throw new AssertionError(
                "Login response did not contain token/accessToken: " + body
        );
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}