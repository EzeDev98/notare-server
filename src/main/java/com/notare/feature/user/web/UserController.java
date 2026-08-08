package com.notare.feature.user.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.RegistrationResponse;
import com.notare.feature.user.dto.response.UserSummaryResponse;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/user")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        RegistrationResponse response = userService.registerUser(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    /**
     * Search for other users to invite as collaborators. Authenticated (only the
     * POST /user/** paths are public), and the current user is excluded from
     * results in the query.
     */
    @GetMapping("/search")
    public ResponseEntity<PagedResponse<UserSummaryResponse>> searchUsers(
            @AuthenticationPrincipal UserDetail userDetail,
            @RequestParam("query") String query,
            @ParameterObject
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {

        return ResponseEntity.ok(
                userService.searchUsers(userDetail.getUser().getId(), query, pageable));
    }
}