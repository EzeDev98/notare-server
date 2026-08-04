package com.notare.feature.auth.service.impl;

import com.notare.common.exception.UserNotFoundException;
import com.notare.common.security.JwtService;
import com.notare.feature.auth.dto.request.RefreshTokenRequest;
import com.notare.feature.auth.dto.response.TokenResponse;
import com.notare.feature.auth.service.AuthService;
import com.notare.feature.session.records.RotatedSession;
import com.notare.feature.session.service.SessionService;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Map;

import static com.notare.common.constant.GeneralConstant.SESSION_ID_CLAIM;
import static com.notare.common.constant.GeneralConstant.TOKEN_TYPE;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public TokenResponse refreshToken(RefreshTokenRequest request, String userAgent, String clientIpAddress) {

        RotatedSession rotated = sessionService.rotateRefreshToken(request.getRefreshToken(), userAgent, clientIpAddress);

        User user = userRepository.findById(rotated.userId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        UserDetail userDetail = new UserDetail(user);
        String accessToken = jwtService.generateToken(Map.of(SESSION_ID_CLAIM, rotated.sessionId()), userDetail);

        return new TokenResponse(accessToken, rotated.rawRefreshToken(), TOKEN_TYPE, expirationMs);
    }
}
