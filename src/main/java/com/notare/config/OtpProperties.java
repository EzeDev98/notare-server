package com.notare.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.otp")
public class OtpProperties {

    private int length = 6;

    private int expiryMinutes = 10;

    private int maxAttempts = 5;

    private int resendCooldownSeconds = 60;
}