package com.notare.feature.notetidy.service;

import java.util.concurrent.CompletableFuture;

/** Tidies one note in the background. */
public interface NoteTidyService {

    CompletableFuture<Void> tidy(Long noteId);
}
