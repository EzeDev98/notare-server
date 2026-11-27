package com.notare.feature.sync.service.impl;

import com.notare.common.exception.NoteNotFoundException;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.sync.dto.request.SyncBatchRequest;
import com.notare.feature.sync.dto.request.SyncOperationRequest;
import com.notare.feature.sync.dto.response.SyncBatchResponse;
import com.notare.feature.sync.dto.response.SyncOperationResult;
import com.notare.feature.sync.enums.SyncOperationType;
import com.notare.feature.sync.enums.SyncResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tests for SyncPushServiceImpl. */
@ExtendWith(MockitoExtension.class)
class SyncPushServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long NOTE_ID = 42L;

    @Mock
    private SyncOperationApplier applier;

    @Mock
    private NoteService noteService;

    private SyncPushServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncPushServiceImpl(applier);
    }

    private SyncOperationRequest operation(String operationId, SyncOperationType type) {
        SyncOperationRequest request = new SyncOperationRequest();
        request.setOperationId(operationId);
        request.setType(type);
        return request;
    }

    private SyncBatchRequest batch(SyncOperationRequest... operations) {
        SyncBatchRequest request = new SyncBatchRequest();
        request.setOperations(List.of(operations));
        return request;
    }

    private NoteResponse note(Long id, Long version) {
        return new NoteResponse(id, OWNER_ID, "hello", false, version, OWNER_ID, null, null, false, null);
    }

    private SyncOperationResult applied(String operationId) {
        return new SyncOperationResult(operationId, SyncResultStatus.APPLIED, note(NOTE_ID, 2L), null, null);
    }

    @Test
    void push_createMissingClientId_isInvalidWithoutCallingTheApplier() {
        SyncOperationRequest op = operation("op-1", SyncOperationType.CREATE);
        op.setClientId(null);

        SyncBatchResponse response = service.push(OWNER_ID, batch(op));

        assertThat(response.results()).hasSize(1);
        SyncOperationResult result = response.results().get(0);
        assertThat(result.status()).isEqualTo(SyncResultStatus.INVALID);
        assertThat(result.message()).contains("clientId");
        assertThat(response.appliedCount()).isEqualTo(0);
        verifyNoInteractions(applier);
    }

    @Test
    void push_createBlankClientId_isAlsoInvalid() {
        SyncOperationRequest op = operation("op-1", SyncOperationType.CREATE);
        op.setClientId("   ");

        SyncBatchResponse response = service.push(OWNER_ID, batch(op));

        assertThat(response.results().get(0).status()).isEqualTo(SyncResultStatus.INVALID);
        verifyNoInteractions(applier);
    }

    @Test
    void push_updateMissingNoteId_isInvalidWithoutCallingTheApplier() {
        SyncOperationRequest op = operation("op-1", SyncOperationType.UPDATE);
        op.setNoteId(null);

        SyncBatchResponse response = service.push(OWNER_ID, batch(op));

        SyncOperationResult result = response.results().get(0);
        assertThat(result.status()).isEqualTo(SyncResultStatus.INVALID);
        assertThat(result.message()).contains("noteId");
        verifyNoInteractions(applier);
    }

    @Test
    void push_deleteMissingNoteId_isInvalidWithoutCallingTheApplier() {
        SyncOperationRequest op = operation("op-1", SyncOperationType.DELETE);
        op.setNoteId(null);

        SyncBatchResponse response = service.push(OWNER_ID, batch(op));

        SyncOperationResult result = response.results().get(0);
        assertThat(result.status()).isEqualTo(SyncResultStatus.INVALID);
        assertThat(result.message()).contains("noteId");
        verifyNoInteractions(applier);
    }

    @Test
    void push_wellFormedOperation_delegatesToTheApplierAndCountsItApplied() {
        SyncOperationRequest op = operation("op-1", SyncOperationType.CREATE);
        op.setClientId("local-1");
        when(applier.apply(OWNER_ID, op)).thenReturn(applied("op-1"));

        SyncBatchResponse response = service.push(OWNER_ID, batch(op));

        assertThat(response.results()).containsExactly(applied("op-1"));
        assertThat(response.appliedCount()).isEqualTo(1);
    }

    @Test
    void push_serverTimeIsCapturedAfterTheLoopCompletes() {
        SyncOperationRequest op = operation("op-1", SyncOperationType.CREATE);
        op.setClientId("local-1");
        when(applier.apply(OWNER_ID, op)).thenReturn(applied("op-1"));

        LocalDateTime before = LocalDateTime.now();
        SyncBatchResponse response = service.push(OWNER_ID, batch(op));
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.serverTime()).isBetween(before, after);
    }

    @Test
    void push_applierThrowsNoteNotFoundException_returnsNotFoundWithoutLookingUpTheNote() {
        SyncOperationRequest op = operation("op-1", SyncOperationType.DELETE);
        op.setNoteId(NOTE_ID);
        when(applier.apply(OWNER_ID, op)).thenThrow(new NoteNotFoundException("gone"));

        SyncBatchResponse response = service.push(OWNER_ID, batch(op));

        SyncOperationResult result = response.results().get(0);
        assertThat(result.status()).isEqualTo(SyncResultStatus.NOT_FOUND);
        assertThat(response.appliedCount()).isEqualTo(0);
        verify(noteService, never()).findAnyNote(any(), any());
    }

    @Test
    void push_multipleOperations_appliesThemInRequestOrder() {
        SyncOperationRequest create = operation("op-1", SyncOperationType.CREATE);
        create.setClientId("local-1");
        SyncOperationRequest update = operation("op-2", SyncOperationType.UPDATE);
        update.setNoteId(NOTE_ID);
        when(applier.apply(eq(OWNER_ID), any())).thenReturn(applied("op-1"), applied("op-2"));

        service.push(OWNER_ID, batch(create, update));

        InOrder order = inOrder(applier);
        order.verify(applier).apply(OWNER_ID, create);
        order.verify(applier).apply(OWNER_ID, update);
    }

    @Test
    void push_oneInvalidOperationAmongValidOnes_doesNotStopTheBatch() {
        SyncOperationRequest invalid = operation("op-1", SyncOperationType.CREATE);
        invalid.setClientId(null);
        SyncOperationRequest valid = operation("op-2", SyncOperationType.CREATE);
        valid.setClientId("local-2");
        when(applier.apply(OWNER_ID, valid)).thenReturn(applied("op-2"));

        SyncBatchResponse response = service.push(OWNER_ID, batch(invalid, valid));

        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).status()).isEqualTo(SyncResultStatus.INVALID);
        assertThat(response.results().get(1).status()).isEqualTo(SyncResultStatus.APPLIED);
        assertThat(response.appliedCount()).isEqualTo(1);
        verify(applier, never()).apply(OWNER_ID, invalid);
    }

    @Test
    void push_anUnexpectedRuntimeExceptionFromTheApplier_propagatesAndFailsTheWholeBatch() {
        SyncOperationRequest op = operation("op-1", SyncOperationType.CREATE);
        op.setClientId("local-1");
        when(applier.apply(OWNER_ID, op)).thenThrow(new IllegalStateException("database is down"));

        assertThatThrownBy(() -> service.push(OWNER_ID, batch(op)))
                .isInstanceOf(IllegalStateException.class);
    }
}
