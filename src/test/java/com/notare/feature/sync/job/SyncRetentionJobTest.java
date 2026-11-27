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

/** Tests for SyncRetentionJob. */
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
