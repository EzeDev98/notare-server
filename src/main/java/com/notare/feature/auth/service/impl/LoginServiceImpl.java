package com.notare.feature.auth.service.impl;

import com.notare.common.security.JwtService;
import com.notare.feature.auth.dto.request.LoginRequest;
import com.notare.feature.auth.dto.response.LoginResponse;
import com.notare.feature.auth.service.LoginService;
import com.notare.feature.session.records.SessionCreationResult;
import com.notare.feature.session.service.SessionService;
import com.notare.feature.user.model.UserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.notare.common.constant.GeneralConstant.SESSION_ID_CLAIM;
import static com.notare.common.constant.GeneralConstant.TOKEN_TYPE;

@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SessionService sessionService;

    @Override
    public LoginResponse login(LoginRequest request, String userAgent, String ipAddress) {

        String identifier = request.getUsernameOrEmail().trim();

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(identifier, request.getPassword()));

        UserDetail userDetail = (UserDetail) authentication.getPrincipal();

        SessionCreationResult session = sessionService.createSession(userDetail.getUser().getId(), userAgent, ipAddress);
        String accessToken = jwtService.generateToken(Map.of(SESSION_ID_CLAIM, session.sessionId()), userDetail);

        return new LoginResponse(accessToken, session.rawRefreshToken(), TOKEN_TYPE, expirationMs, userDetail.getUser().getNotarianId(), userDetail.getUsername());
    }
}