package com.notare.feature.transcription.web;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import com.notare.feature.transcription.enums.TranscriptionStatus;
import com.notare.feature.transcription.service.TranscriptionService;
import com.notare.feature.user.enums.UserStatus;
import com.notare.feature.user.model.User;
import com.notare.feature.user.model.UserDetail;
import com.notare.common.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 15n of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - see {@link
 * AuthControllerTest}'s disclaimer (Step 15a);
 * same reasoning. Run via {@code mvn test} before trusting it.
 *
 * <p>Authenticated principal is supplied the same way as
 * {@link CollaborationControllerTest}
 * (Step 15d) - directly on {@code SecurityContextHolder} in the test thread,
 * with {@code addFilters = false} keeping the real
 * {@code JwtAuthenticationFilter} chain out of this slice.
 *
 * <p>This is the smallest controller in the roadmap - one endpoint, no path
 * variables, no request body - so the tests are correspondingly narrow: the
 * required {@code q} parameter, the {@code @PageableDefault} (size 20, sort
 * {@code updatedAt} DESC), an explicit override, the caller's id reaching
 * the service, and the {@link PagedResponse} envelope round-tripping.
 * {@link TranscriptionService#searchTranscripts} takes no query string that
 * can fail validation and throws nothing of its own (per its own javadoc -
 * a blank query matches everything rather than erroring), so there is no
 * exception-mapping test here the way there is for most other controllers.
 *
 * <p>Covers: {@code GET /transcriptions/search} - a missing {@code q}
 * returns 400 before the service runs (Spring's own required-parameter
 * binding, not bean validation - the same mechanism {@code NoteController}'s
 * {@code /notes/search} relies on, Step 15f); the query string is forwarded
 * verbatim, including a blank string; the {@code @PageableDefault}
 * default paging/sort and an explicit override; the
 * response is the service's {@link PagedResponse} unchanged.
 */
@WebMvcTest(
        controllers = TranscriptionSearchController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
class TranscriptionSearchControllerTest {

    private static final String SEARCH_URL = "/api/v1/transcriptions/search";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TranscriptionService transcriptionService;

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
        user.setPassword("hashed");
        user.setUserStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);

        UserDetail userDetail = new UserDetail(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetail, null, userDetail.getAuthorities()));
    }

    private TranscriptionResponse completedTranscription(Long id, Long voiceNoteId, String transcript) {
        LocalDateTime now = LocalDateTime.now();
        return new TranscriptionResponse(
                id, voiceNoteId, TranscriptionStatus.COMPLETED, transcript, 0.95, "en", null, now, now);
    }

    private PagedResponse<TranscriptionResponse> emptyPage() {
        Page<TranscriptionResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        return PagedResponse.of(List.of(), page);
    }

    // ----------------------------------------------------------------
    // GET /transcriptions/search
    // ----------------------------------------------------------------

    @Test
    void search_missingQueryParam_returns400BeforeReachingTheService() throws Exception {
        authenticateAs(1L);

        mockMvc.perform(get(SEARCH_URL))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transcriptionService);
    }

    @Test
    void search_forwardsTheQueryParamVerbatim() throws Exception {
        authenticateAs(1L);
        when(transcriptionService.searchTranscripts(eq(1L), eq("meeting notes"), any()))
                .thenReturn(emptyPage());

        mockMvc.perform(get(SEARCH_URL).param("q", "meeting notes"))
                .andExpect(status().isOk());

        verify(transcriptionService).searchTranscripts(eq(1L), eq("meeting notes"), any());
    }

    @Test
    void search_blankQuery_isForwardedAsIs_notRejected() throws Exception {
        // A blank q is a legitimate "match everything" query per the service's
        // own javadoc, not a validation failure - the controller has no
        // @NotBlank on it, so this must reach the service rather than 400.
        authenticateAs(1L);
        when(transcriptionService.searchTranscripts(eq(1L), eq(""), any()))
                .thenReturn(emptyPage());

        mockMvc.perform(get(SEARCH_URL).param("q", ""))
                .andExpect(status().isOk());

        verify(transcriptionService).searchTranscripts(eq(1L), eq(""), any());
    }

    @Test
    void search_noPagingParams_usesThePageableDefaultOfSize20SortedByUpdatedAtDesc() throws Exception {
        authenticateAs(1L);
        when(transcriptionService.searchTranscripts(eq(1L), eq("hello"), any()))
                .thenReturn(emptyPage());

        mockMvc.perform(get(SEARCH_URL).param("q", "hello"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transcriptionService).searchTranscripts(eq(1L), eq("hello"), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        Sort.Order order = pageable.getSort().getOrderFor("updatedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void search_explicitPagingParams_overridesTheDefault() throws Exception {
        authenticateAs(1L);
        when(transcriptionService.searchTranscripts(eq(1L), eq("hello"), any()))
                .thenReturn(emptyPage());

        mockMvc.perform(get(SEARCH_URL)
                        .param("q", "hello")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "id,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transcriptionService).searchTranscripts(eq(1L), eq("hello"), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(5);
        Sort.Order order = pageable.getSort().getOrderFor("id");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void search_returnsThePagedResponseEnvelopeFromTheService() throws Exception {
        authenticateAs(1L);
        TranscriptionResponse row = completedTranscription(9L, 7L, "hello world");
        Page<TranscriptionResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(transcriptionService.searchTranscripts(eq(1L), eq("hello"), any()))
                .thenReturn(PagedResponse.of(List.of(row), page));

        mockMvc.perform(get(SEARCH_URL).param("q", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.content[0].transcript").value("hello world"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void search_isKeyedOffTheAuthenticatedPrincipalNotAnythingElse() throws Exception {
        authenticateAs(77L);
        when(transcriptionService.searchTranscripts(eq(77L), eq("hello"), any()))
                .thenReturn(emptyPage());

        mockMvc.perform(get(SEARCH_URL).param("q", "hello"))
                .andExpect(status().isOk());

        verify(transcriptionService).searchTranscripts(eq(77L), eq("hello"), any());
    }
}