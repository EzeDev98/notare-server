package com.notare.feature.collaboration.mapper;

import com.notare.feature.collaboration.dto.response.CollaborationResponse;
import com.notare.feature.collaboration.model.Collaboration;
import org.springframework.stereotype.Component;

@Component
public class CollaborationMapper {

    public CollaborationResponse toResponse(Collaboration c) {
        return new CollaborationResponse(
                c.getId(),
                c.getOwnerId(),
                c.getCollaboratorId(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}