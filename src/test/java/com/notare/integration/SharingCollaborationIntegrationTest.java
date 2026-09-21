package com.notare.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
class SharingCollaborationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void note_share_can_be_created_accessed_and_revoked() throws Exception {
        TestUser owner = registerAndLogin("owner");
        TestUser recipient = registerAndLogin("recipient");

        // shareNote requires the recipient to already be an accepted
        // collaborator (NoteShareServiceImpl's third gate) — without this,
        // the POST /shares call below 400s with InvalidNoteShareException.
        String collaborationRequest = """
                {
                  "collaboratorId": %d
                }
                """.formatted(recipient.userId());

        ResponseEntity<String> invitation =
                exchange(
                        HttpMethod.POST,
                        "/api/v1/collaborations",
                        owner.token(),
                        collaborationRequest
                );

        assertThat(invitation.getStatusCode())
                .isIn(
                        HttpStatus.OK,
                        HttpStatus.CREATED
                );

        ResponseEntity<String> invitationsReceived =
                exchange(
                        HttpMethod.GET,
                        "/api/v1/collaborations/received",
                        recipient.token(),
                        null
                );

        assertThat(invitationsReceived.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String collaborationId =
                extractIdFromFirstArrayElement(invitationsReceived.getBody());

        assertThat(collaborationId).isNotBlank();

        ResponseEntity<String> acceptCollaboration =
                exchange(
                        HttpMethod.POST,
                        "/api/v1/collaborations/" + collaborationId + "/accept",
                        recipient.token(),
                        null
                );

        assertThat(acceptCollaboration.getStatusCode())
                .isIn(
                        HttpStatus.OK,
                        HttpStatus.NO_CONTENT
                );

        String noteRequest = """
                {
                  "content": "Integration test shared note"
                }
                """;

        ResponseEntity<String> noteResponse =
                exchange(
                        HttpMethod.POST,
                        "/api/v1/notes",
                        owner.token(),
                        noteRequest
                );

        assertThat(noteResponse.getStatusCode())
                .isIn(
                        HttpStatus.OK,
                        HttpStatus.CREATED
                );

        String noteId =
                extractId(noteResponse.getBody());

        assertThat(noteId).isNotBlank();

        String shareRequest = """
                {
                  "sharedWithUserId": %d,
                  "permission": "READ"
                }
                """.formatted(recipient.userId());

        ResponseEntity<String> shareResponse =
                exchange(
                        HttpMethod.POST,
                        "/api/v1/notes/" + noteId + "/shares",
                        owner.token(),
                        shareRequest
                );

        assertThat(shareResponse.getStatusCode())
                .isIn(
                        HttpStatus.OK,
                        HttpStatus.CREATED
                );

        ResponseEntity<String> sharedNote =
                exchange(
                        HttpMethod.GET,
                        "/api/v1/shared-notes/" + noteId,
                        recipient.token(),
                        null
                );

        assertThat(sharedNote.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(sharedNote.getBody())
                .contains("Integration test shared note");

        ResponseEntity<String> shares =
                exchange(
                        HttpMethod.GET,
                        "/api/v1/notes/" + noteId + "/shares",
                        owner.token(),
                        null
                );

        assertThat(shares.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> revoke =
                exchange(
                        HttpMethod.DELETE,
                        "/api/v1/notes/" + noteId
                                + "/shares/"
                                + recipient.userId(),
                        owner.token(),
                        null
                );

        assertThat(revoke.getStatusCode())
                .isIn(
                        HttpStatus.OK,
                        HttpStatus.NO_CONTENT
                );

        ResponseEntity<String> afterRevoke =
                exchange(
                        HttpMethod.GET,
                        "/api/v1/shared-notes/" + noteId,
                        recipient.token(),
                        null
                );

        assertThat(afterRevoke.getStatusCode())
                .isIn(
                        HttpStatus.FORBIDDEN,
                        HttpStatus.NOT_FOUND
                );
    }

    @Test
    void collaboration_invitation_can_be_sent_and_accepted() throws Exception {
        TestUser owner = registerAndLogin("collaboration-owner");
        TestUser collaborator = registerAndLogin("collaboration-user");

        String request = """
                {
                  "collaboratorId": %d
                }
                """.formatted(collaborator.userId());

        ResponseEntity<String> invitation =
                exchange(
                        HttpMethod.POST,
                        "/api/v1/collaborations",
                        owner.token(),
                        request
                );

        assertThat(invitation.getStatusCode())
                .isIn(
                        HttpStatus.OK,
                        HttpStatus.CREATED
                );

        ResponseEntity<String> invitations =
                exchange(
                        HttpMethod.GET,
                        "/api/v1/collaborations/received",
                        collaborator.token(),
                        null
                );

        assertThat(invitations.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String collaborationId =
                extractIdFromFirstArrayElement(
                        invitations.getBody()
                );

        assertThat(collaborationId)
                .isNotBlank();

        ResponseEntity<String> accept =
                exchange(
                        HttpMethod.POST,
                        "/api/v1/collaborations/"
                                + collaborationId
                                + "/accept",
                        collaborator.token(),
                        null
                );

        assertThat(accept.getStatusCode())
                .isIn(
                        HttpStatus.OK,
                        HttpStatus.NO_CONTENT
                );

        // There is no bare GET /collaborations endpoint — CollaborationController
        // only exposes /sent (invitations I sent) and /received (invitations I
        // was sent). The collaborator just accepted an invitation from owner, so
        // it's still on the collaborator's "received" list — accepting changes
        // its status, it doesn't remove the row from this listing.
        ResponseEntity<String> collaborations =
                exchange(
                        HttpMethod.GET,
                        "/api/v1/collaborations/received",
                        collaborator.token(),
                        null
                );

        assertThat(collaborations.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(collaborations.getBody())
                .contains(String.valueOf(owner.userId()));
    }

    private TestUser registerAndLogin(String prefix)
            throws Exception {

        String username =
                prefix + "_"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8);

        String email =
                username + "@example.com";

        String password =
                "IntegrationPassword123!";

        String registration = """
                {
                  "firstName": "Integration",
                  "lastName": "Tester",
                  "username": "%s",
                  "email": "%s",
                  "password": "%s",
                  "confirmPassword": "%s"
                }
                """.formatted(username, email, password, password);

        ResponseEntity<String> registrationResponse =
                restTemplate.postForEntity(
                        url("/api/v1/user/register"),
                        json(registration),
                        String.class
                );

        assertThat(registrationResponse.getStatusCode())
                .isIn(
                        HttpStatus.OK,
                        HttpStatus.CREATED
                );

        String login = """
                {
                  "usernameOrEmail": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);

        ResponseEntity<String> loginResponse =
                restTemplate.postForEntity(
                        url("/api/v1/login/login"),
                        json(login),
                        String.class
                );

        assertThat(loginResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String token =
                extractToken(loginResponse.getBody());

        // LoginResponse deliberately carries no numeric id (see its javadoc) —
        // GET /user/me is the endpoint that answers "what's my id", so fetch it
        // with the token we just got rather than trying to parse one out of the
        // login body.
        ResponseEntity<String> me =
                exchange(
                        HttpMethod.GET,
                        "/api/v1/user/me",
                        token,
                        null
                );

        assertThat(me.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Long userId =
                extractUserId(me.getBody());

        return new TestUser(
                userId,
                token
        );
    }

    private ResponseEntity<String> exchange(
            HttpMethod method,
            String path,
            String token,
            String body
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<String> entity =
                new HttpEntity<>(body, headers);

        return restTemplate.exchange(
                url(path),
                method,
                entity,
                String.class
        );
    }

    private HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String extractToken(String body)
            throws Exception {

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
                "Could not find authentication token: " + body
        );
    }

    private Long extractUserId(String body)
            throws Exception {

        JsonNode json =
                objectMapper.readTree(body);

        JsonNode user = json;

        if (json.has("user")) {
            user = json.get("user");
        } else if (json.has("data")
                && json.get("data").has("user")) {
            user = json.get("data").get("user");
        }

        if (user.hasNonNull("id")) {
            return user.get("id").asLong();
        }

        if (user.hasNonNull("userId")) {
            return user.get("userId").asLong();
        }

        throw new AssertionError(
                "Could not find user ID: " + body
        );
    }

    private String extractId(String body)
            throws Exception {

        JsonNode json =
                objectMapper.readTree(body);

        if (json.hasNonNull("id")) {
            return json.get("id").asText();
        }

        if (json.hasNonNull("noteId")) {
            return json.get("noteId").asText();
        }

        if (json.has("data")) {
            JsonNode data = json.get("data");

            if (data.hasNonNull("id")) {
                return data.get("id").asText();
            }

            if (data.hasNonNull("noteId")) {
                return data.get("noteId").asText();
            }
        }

        throw new AssertionError(
                "Could not extract entity ID: " + body
        );
    }

    private String extractIdFromFirstArrayElement(String body)
            throws Exception {

        JsonNode json =
                objectMapper.readTree(body);

        JsonNode array = json;

        if (json.has("content")) {
            array = json.get("content");
        } else if (json.has("data")) {
            array = json.get("data");
        }

        if (!array.isArray() || array.isEmpty()) {
            throw new AssertionError(
                    "Expected a non-empty collection: " + body
            );
        }

        JsonNode first = array.get(0);

        if (first.hasNonNull("id")) {
            return first.get("id").asText();
        }

        if (first.hasNonNull("collaborationId")) {
            return first.get("collaborationId").asText();
        }

        throw new AssertionError(
                "Could not extract collaboration ID: " + first
        );
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private record TestUser(
            Long userId,
            String token
    ) {
    }
}