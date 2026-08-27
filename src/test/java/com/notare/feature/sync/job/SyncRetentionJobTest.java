package com.notare.feature.sync.job;

import com.notare.feature.sync.config.SyncProperties;
import com.notare.feature.sync.repository.NoteTombstoneRepository;
import com.notare.feature.sync.repository.SyncOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 13d of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link SyncRetentionJob}'s source and follows this codebase's existing
 * Mockito conventions exactly (see {@code VoiceUploadValidatorTest} for the
 * pattern of instantiating a real {@code @ConfigurationProperties} bean
 * directly rather than mocking it), but it has not actually been compiled or
 * run. Please run it via {@code mvn test} before trusting it.
 *
 * <p>{@code @Scheduled} and {@code @Transactional} are infrastructure wiring
 * applied by Spring proxies at runtime — not observable by calling
 * {@link SyncRetentionJob#sweep} directly, which is what these tests do,
 * exactly like this codebase's other scheduled/event-listener suites (see
 * {@code TranscriptionWorkerTest}).
 *
 * <p>Covers, per the class's own doc comment:
 * <ul>
 *   <li>both repositories are swept with the SAME cutoff — the doc comment
 *       is explicit that the two tables describe the same retention window
 *       and must never be allowed to describe different ones;</li>
 *   <li>the cutoff is computed from {@code syncProperties.getRetentionDays()},
 *       not a hardcoded value;</li>
 *   <li>both deletes always run, in either order relative to each other,
 *       regardless of how many rows each one reports removing — including
 *       the zero-rows case, which the doc comment says is logged
 *       unconditionally rather than treated as a no-op skip.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SyncRetentionJobTest {

    @Mock
    private SyncOperationRepository syncOperationRepository;

    @Mock
    private NoteTombstoneRepository noteTombstoneRepository;

    @Captor
    private ArgumentCaptor<LocalDateTime> operationsCutoffCaptor;

    @Captor
    private ArgumentCaptor<LocalDateTime> tombstonesCutoffCaptor;

    private SyncProperties syncProperties;
    private SyncRetentionJob job;

    @BeforeEach
    void setUp() {
        syncProperties = new SyncProperties();
        syncProperties.setRetentionDays(30);
        job = new SyncRetentionJob(syncOperationRepository, noteTombstoneRepository, syncProperties);
    }

    @Test
    void sweep_deletesFromBothRepositoriesUsingTheSameCutoff() {
        when(syncOperationRepository.deleteOlderThan(operationsCutoffCaptor.capture())).thenReturn(3);
        when(noteTombstoneRepository.deleteOlderThan(tombstonesCutoffCaptor.capture())).thenReturn(1);

        job.sweep();

        assertThat(operationsCutoffCaptor.getValue()).isEqualTo(tombstonesCutoffCaptor.getValue());
    }

    @Test
    void sweep_computesTheCutoffFromTheConfiguredRetentionDays() {
        when(syncOperationRepository.deleteOlderThan(operationsCutoffCaptor.capture())).thenReturn(0);
        when(noteTombstoneRepository.deleteOlderThan(any())).thenReturn(0);

        LocalDateTime before = LocalDateTime.now().minusDays(30);
        job.sweep();
        LocalDateTime after = LocalDateTime.now().minusDays(30);

        assertThat(operationsCutoffCaptor.getValue()).isBetween(
                before.minus(1, ChronoUnit.SECONDS), after.plus(1, ChronoUnit.SECONDS));
    }

    @Test
    void sweep_respectsACustomRetentionWindow() {
        syncProperties.setRetentionDays(7);
        job = new SyncRetentionJob(syncOperationRepository, noteTombstoneRepository, syncProperties);
        when(syncOperationRepository.deleteOlderThan(operationsCutoffCaptor.capture())).thenReturn(0);
        when(noteTombstoneRepository.deleteOlderThan(any())).thenReturn(0);

        LocalDateTime before = LocalDateTime.now().minusDays(7);
        job.sweep();
        LocalDateTime after = LocalDateTime.now().minusDays(7);

        assertThat(operationsCutoffCaptor.getValue()).isBetween(
                before.minus(1, ChronoUnit.SECONDS), after.plus(1, ChronoUnit.SECONDS));
    }

    @Test
    void sweep_bothDeletesRunEvenWhenNothingIsRemoved() {
        when(syncOperationRepository.deleteOlderThan(any())).thenReturn(0);
        when(noteTombstoneRepository.deleteOlderThan(any())).thenReturn(0);

        job.sweep();

        verify(syncOperationRepository).deleteOlderThan(any());
        verify(noteTombstoneRepository).deleteOlderThan(any());
    }

    @Test
    void sweep_bothDeletesRunRegardlessOfHowManyRowsTheOtherRemoves() {
        when(syncOperationRepository.deleteOlderThan(any())).thenReturn(500);
        when(noteTombstoneRepository.deleteOlderThan(any())).thenReturn(0);

        job.sweep();

        verify(syncOperationRepository).deleteOlderThan(any());
        verify(noteTombstoneRepository).deleteOlderThan(any());
    }

    private static LocalDateTime any() {
        return org.mockito.ArgumentMatchers.any();
    }
}