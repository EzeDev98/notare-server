package com.notare.feature.transcription.engine;

import com.notare.feature.transcription.dto.response.TranscriptionResult;
import org.springframework.core.io.Resource;

/**
 * Abstraction over "the thing that turns audio into text". Same Strategy shape
 * as FileStorageService: the app depends on this interface, and the concrete
 * engine (dev stub now, a real provider like Whisper/AWS Transcribe later) is
 * selected by profile. The caller loads the audio and hands it in as a Resource,
 * so the engine doesn't know or care where the bytes came from.
 */
public interface TranscriptionEngine {

    TranscriptionResult transcribe(Resource audio, String contentType);
}