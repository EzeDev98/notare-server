package com.notare.feature.collaboration.dto.response;

import com.notare.feature.collaboration.enums.CollaborationStatus;

import java.time.LocalDateTime;

/**
 * The collaboration relationship as returned to clients. Carries both party ids
 * plus the status. It deliberately does NOT embed the other user's profile
 * (name/username): enriching every row would mean a user lookup per row (an N+1
 * in list endpoints). The client resolves ids to names via user search / a
 * profile endpoint instead — enrichment can be added later with a batch fetch.
 */
public record CollaborationResponse(
        Long id,
        Long ownerId,
        Long collaboratorId,
        CollaborationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}