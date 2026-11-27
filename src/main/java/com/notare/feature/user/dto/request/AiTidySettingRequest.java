package com.notare.feature.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body for PUT /user/me/ai-tidy. */
@Getter
@Setter
@NoArgsConstructor
public class AiTidySettingRequest {

    @NotNull(message = "enabled is required.")
    private Boolean enabled;
}
