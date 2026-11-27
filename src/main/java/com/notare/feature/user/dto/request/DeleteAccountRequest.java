package com.notare.feature.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request body for deleting an account: the username typed to confirm. */
@Getter
@Setter
@NoArgsConstructor
public class DeleteAccountRequest {

    @NotBlank(message = "username is required.")
    private String username;
}
