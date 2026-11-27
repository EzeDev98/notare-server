package com.notare.feature.transcription.mapper;

import com.notare.feature.transcription.dto.response.TranscriptionResponse;
import com.notare.feature.transcription.model.Transcription;
import org.springframework.stereotype.Component;

/** Maps transcriptions to their response. */
@Component
public class TranscriptionMapper {

    public TranscriptionResponse toResponse(Transcription transcription) {
        return new TranscriptionResponse(
                transcription.getId(),
                transcription.getVoiceNoteId(),
                transcription.getStatus(),
                transcription.getTranscript(),
                transcription.getConfidence(),
                transcription.getLanguage(),
                transcription.getErrorMessage(),
                transcription.getCreatedAt(),
                transcription.getUpdatedAt()
        );
    }
}
