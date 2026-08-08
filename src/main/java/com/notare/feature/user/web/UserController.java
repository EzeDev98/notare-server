package com.notare.feature.user.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.CurrentUserResponse;
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

import java.util.List;

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
    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(
            @AuthenticationPrincipal UserDetail userDetail) {

        return ResponseEntity.ok(userService.getCurrentUser(userDetail.getUser().getId()));
    }

    /**
     * Resolve a batch of user ids to public profiles.
     *
     * <p>The API returns bare ids everywhere — {@code ownerId},
     * {@code actorId}, {@code lastModifiedBy}, {@code sharedWithUserId},
     * {@code collaboratorId} — because embedding a profile in every row would
     * mean a user lookup per row. That decision only works if the client has a
     * way to resolve a page's worth of ids in one request. This is it.
     *
     * <p>Spring binds {@code List<Long>} from either form, so
     * {@code ?ids=1,2,3} and {@code ?ids=1&ids=2&ids=3} both work and no
     * parsing code is needed for either.
     *
     * <p>Returns a bare list rather than a paged envelope. The caller already
     * knows how many results it can possibly get — it supplied the ids — so
     * pagination would add an envelope around a set the client defined itself.
     * The per-request cap does the job pagination would otherwise be doing.
     *
     * <p><b>Known trade-off.</b> Ids are sequential, so an authenticated caller
     * can walk this endpoint from 1 upward and enumerate the directory's
     * usernames and display names. That is a real disclosure that
     * {@code /user/search} does not have, since search requires knowing a term
     * to match. It is accepted for the same reason partial email matching was
     * accepted on search: the fields exposed are the ones this application
     * treats as a public profile, and every alternative — restricting lookup to
     * ids the caller already has a relationship with — needs the user module to
     * query collaborations and shares, inverting the dependency direction those
     * modules are built on. The per-request cap bounds the rate; the rate
     * limiter already on the backlog for {@code /sync} should cover this path
     * too, and that is the proper fix.
     */
    @GetMapping("/lookup")
    public ResponseEntity<List<UserSummaryResponse>> lookupUsers(
            @RequestParam("ids") List<Long> ids) {

        return ResponseEntity.ok(userService.lookupUsers(ids));
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