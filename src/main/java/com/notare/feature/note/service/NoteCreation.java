package com.notare.feature.note.service;

import com.notare.feature.note.dto.response.NoteResponse;

/**
 * The outcome of an idempotent create: the note, and whether this call made it.
 * {@code created} is false when the same client id had already made a note, so the
 * controller can answer 200 instead of 201.
 */
public record NoteCreation(NoteResponse note, boolean created) {
}
