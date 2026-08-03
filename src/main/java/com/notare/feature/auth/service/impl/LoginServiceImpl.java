package com.notare.feature.auth.service.impl;

import com.notare.common.security.JwtService;
import com.notare.feature.auth.dto.request.LoginRequest;
import com.notare.feature.auth.dto.response.LoginResponse;
import com.notare.feature.auth.service.LoginService;
import com.notare.feature.user.model.UserDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import static com.notare.common.constant.GeneralConstant.TOKEN_TYPE;

@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Override
    public LoginResponse login(LoginRequest request) {

        String identifier = request.getUsernameOrEmail().trim();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(identifier, request.getPassword())
        );

        UserDetail userDetail = (UserDetail) authentication.getPrincipal();
        String token = jwtService.generateToken(userDetail);

        return new LoginResponse(token, TOKEN_TYPE, expirationMs, userDetail.getUser().getNotarianId(), userDetail.getUsername());
    }
}
