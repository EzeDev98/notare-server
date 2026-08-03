package com.notare.feature.user.service;

import com.notare.feature.user.dto.request.RegistrationRequest;
import com.notare.feature.user.dto.response.RegistrationResponse;

public interface UserService {
    RegistrationResponse registerUser(RegistrationRequest request);
}
