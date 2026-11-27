package com.notare.feature.transcription.engine;

import com.notare.feature.transcription.dto.response.TranscriptionResult;
import org.springframework.core.io.Resource;

/** Turns audio into text. */
public interface TranscriptionEngine {

    TranscriptionResult transcribe(Resource audio, String contentType);
}
