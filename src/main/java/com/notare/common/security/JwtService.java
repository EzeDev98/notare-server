package com.notare.common.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.notare.common.constant.GeneralConstant.*;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final long resetTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.jwt.reset-token-expiration-ms}") long resetTokenExpirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret));
        this.expirationMs = expirationMs;
        this.resetTokenExpirationMs = resetTokenExpirationMs;
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetail) {

        Date now = new Date();
        Date expirationTime = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetail.getUsername())
                .issuedAt(now)
                .expiration(expirationTime)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractSessionId(String token) {
        return extractClaim(token, claims -> claims.get(SESSION_ID_CLAIM, String.class));
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    public String generatePasswordResetToken(UserDetails userDetails) {

        Map<String, Object> claims = new HashMap<>();
        claims.put(RESET_PURPOSE_CLAIM, RESET_PURPOSE_VALUE);

        Date now = new Date();
        Date expirationTime = new Date(now.getTime() + resetTokenExpirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(expirationTime)
                .signWith(signingKey)
                .compact();
    }

    public boolean isPasswordResetToken(String token) {
        String purpose = extractClaim(token, claims -> claims.get(RESET_PURPOSE_CLAIM, String.class));
        return RESET_PURPOSE_VALUE.equals(purpose);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException ex) {
            throw new JwtException("Invalid or expired JWT token", ex);
        }
    }
}