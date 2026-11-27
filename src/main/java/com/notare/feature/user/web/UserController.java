package com.notare.feature.user.web;

import com.notare.feature.user.dto.request.AiTidySettingRequest;
import com.notare.feature.user.dto.request.DeleteAccountRequest;
import com.notare.feature.user.dto.response.AiTidySettingResponse;
import com.notare.feature.user.service.AccountDeletionService;
import org.springframework.web.bind.annotation.DeleteMapping;

import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.CurrentUserResponse;
import com.notare.feature.user.dto.response.RegistrationResponse;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/user")
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        RegistrationResponse response = userService.registerUser(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * The caller's own profile.
     *
     * <p>{@code /me} rather than {@code /{userId}} on purpose. A path variable
     * would invite the question "may I read another id?", and every answer to
     * that question is a decision the endpoint should not be making. There is
     * no id in the URL, so the subject is the token holder and cannot be
     * anything else — authorization is expressed by the route's shape rather
     * than enforced by a check inside it, which is the same instinct behind
     * scoping every repository finder by owner.
     *
     * <p>This must be routed BEFORE any future {@code /user/{something}}
     * mapping, though nothing here needs it today: Spring matches literal
     * segments ahead of templates, the same reason {@code /notes/search} sits
     * safely alongside {@code /notes/{noteId}}.
     */
    /**
     * Step 17g (B-03). Deletes the account and everything it owns. The body carries the username as
     * confirmation. If it fails halfway, calling again continues; a scheduled job also continues it.
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal UserDetail userDetail,
            @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.delete(userDetail.getUser(), request.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(
            @AuthenticationPrincipal UserDetail userDetail) {

        return ResponseEntity.ok(userService.getCurrentUser(userDetail.getUser().getId()));
    }

    /** AI tidy opt-in for the caller. */
    @GetMapping("/me/ai-tidy")
    public ResponseEntity<AiTidySettingResponse> getAiTidy(@AuthenticationPrincipal UserDetail userDetail) {
        return ResponseEntity.ok(userService.getAiTidy(userDetail.getUser().getId()));
    }

    @PutMapping("/me/ai-tidy")
    public ResponseEntity<AiTidySettingResponse> setAiTidy(
            @AuthenticationPrincipal UserDetail userDetail,
            @Valid @RequestBody AiTidySettingRequest request) {

        return ResponseEntity.ok(userService.setAiTidy(userDetail.getUser().getId(), request.getEnabled()));
    }
}
