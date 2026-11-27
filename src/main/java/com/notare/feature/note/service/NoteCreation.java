package com.notare.feature.note.service;

import com.notare.feature.note.dto.response.NoteResponse;

/** The result of an idempotent create, and whether this call made the note. */
public record NoteCreation(NoteResponse note, boolean created) {
}
