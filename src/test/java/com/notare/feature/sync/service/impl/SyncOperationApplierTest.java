package com.notare.feature.sync.service.impl;

import com.notare.common.exception.NoteNotFoundException;
import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.sync.dto.request.SyncOperationRequest;
import com.notare.feature.sync.dto.response.SyncOperationResult;
import com.notare.feature.sync.enums.SyncOperationType;
import com.notare.feature.sync.enums.SyncResultStatus;
import com.notare.feature.sync.model.SyncOperation;
import com.notare.feature.sync.repository.SyncOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step 13c of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox.</b> This environment has no route to a
 * Maven mirror, so Mockito is unavailable as a compile-time dependency here,
 * and {@code target/classes} only holds this project's own already-compiled
 * classes, not third-party jars. Every test below is reasoned carefully from
 * {@link SyncOperationApplier}'s source and follows this codebase's existing
 * Mockito conventions exactly, but it has not actually been compiled or run.
 * Please run it via {@code mvn test} before trusting it. As the class's own
 * javadoc explains, {@code @Transactional(propagation = REQUIRES_NEW)} is
 * infrastructure wiring applied by Spring's AOP proxy — calling
 * {@link SyncOperationApplier#apply} directly, as these tests do, exercises
 * exactly the business logic that runs once that proxying has happened.
 *
 * <p>Covers, per the method's own numbered steps and the class's extended
 * doc comment:
 * <ul>
 *   <li><b>Replay (step 1):</b> an already-recorded operationId short-circuits
 *       straight to a re-read of the note and an APPLIED result — the CREATE
 *       /UPDATE/DELETE branch and the ledger write are both skipped entirely,
 *       which is what makes a retried batch safe;</li>
 *   <li>a replay whose note has since vanished (a later permanent delete)
 *       still returns APPLIED with a null note, per the method's own comment
 *       that this is "the honest answer" and all the client needs to know is
 *       it can drop the operation from its queue;</li>
 *   <li><b>Fresh work (steps 2-3):</b> each of CREATE/UPDATE/DELETE calls the
 *       corresponding NoteService method with the right fields threaded
 *       through, and — critically — the ledger write happens ONLY for a
 *       genuinely new operation, never on a replay;</li>
 *   <li>the ledger entry records the note's id and its POST-operation
 *       version, keyed by the request's own operationId and ownerId;</li>
 *   <li>the "nothing to point the ledger at" guard: if the post-operation
 *       re-read comes back empty (DELETE's own re-read, or any note that
 *       vanished between the write and the read), the ledger write is
 *       skipped rather than saving a broken row;</li>
 *   <li>{@link NoteConflictException} and {@link NoteNotFoundException} are
 *       NOT caught here — they propagate uncaught so the surrounding
 *       REQUIRES_NEW transaction rolls back cleanly, per the class's own doc
 *       comment on why catching them in-place would be unsafe.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SyncOperationApplierTest {

    private static final Long OWNER_ID = 1L;
    private static final Long NOTE_ID = 42L;
    private static final String OPERATION_ID = "op-1";

    @Mock
    private NoteService noteService;

    @Mock
    private SyncOperationRepository syncOperationRepository;

    @Mock
    private com.notare.feature.voicenote.service.VoiceNoteService voiceNoteService;

    @Captor
    private ArgumentCaptor<SyncOperation> ledgerCaptor;

    private SyncOperationApplier applier;

    @BeforeEach
    void setUp() {
        applier = new SyncOperationApplier(noteService, syncOperationRepository, voiceNoteService);
    }

    private NoteResponse note(Long id, Long version) {
        return new NoteResponse(id, OWNER_ID, "hello", false, version, OWNER_ID, null, null);
    }

    private SyncOperationRequest request(SyncOperationType type) {
        SyncOperationRequest req = new SyncOperationRequest();
        req.setOperationId(OPERATION_ID);
        req.setType(type);
        return req;
    }

    private SyncOperation ledgerEntry() {
        SyncOperation op = new SyncOperation();
        op.setOwnerId(OWNER_ID);
        op.setOperationId(OPERATION_ID);
        op.setNoteId(NOTE_ID);
        op.setResultVersion(3L);
        return op;
    }

    // -------------------------------------------------------------------
    // Step 1: replay of an already-recorded operation
    // -------------------------------------------------------------------

    @Test
    void apply_alreadyRecordedOperation_replaysWithoutRedoingTheWorkOrTheLedgerWrite() {
        when(syncOperationRepository.findByOwnerIdAndOperationId(OWNER_ID, OPERATION_ID))
                .thenReturn(Optional.of(ledgerEntry()));
        when(noteService.findAnyNote(OWNER_ID, NOTE_ID)).thenReturn(Optional.of(note(NOTE_ID, 3L)));

        SyncOperationRequest req = request(SyncOperationType.UPDATE);
        req.setNoteId(NOTE_ID);

        SyncOperationResult result = applier.apply(OWNER_ID, req);

        assertThat(result.status()).isEqualTo(SyncResultStatus.APPLIED);
        assertThat(result.note().version()).isEqualTo(3L);
        verify(noteService, never()).updateNote(any(), any(), any());
        verify(noteService, never()).createWithClientId(any(), any(), any());
        verify(noteService, never()).permanentlyDeleteNote(any(), any());
        verify(syncOperationRepository, never()).save(any());
    }

    @Test
    void apply_replayedOperation_whoseNoteHasSinceVanished_stillReturnsAppliedWithANullNote() {
        when(syncOperationRepository.findByOwnerIdAndOperationId(OWNER_ID, OPERATION_ID))
                .thenReturn(Optional.of(ledgerEntry()));
        when(noteService.findAnyNote(OWNER_ID, NOTE_ID)).thenReturn(Optional.empty());

        SyncOperationResult result = applier.apply(OWNER_ID, request(SyncOperationType.DELETE));

        assertThat(result.status()).isEqualTo(SyncResultStatus.APPLIED);
        assertThat(result.note()).isNull();
    }

    // -------------------------------------------------------------------
    // Fresh CREATE
    // -------------------------------------------------------------------

    @Test
    void apply_newCreateOperation_createsWithClientIdAndRecordsTheLedgerEntry() {
        when(syncOperationRepository.findByOwnerIdAndOperationId(OWNER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        SyncOperationRequest req = request(SyncOperationType.CREATE);
        req.setClientId("local-1");
        req.setContent("hello world");
        NoteResponse created = note(NOTE_ID, 1L);
        when(noteService.createWithClientId(eq(OWNER_ID), any(CreateNoteRequest.class), eq("local-1")))
                .thenReturn(created);

        SyncOperationResult result = applier.apply(OWNER_ID, req);

        assertThat(result.status()).isEqualTo(SyncResultStatus.APPLIED);
        assertThat(result.note()).isEqualTo(created);
        assertThat(result.clientId()).isEqualTo("local-1");

        verify(noteService).createWithClientId(eq(OWNER_ID), any(CreateNoteRequest.class), eq("local-1"));
        verify(syncOperationRepository).save(ledgerCaptor.capture());
        SyncOperation saved = ledgerCaptor.getValue();
        assertThat(saved.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(saved.getOperationId()).isEqualTo(OPERATION_ID);
        assertThat(saved.getNoteId()).isEqualTo(NOTE_ID);
        assertThat(saved.getResultVersion()).isEqualTo(1L);
    }

    @Test
    void apply_createRequest_threadsTheContentThroughToCreateNoteRequest() {
        when(syncOperationRepository.findByOwnerIdAndOperationId(OWNER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        SyncOperationRequest req = request(SyncOperationType.CREATE);
        req.setClientId("local-1");
        req.setContent("hello world");
        when(noteService.createWithClientId(eq(OWNER_ID), any(CreateNoteRequest.class), eq("local-1")))
                .thenReturn(note(NOTE_ID, 1L));

        applier.apply(OWNER_ID, req);

        ArgumentCaptor<CreateNoteRequest> captor = ArgumentCaptor.forClass(CreateNoteRequest.class);
        verify(noteService).createWithClientId(eq(OWNER_ID), captor.capture(), eq("local-1"));
        assertThat(captor.getValue().getContent()).isEqualTo("hello world");
    }

    // -------------------------------------------------------------------
    // Fresh UPDATE
    // -------------------------------------------------------------------

    @Test
    void apply_newUpdateOperation_updatesWithTheGivenVersionAndRecordsTheLedgerEntry() {
        when(syncOperationRepository.findByOwnerIdAndOperationId(OWNER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        SyncOperationRequest req = request(SyncOperationType.UPDATE);
        req.setNoteId(NOTE_ID);
        req.setContent("updated content");
        NoteResponse updated = note(NOTE_ID, 3L);
        when(noteService.updateNote(eq(OWNER_ID), eq(NOTE_ID), any(UpdateNoteRequest.class)))
                .thenReturn(updated);

        SyncOperationResult result = applier.apply(OWNER_ID, req);

        assertThat(result.status()).isEqualTo(SyncResultStatus.APPLIED);
        assertThat(result.note()).isEqualTo(updated);
        // clientId is null for UPDATE — reconciliation-by-clientId only matters for CREATE.
        assertThat(result.clientId()).isNull();

        ArgumentCaptor<UpdateNoteRequest> captor = ArgumentCaptor.forClass(UpdateNoteRequest.class);
        verify(noteService).updateNote(eq(OWNER_ID), eq(NOTE_ID), captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("updated content");

        verify(syncOperationRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getResultVersion()).isEqualTo(3L);
    }

    // -------------------------------------------------------------------
    // Fresh DELETE
    // -------------------------------------------------------------------

    @Test
    void apply_deleteOperation_whoseReReadComesBackEmpty_skipsTheLedgerWrite() {
        when(syncOperationRepository.findByOwnerIdAndOperationId(OWNER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        SyncOperationRequest req = request(SyncOperationType.DELETE);
        req.setNoteId(NOTE_ID);
        when(noteService.findAnyNote(OWNER_ID, NOTE_ID)).thenReturn(Optional.empty());

        SyncOperationResult result = applier.apply(OWNER_ID, req);

        assertThat(result.status()).isEqualTo(SyncResultStatus.APPLIED);
        assertThat(result.note()).isNull();
        // Nothing to point the ledger at — skipping costs at most one
        // redundant replay, which the class's own doc comment says is safe.
        verify(syncOperationRepository, never()).save(any());
    }

    @Test
    void apply_deleteOperation_thatFindsTheNoteAlreadyGone_propagatesTheException() {
        when(syncOperationRepository.findByOwnerIdAndOperationId(OWNER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        SyncOperationRequest req = request(SyncOperationType.DELETE);
        req.setNoteId(NOTE_ID);
        org.mockito.Mockito.doThrow(new NoteNotFoundException("already gone"))
                .when(noteService).permanentlyDeleteNote(OWNER_ID, NOTE_ID);

        assertThatThrownBy(() -> applier.apply(OWNER_ID, req))
                .isInstanceOf(NoteNotFoundException.class);

        verify(syncOperationRepository, never()).save(any());
    }
}