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
import com.notare.feature.collaboration.service.CollaborationService;
import com.notare.feature.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CollaborationServiceImpl implements CollaborationService {

    private final CollaborationRepository collaborationRepository;
    private final CollaborationMapper collaborationMapper;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CollaborationResponse invite(Long ownerId, Long collaboratorId) {
        if (ownerId.equals(collaboratorId)) {
            throw new InvalidCollaborationException("You cannot invite yourself.");
        }

        // Existence check through the user module's PUBLIC API, not its repository.
        if (!userService.existsById(collaboratorId)) {
            throw new UserNotFoundException(
                    "The user you are trying to invite does not exist."
            );
        }

        /*
         * There is one collaboration row per owner/collaborator pair.
         *
         * The important distinction is the STATUS of an existing row:
         *
         *   PENDING  -> cannot send another invitation
         *   ACCEPTED -> they are already a collaborator
         *   REJECTED -> this is allowed; reuse the row and send a new invitation
         *
         * We deliberately reuse REJECTED instead of inserting a new row because
         * the database has a unique(owner_id, collaborator_id) constraint.
         */
        Collaboration existing = collaborationRepository
                .findByOwnerIdAndCollaboratorId(ownerId, collaboratorId)
                .orElse(null);

        if (existing != null) {
            if (existing.getStatus() == CollaborationStatus.PENDING) {
                throw new CollaborationAlreadyExistsException(
                        "You already have a pending invitation to this user."
                );
            }

            if (existing.getStatus() == CollaborationStatus.ACCEPTED) {
                throw new CollaborationAlreadyExistsException(
                        "You are already collaborating with this user."
                );
            }

            /*
             * REJECTED:
             *
             * Re-open the existing relationship as a fresh invitation.
             * The same row is retained, preserving its identity while the
             * status moves back to PENDING.
             */
            existing.setStatus(CollaborationStatus.PENDING);

            try {
                Collaboration saved = collaborationRepository.saveAndFlush(existing);

                eventPublisher.publishEvent(
                        new CollaborationInvitedEvent(
                                saved.getId(),
                                ownerId,
                                collaboratorId
                        )
                );

                return collaborationMapper.toResponse(saved);
            } catch (DataIntegrityViolationException ex) {
                throw new CollaborationAlreadyExistsException(
                        "Could not send the collaboration invitation."
                );
            }
        }

        /*
         * No previous relationship exists, so create the first row.
         */
        Collaboration collaboration = new Collaboration();
        collaboration.setOwnerId(ownerId);
        collaboration.setCollaboratorId(collaboratorId);
        collaboration.setStatus(CollaborationStatus.PENDING);

        try {
            Collaboration saved = collaborationRepository.saveAndFlush(collaboration);

            eventPublisher.publishEvent(
                    new CollaborationInvitedEvent(
                            saved.getId(),
                            ownerId,
                            collaboratorId
                    )
            );

            return collaborationMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            /*
             * Protect against a concurrent invitation creating the same
             * owner/collaborator pair between our lookup and insert.
             */
            throw new CollaborationAlreadyExistsException(
                    "A collaboration invitation already exists for this user."
            );
        }
    }

    @Override
    @Transactional
    public CollaborationResponse accept(Long currentUserId, Long collaborationId) {
        Collaboration collaboration = requireInviteeInvitation(currentUserId, collaborationId);

        // Capture BEFORE the transition: accept is idempotent, so a second call
        // on an already-ACCEPTED invitation changes nothing and must not notify
        // again. Publishing unconditionally would let a client retry — or a user
        // double-tap — into a duplicate notification for a single real event.
        boolean wasPending = collaboration.getStatus() == CollaborationStatus.PENDING;

        Collaboration updated = transition(collaboration, CollaborationStatus.ACCEPTED);

        if (wasPending) {
            // The OWNER is the one waiting on this answer, so they're the recipient.
            eventPublisher.publishEvent(new CollaborationAcceptedEvent(updated.getId(), updated.getOwnerId(), updated.getCollaboratorId()));
        }
        return collaborationMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public CollaborationResponse reject(Long currentUserId, Long collaborationId) {
        Collaboration collaboration = requireInviteeInvitation(currentUserId, collaborationId);

        boolean wasPending = collaboration.getStatus() == CollaborationStatus.PENDING;

        Collaboration updated = transition(collaboration, CollaborationStatus.REJECTED);

        if (wasPending) {
            eventPublisher.publishEvent(new CollaborationRejectedEvent(
                    updated.getId(), updated.getOwnerId(), updated.getCollaboratorId()));
        }
        return collaborationMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void cancel(Long ownerId, Long collaborationId) {
        // Owner-scoped: only the sender can withdraw. Works whether the invite is
        // still pending or already accepted (removing the collaborator).
        Collaboration collaboration = collaborationRepository
                .findByIdAndOwnerId(collaborationId, ownerId)
                .orElseThrow(() -> new CollaborationNotFoundException("Invitation not found."));

        Long collaboratorId = collaboration.getCollaboratorId();
        collaborationRepository.delete(collaboration);

        // Ending the relationship must also end every note access it authorised.
        // Published rather than calling the share module directly: this module
        // has no idea sharing exists, and shouldn't. The listener runs
        // BEFORE_COMMIT, inside THIS transaction, so the grants and the
        // collaboration disappear together or not at all — a cancel that
        // committed while access survived would be a silent security failure.
        //
        // Where this line sits in the method is immaterial: a BEFORE_COMMIT
        // listener fires during commit preparation, after everything here has
        // run. Publishing is registering an intent, not invoking a callback.
        eventPublisher.publishEvent(new CollaborationRemovedEvent(ownerId, collaboratorId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CollaborationResponse> listSent(Long ownerId, Pageable pageable) {
        return toPage(collaborationRepository.findByOwnerId(ownerId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CollaborationResponse> listReceived(Long currentUserId, Pageable pageable) {
        return toPage(collaborationRepository.findByCollaboratorId(currentUserId, pageable));
    }

    /**
     * The ACCEPTED check the share module gates every grant on. Deliberately a
     * single exists query rather than loading the row: nothing here needs the
     * entity, and an exists compiles to a cheap indexed lookup that answers the
     * question directly.
     *
     * <p>The relationship is directional as modelled — ownerId invited
     * collaboratorId — so this answers "has the person I invited accepted",
     * which is exactly the question an owner sharing their own note is asking.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isAcceptedCollaborator(Long ownerId, Long collaboratorId) {
        return collaborationRepository.existsByOwnerIdAndCollaboratorIdAndStatus(
                ownerId, collaboratorId, CollaborationStatus.ACCEPTED);
    }

    /**
     * Loads an invitation scoped to its INVITEE, so only the invited user can
     * accept or reject it. A mismatch (you're not the invitee, or no such row)
     * returns empty -> 404. The authorization is enforced by the QUERY, not by a
     * separate if-check.
     */
    private Collaboration requireInviteeInvitation(Long currentUserId, Long collaborationId) {
        return collaborationRepository
                .findByIdAndCollaboratorId(collaborationId, currentUserId)
                .orElseThrow(() -> new CollaborationNotFoundException("Invitation not found."));
    }

    /**
     * The state machine. Valid moves:
     *   PENDING -> ACCEPTED  (accept)
     *   PENDING -> REJECTED  (reject)
     * Already at the target status: idempotent no-op. From the opposite terminal
     * state (accept a rejected invite, or reject an accepted one): illegal -> 400.
     */
    private Collaboration transition(Collaboration collaboration, CollaborationStatus target) {
        CollaborationStatus current = collaboration.getStatus();

        if (current == target) {
            return collaboration; // idempotent — already in the requested state
        }
        if (current != CollaborationStatus.PENDING) {
            throw new InvalidCollaborationException(
                    "This invitation is already " + current.name().toLowerCase()
                            + " and cannot be changed.");
        }

        collaboration.setStatus(target);
        // saveAndFlush so updatedAt is refreshed before we map the response.
        return collaborationRepository.saveAndFlush(collaboration);
    }

    private PagedResponse<CollaborationResponse> toPage(Page<Collaboration> page) {
        List<CollaborationResponse> content = page.getContent().stream()
                .map(collaborationMapper::toResponse)
                .toList();
        return PagedResponse.of(content, page);
    }
}