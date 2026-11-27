package com.notare.feature.notetidy.service;

import java.util.concurrent.CompletableFuture;

public interface NoteTidyService {

    /**
     * Corrects and formats one note in the background. Never throws: a failure
     * is logged and the note is retried later with backoff. Completes when the
     * note is done, so the picker knows it can be picked again.
     */
    CompletableFuture<Void> tidy(Long noteId);
}
