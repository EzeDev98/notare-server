package com.notare.feature.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body for POST /user/password/change (step 17g, B-02). The rules for a NEW password live in the
 * auth service, which answers 400 with the reason; only presence and a sane size are checked here.
 */
@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "currentPassword is required.")
    @Size(max = 128, message = "currentPassword is too long.")
    private String currentPassword;

    @NotBlank(message = "newPassword is required.")
    @Size(max = 128, message = "newPassword is too long.")
    private String newPassword;
}
