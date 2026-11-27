package com.notare.feature.auth.web;

import com.notare.common.dto.MessageResponse;
import com.notare.feature.auth.dto.request.ResendActivationRequest;
import com.notare.feature.auth.service.ActivationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint for resending the account-activation email. */
@RequiredArgsConstructor
@RestController
@RequestMapping("${app.title}/activation")
public class ActivationController {

    private final ActivationService activationService;

    @PostMapping("/resend")
    public ResponseEntity<MessageResponse> resend(@Valid @RequestBody ResendActivationRequest request) {
        activationService.resendActivation(request.getNotarianIdOrEmail());
        return ResponseEntity.ok(new MessageResponse("If an account with that ID exists, a new activation email has been sent."));
    }
}
