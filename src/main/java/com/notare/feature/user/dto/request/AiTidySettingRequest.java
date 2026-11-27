package com.notare.feature.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for turning AI tidy on or off. */
@Getter
@Setter
@NoArgsConstructor
public class AiTidySettingRequest {

    @NotNull(message = "enabled is required.")
    private Boolean enabled;
}
