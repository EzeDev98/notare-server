package com.notare.feature.collaboration.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.CollaborationAlreadyExistsException;
import com.notare.common.exception.CollaborationNotFoundException;
import com.notare.common.exception.InvalidCollaborationException;
import com.notare.common.exception.UserNotFoundException;
import com.notare.feature.collaboration.dto.response.CollaborationResponse;
import com.notare.feature.collaboration.enums.CollaborationStatus;
import com.notare.feature.collaboration.event.CollaborationAcceptedEvent;
import com.notare.feature.collaboration.event.CollaborationInvitedEvent;
import com.notare.feature.collaboration.event.CollaborationRejectedEvent;
import com.notare.feature.collaboration.event.CollaborationRemovedEvent;
import com.notare.feature.collaboration.mapper.CollaborationMapper;
import com.notare.feature.collaboration.model.Collaboration;
import com.notare.feature.collaboration.repository.CollaborationRepository;
import com.notare.feature.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Step 8a of the coverage roadmap.
 *
 * <p><b>Not executed in this sandbox</b> - Mockito and Spring Data
 * ({@code Page}/{@code Pageable}) are unavailable as compile-time
 * dependencies here (see {@link
 * com.notare.feature.user.service.impl.UserServiceImplementationTest} for the
 * full explanation). {@link CollaborationMapper} itself is real - no
 * third-party dependency of its own - so it's used directly rather than
 * mocked. Reasoned carefully from source; run via {@code mvn test} before
 * trusting it.
 *
 * <p>Covers {@link CollaborationServiceImpl}'s state machine end to end:
 *
 * <ul>
 *   <li>{@code invite} has three distinct outcomes for an existing row
 *       (PENDING -> 409, ACCEPTED -> 409, REJECTED -> reopened as a fresh
 *       PENDING invitation reusing the SAME row, since the database has a
 *       unique(owner, collaborator) constraint) plus the no-prior-row create
 *       path, each with its own {@code DataIntegrityViolationException}
 *       race-loser translation.</li>
 *   <li>{@code accept}/{@code reject} share one {@code transition} state
 *       machine: same-state is an idempotent no-op that does NOT re-publish
 *       an event (a client retry or double-tap must not double-notify), and
 *       transitioning from the OTHER terminal state (accept a rejected
 *       invite, reject an accepted one) is rejected outright.</li>
 *   <li>{@code accept}/{@code reject} are scoped to the INVITEE by the query
 *       itself ({@code findByIdAndCollaboratorId}) - the owner cannot accept
 *       their own invitation by calling this endpoint.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CollaborationServiceImplTest {

    @Mock private CollaborationRepository collaborationRepository;
    @Mock private UserService userService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private final CollaborationMapper collaborationMapper = new CollaborationMapper();

    private CollaborationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CollaborationServiceImpl(collaborationRepository, collaborationMapper, userService, eventPublisher);
    }

    private Collaboration collaboration(Long id, Long ownerId, Long collaboratorId, CollaborationStatus status) {
        Collaboration c = new Collaboration();
        c.setId(id);
        c.setOwnerId(ownerId);
        c.setCollaboratorId(collaboratorId);
        c.setStatus(status);
        return c;
    }

    // ---- invite -------------------------------------------------

    @Test
    void invite_throwsInvalidCollaborationException_whenInvitingSelf() {
        assertThatThrownBy(() -> service.invite(1L, 1L))
                .isInstanceOf(InvalidCollaborationException.class)
                .hasMessage("You cannot invite yourself.");
        verifyNoInteractions(userService, collaborationRepository, eventPublisher);
    }

    @Test
    void invite_throwsUserNotFoundException_whenTheCollaboratorDoesNotExist() {
        when(userService.existsById(2L)).thenReturn(false);

        assertThatThrownBy(() -> service.invite(1L, 2L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("The user you are trying to invite does not exist.");
        verifyNoInteractions(collaborationRepository, eventPublisher);
    }

    @Test
    void invite_throwsCollaborationAlreadyExistsException_whenAPendingInvitationAlreadyExists() {
        when(userService.existsById(2L)).thenReturn(true);
        when(collaborationRepository.findByOwnerIdAndCollaboratorId(1L, 2L))
                .thenReturn(Optional.of(collaboration(10L, 1L, 2L, CollaborationStatus.PENDING)));

        assertThatThrownBy(() -> service.invite(1L, 2L))
                .isInstanceOf(CollaborationAlreadyExistsException.class)
                .hasMessage("You already have a pending invitation to this user.");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void invite_throwsCollaborationAlreadyExistsException_whenAlreadyAccepted() {
        when(userService.existsById(2L)).thenReturn(true);
        when(collaborationRepository.findByOwnerIdAndCollaboratorId(1L, 2L))
                .thenReturn(Optional.of(collaboration(10L, 1L, 2L, CollaborationStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.invite(1L, 2L))
                .isInstanceOf(CollaborationAlreadyExistsException.class)
                .hasMessage("You are already collaborating with this user.");
    }

    @Test
    void invite_reopensARejectedInvitation_asPendingOnTheSameRow_andPublishesInvitedEvent() {
        when(userService.existsById(2L)).thenReturn(true);
        Collaboration existing = collaboration(10L, 1L, 2L, CollaborationStatus.REJECTED);
        when(collaborationRepository.findByOwnerIdAndCollaboratorId(1L, 2L)).thenReturn(Optional.of(existing));
        when(collaborationRepository.saveAndFlush(existing)).thenReturn(existing);

        CollaborationResponse response = service.invite(1L, 2L);

        assertThat(existing.getStatus()).isEqualTo(CollaborationStatus.PENDING);
        assertThat(response.id()).isEqualTo(10L);
        ArgumentCaptor<CollaborationInvitedEvent> captor = ArgumentCaptor.forClass(CollaborationInvitedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CollaborationInvitedEvent(10L, 1L, 2L));
        // Same row reused, not a new insert.
        verify(collaborationRepository, never()).save(any(Collaboration.class));
    }

    @Test
    void invite_wrapsDataIntegrityViolationOnReopen_intoCollaborationAlreadyExistsException() {
        when(userService.existsById(2L)).thenReturn(true);
        Collaboration existing = collaboration(10L, 1L, 2L, CollaborationStatus.REJECTED);
        when(collaborationRepository.findByOwnerIdAndCollaboratorId(1L, 2L)).thenReturn(Optional.of(existing));
        when(collaborationRepository.saveAndFlush(existing)).thenThrow(new DataIntegrityViolationException("race"));

        assertThatThrownBy(() -> service.invite(1L, 2L))
                .isInstanceOf(CollaborationAlreadyExistsException.class)
                .hasMessage("Could not send the collaboration invitation.");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void invite_createsAFreshPendingRow_whenNoPriorRelationshipExists_andPublishesInvitedEvent() {
        when(userService.existsById(2L)).thenReturn(true);
        when(collaborationRepository.findByOwnerIdAndCollaboratorId(1L, 2L)).thenReturn(Optional.empty());
        when(collaborationRepository.saveAndFlush(any(Collaboration.class))).thenAnswer(inv -> {
            Collaboration c = inv.getArgument(0);
            c.setId(20L);
            return c;
        });

        CollaborationResponse response = service.invite(1L, 2L);

        ArgumentCaptor<Collaboration> captor = ArgumentCaptor.forClass(Collaboration.class);
        verify(collaborationRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(1L);
        assertThat(captor.getValue().getCollaboratorId()).isEqualTo(2L);
        assertThat(captor.getValue().getStatus()).isEqualTo(CollaborationStatus.PENDING);
        assertThat(response.id()).isEqualTo(20L);

        verify(eventPublisher).publishEvent(new CollaborationInvitedEvent(20L, 1L, 2L));
    }

    @Test
    void invite_wrapsDataIntegrityViolationOnCreate_intoCollaborationAlreadyExistsException() {
        when(userService.existsById(2L)).thenReturn(true);
        when(collaborationRepository.findByOwnerIdAndCollaboratorId(1L, 2L)).thenReturn(Optional.empty());
        when(collaborationRepository.saveAndFlush(any(Collaboration.class)))
                .thenThrow(new DataIntegrityViolationException("concurrent insert"));

        assertThatThrownBy(() -> service.invite(1L, 2L))
                .isInstanceOf(CollaborationAlreadyExistsException.class)
                .hasMessage("A collaboration invitation already exists for this user.");
        verifyNoInteractions(eventPublisher);
    }

    // ---- accept -------------------------------------------------

    @Test
    void accept_throwsCollaborationNotFoundException_whenNoInvitationIsScopedToTheInvitee() {
        when(collaborationRepository.findByIdAndCollaboratorId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(2L, 10L))
                .isInstanceOf(CollaborationNotFoundException.class)
                .hasMessage("Invitation not found.");
    }

    @Test
    void accept_transitionsPendingToAccepted_andNotifiesTheOwner() {
        Collaboration invitation = collaboration(10L, 1L, 2L, CollaborationStatus.PENDING);
        when(collaborationRepository.findByIdAndCollaboratorId(10L, 2L)).thenReturn(Optional.of(invitation));
        when(collaborationRepository.saveAndFlush(invitation)).thenReturn(invitation);

        CollaborationResponse response = service.accept(2L, 10L);

        assertThat(invitation.getStatus()).isEqualTo(CollaborationStatus.ACCEPTED);
        assertThat(response.status()).isEqualTo(CollaborationStatus.ACCEPTED);
        verify(eventPublisher).publishEvent(new CollaborationAcceptedEvent(10L, 1L, 2L));
    }

    @Test
    void accept_isIdempotent_whenAlreadyAccepted_andDoesNotPublishAgain() {
        Collaboration invitation = collaboration(10L, 1L, 2L, CollaborationStatus.ACCEPTED);
        when(collaborationRepository.findByIdAndCollaboratorId(10L, 2L)).thenReturn(Optional.of(invitation));

        service.accept(2L, 10L);

        verify(collaborationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void accept_throwsInvalidCollaborationException_whenTransitioningFromRejected() {
        Collaboration invitation = collaboration(10L, 1L, 2L, CollaborationStatus.REJECTED);
        when(collaborationRepository.findByIdAndCollaboratorId(10L, 2L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.accept(2L, 10L))
                .isInstanceOf(InvalidCollaborationException.class)
                .hasMessage("This invitation is already rejected and cannot be changed.");
        verifyNoInteractions(eventPublisher);
    }

    // ---- reject -------------------------------------------------

    @Test
    void reject_transitionsPendingToRejected_andNotifiesTheOwner() {
        Collaboration invitation = collaboration(10L, 1L, 2L, CollaborationStatus.PENDING);
        when(collaborationRepository.findByIdAndCollaboratorId(10L, 2L)).thenReturn(Optional.of(invitation));
        when(collaborationRepository.saveAndFlush(invitation)).thenReturn(invitation);

        service.reject(2L, 10L);

        assertThat(invitation.getStatus()).isEqualTo(CollaborationStatus.REJECTED);
        verify(eventPublisher).publishEvent(new CollaborationRejectedEvent(10L, 1L, 2L));
    }

    @Test
    void reject_isIdempotent_whenAlreadyRejected_andDoesNotPublishAgain() {
        Collaboration invitation = collaboration(10L, 1L, 2L, CollaborationStatus.REJECTED);
        when(collaborationRepository.findByIdAndCollaboratorId(10L, 2L)).thenReturn(Optional.of(invitation));

        service.reject(2L, 10L);

        verify(collaborationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void reject_throwsInvalidCollaborationException_whenTransitioningFromAccepted() {
        Collaboration invitation = collaboration(10L, 1L, 2L, CollaborationStatus.ACCEPTED);
        when(collaborationRepository.findByIdAndCollaboratorId(10L, 2L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.reject(2L, 10L))
                .isInstanceOf(InvalidCollaborationException.class)
                .hasMessage("This invitation is already accepted and cannot be changed.");
    }

    // ---- cancel -------------------------------------------------

    @Test
    void cancel_throwsCollaborationNotFoundException_whenNoInvitationIsScopedToTheOwner() {
        when(collaborationRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(1L, 10L))
                .isInstanceOf(CollaborationNotFoundException.class)
                .hasMessage("Invitation not found.");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancel_deletesTheRow_andPublishesCollaborationRemovedEvent() {
        Collaboration invitation = collaboration(10L, 1L, 2L, CollaborationStatus.ACCEPTED);
        when(collaborationRepository.findByIdAndOwnerId(10L, 1L)).thenReturn(Optional.of(invitation));

        service.cancel(1L, 10L);

        verify(collaborationRepository).delete(invitation);
        verify(eventPublisher).publishEvent(new CollaborationRemovedEvent(1L, 2L));
    }

    // ---- listSent / listReceived -------------------------------------------------

    @Test
    void listSent_mapsTheOwnersInvitationsToResponses() {
        Collaboration invitation = collaboration(10L, 1L, 2L, CollaborationStatus.PENDING);
        Page<Collaboration> page = new PageImpl<>(List.of(invitation));
        when(collaborationRepository.findByOwnerId(eq(1L), any())).thenReturn(page);

        PagedResponse<CollaborationResponse> result = service.listSent(1L, Pageable.unpaged());

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(10L);
    }

    @Test
    void listReceived_mapsTheInviteesInvitationsToResponses() {
        Collaboration invitation = collaboration(10L, 1L, 2L, CollaborationStatus.PENDING);
        Page<Collaboration> page = new PageImpl<>(List.of(invitation));
        when(collaborationRepository.findByCollaboratorId(eq(2L), any())).thenReturn(page);

        PagedResponse<CollaborationResponse> result = service.listReceived(2L, Pageable.unpaged());

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).id()).isEqualTo(10L);
    }

    // ---- isAcceptedCollaborator -------------------------------------------------

    @Test
    void isAcceptedCollaborator_delegatesToTheExistsQuery_scopedToAcceptedStatus() {
        when(collaborationRepository.existsByOwnerIdAndCollaboratorIdAndStatus(1L, 2L, CollaborationStatus.ACCEPTED))
                .thenReturn(true);

        assertThat(service.isAcceptedCollaborator(1L, 2L)).isTrue();
        verify(collaborationRepository).existsByOwnerIdAndCollaboratorIdAndStatus(1L, 2L, CollaborationStatus.ACCEPTED);
    }
}