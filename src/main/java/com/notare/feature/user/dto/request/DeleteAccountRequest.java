package com.notare.feature.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body for DELETE /user/me (step 17g, B-03): the person types their username to confirm. */
@Getter
@Setter
@NoArgsConstructor
public class DeleteAccountRequest {

    @NotBlank(message = "username is required.")
    private String username;
}
