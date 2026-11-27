package com.notare.feature.auth.service.impl;

import com.notare.client.dto.response.InternalAuthApiResponse;
import com.notare.client.dto.response.InternalAuthLoginResponse;
import com.notare.client.service.InternalAuthClient;
import com.notare.common.exception.InternalAuthCallException;
import com.notare.feature.auth.dto.request.LoginRequest;
import com.notare.feature.auth.dto.response.UserLoginResponse;
import com.notare.feature.auth.service.LoginService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;

import static com.notare.common.constant.GeneralConstant.TOKEN_TYPE;

/** Signs users in through internal-auth-server. */
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    private final InternalAuthClient authClient;
    private final UserRepository userRepository;

    @Override
    public UserLoginResponse login(LoginRequest request, String userAgent, String ipAddress) {

        String identifier = request.getUsernameOrEmail().trim();

        User user = userRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new BadCredentialsException("Username or password is incorrect"));

        UserDetail userDetail = new UserDetail(user);
        if (!userDetail.isAccountNonLocked()) {
            throw new LockedException("Account is locked");
        }

        InternalAuthApiResponse<InternalAuthLoginResponse> response =
                authClient.login(new com.notare.client.dto.request.LoginRequest(user.getUsername(), request.getPassword(), userAgent));

        InternalAuthLoginResponse data = response == null ? null : response.getData();
        if (data == null || data.isMfaRequired() || data.getAccessToken() == null) {
            throw new InternalAuthCallException(HttpStatus.BAD_GATEWAY, "internal-auth-server did not return a session.");
        }

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            if (user.getUserStatus() == UserStatus.PENDING_VERIFICATION) {
                user.setUserStatus(UserStatus.ACTIVE);
            }
            userRepository.save(user);
        }

        if (user.getUserStatus() == UserStatus.SUSPENDED || user.getUserStatus() == UserStatus.DEACTIVATED) {
            user.setUserStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }

        long expiresInMs = data.getExpiresIn() == null ? 0L : data.getExpiresIn() * 1000L;

        return new UserLoginResponse(data.getAccessToken(), data.getRefreshToken(), TOKEN_TYPE, expiresInMs, user.getNotarianId(), user.getUsername());
    }
}
