package com.notare.common.version;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** The oldest client version the server still accepts. */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "notare.client")
public class ClientVersionProperties {

    @NotBlank
    private String minVersion;
}
