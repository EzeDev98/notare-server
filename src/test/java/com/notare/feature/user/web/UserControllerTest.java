package com.notare.feature.user.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notare.common.exception.UserAlreadyExistException;
import com.notare.common.exception.UserNotFoundException;
import com.notare.feature.user.dto.response.CurrentUserResponse;
import com.notare.feature.user.dto.response.RegistrationResponse;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.feature.user.service.UserService;
import com.notare.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import com.notare.support.WebSliceSupport;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests for UserController. */
@WebSliceSupport
@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    private static final String BASE_URL = "/api/v1/user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private com.notare.feature.user.service.AccountDeletionService accountDeletionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("jdoe");
        user.setNotarianId("NOT-ABCDEF");
        user.setEmail("jdoe@example.com");
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        UserDetail userDetail = new UserDetail(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetail, null, userDetail.getAuthorities()));
    }

    private Map<String, String> registrationBody(String firstName, String lastName, String username,
                                                 String email, String password, String confirmPassword) {
        Map<String, String> body = new HashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);
        body.put("confirmPassword", confirmPassword);
        return body;
    }

    private Map<String, String> validRegistrationBody() {
        return registrationBody("Jane", "Doe", "janedoe", "jane@example.com", "Passw0rd", "Passw0rd");
    }

    @Test
    void register_validRequest_echoesTheServicesStatusCodeAndBody() throws Exception {
        when(userService.registerUser(any())).thenReturn(new RegistrationResponse(
                HttpServletResponse.SC_CREATED,
                "Account created. Please check your email to verify your account.",
                "NOT-ABCDEF"));

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRegistrationBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.message").value("Account created. Please check your email to verify your account."))
                .andExpect(jsonPath("$.notarianId").value("NOT-ABCDEF"));

        verify(userService).registerUser(any());
    }

    @Test
    void register_missingRequiredField_returns400BeforeReachingTheService() throws Exception {
        Map<String, String> body = validRegistrationBody();
        body.remove("email");

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email address is required."));

        verifyNoInteractions(userService);
    }

    @Test
    void register_weakPassword_returns400BeforeReachingTheService() throws Exception {
        Map<String, String> body = validRegistrationBody();
        body.put("password", "alllowercase1");
        body.put("confirmPassword", "alllowercase1");

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());

        verifyNoInteractions(userService);
    }

    @Test
    void register_usernameOrEmailAlreadyTaken_returns409ViaGlobalExceptionHandler() throws Exception {
        when(userService.registerUser(any()))
                .thenThrow(new UserAlreadyExistException("A user with this username is already registered"));

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(validRegistrationBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A user with this username is already registered"));
    }

    @Test
    void getCurrentUser_validRequest_returns200WithTheFullProfileIncludingEmail() throws Exception {
        authenticateAs(1L);
        LocalDateTime createdAt = LocalDateTime.now();
        when(userService.getCurrentUser(1L)).thenReturn(new CurrentUserResponse(
                1L, "NOT-ABCDEF", "jdoe", "Jane", "Doe", "jdoe@example.com", createdAt));

        mockMvc.perform(get(BASE_URL + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.notarianId").value("NOT-ABCDEF"))
                .andExpect(jsonPath("$.username").value("jdoe"))
                .andExpect(jsonPath("$.email").value("jdoe@example.com"));
    }

    @Test
    void getCurrentUser_accountNoLongerExists_returns404ViaGlobalExceptionHandler() throws Exception {
        authenticateAs(1L);
        when(userService.getCurrentUser(1L)).thenThrow(new UserNotFoundException("User not found."));

        mockMvc.perform(get(BASE_URL + "/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found."));
    }

    @Test
    void getCurrentUser_usesTheAuthenticatedPrincipalsIdNotAnythingElse() throws Exception {
        authenticateAs(42L);
        when(userService.getCurrentUser(42L)).thenReturn(new CurrentUserResponse(
                42L, "NOT-XYZ123", "other", "Other", "User", "other@example.com", LocalDateTime.now()));

        mockMvc.perform(get(BASE_URL + "/me"))
                .andExpect(status().isOk());

        verify(userService).getCurrentUser(42L);
    }

}
