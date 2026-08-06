package com.notare.feature.collaboration.service;

import com.notare.common.dto.PagedResponse;
import com.notare.feature.collaboration.dto.response.CollaborationResponse;
import org.springframework.data.domain.Pageable;

public interface CollaborationService {

    /** Owner invites a user. */
    CollaborationResponse invite(Long ownerId, Long collaboratorId);

    /** Invitee accepts an invitation addressed to them. */
    CollaborationResponse accept(Long currentUserId, Long collaborationId);

    /** Invitee rejects an invitation addressed to them. */
    CollaborationResponse reject(Long currentUserId, Long collaborationId);

    /** Owner withdraws an invitation / removes the relationship. */
    void cancel(Long ownerId, Long collaborationId);

    /** Invitations I sent (I'm the owner). */
    PagedResponse<CollaborationResponse> listSent(Long ownerId, Pageable pageable);

    /** Invitations I received (I'm the collaborator). */
    PagedResponse<CollaborationResponse> listReceived(Long currentUserId, Pageable pageable);

    /**
     * Has {@code collaboratorId} accepted an invitation from {@code ownerId}?
     *
     * <p>This module's public answer to "who am I allowed to share a note
     * with?". The share module (Step 7) calls it before creating any grant, the
     * same way the transcription module asks the voice-note module rather than
     * touching its repository. A boolean is returned, not a Collaboration, so
     * the caller gets exactly the fact it needs and stays ignorant of this
     * module's entity — the narrower the cross-module surface, the freer this
     * module is to change internally.
     */
    boolean isAcceptedCollaborator(Long ownerId, Long collaboratorId);
}