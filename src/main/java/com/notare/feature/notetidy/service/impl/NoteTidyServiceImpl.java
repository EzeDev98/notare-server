package com.notare.feature.notetidy.service.impl;

import com.notare.feature.note.model.Note;
import com.notare.feature.note.repository.NoteRepository;
import com.notare.feature.notetidy.config.NoteTidyProperties;
import com.notare.feature.notetidy.engine.CorrectionResult;
import com.notare.feature.notetidy.engine.TextCorrector;
import com.notare.feature.notetidy.engine.TitleEngine;
import com.notare.feature.notetidy.format.NoteFormatter;
import com.notare.feature.notetidy.service.NoteTidyService;
import com.notare.feature.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * One note, start to finish: correct, format, infer its title, then save only
 * if nobody edited the note in the meantime. Nothing here ever reaches the
 * user except the better note itself.
 *
 * <p>No transaction is held across the LanguageTool or title calls. The note
 * is read, the work is done, and the save is a single conditional update on
 * the version that was read (each repository write runs in its own transaction).
 *
 * <p>The title is the AI's alone: inferred from the whole note, stored in
 * ai_title and never written into the note's text. It is inferred again each
 * time an edited note is tidied, for notes of at least min-words-for-title
 * words. A shorter note keeps the title it had.
 *
 * <p>Failures retry with backoff. After max-attempts the note is left alone at
 * that version, and an edit gives it a fresh set of attempts.
 *
 * <p>Counted in notare.tidy.notes and notare.tidy.titles, by outcome.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteTidyServiceImpl implements NoteTidyService {

    /** The user edited during the job: look again after this long. */
    private static final long EDITED_RETRY_MINUTES = 5;

    /** First failure waits this long, then it doubles each time. */
    private static final long FAILURE_BACKOFF_MINUTES = 5;

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final TextCorrector textCorrector;
    private final NoteFormatter noteFormatter;
    private final TitleEngine titleEngine;
    private final NoteTidyProperties properties;
    private final MeterRegistry meterRegistry;

    @Override
    @Async("noteTidyExecutor")
    public CompletableFuture<Void> tidy(Long noteId) {
        Optional<Note> found = noteRepository.findById(noteId);
        if (found.isEmpty() || found.get().isDeleted()) {
            return CompletableFuture.completedFuture(null);
        }
        Note note = found.get();
        long version = note.getVersion();
        String original = note.getContent() == null ? "" : note.getContent();

        try {
            if (original.isBlank()) {
                leave(noteId, version, "empty");
                return CompletableFuture.completedFuture(null);
            }

            if (original.length() > properties.getMaxChars()) {
                log.debug("AI tidy: note {} skipped, {} characters", noteId, original.length());
                leave(noteId, version, "too_long");
                return CompletableFuture.completedFuture(null);
            }

            CorrectionResult corrected = textCorrector.correct(original);
            if (!corrected.isEnglish()) {
                log.debug("AI tidy: note {} skipped, language {}", noteId, corrected.languageCode());
                leave(noteId, version, "not_english");
                return CompletableFuture.completedFuture(null);
            }

            String body = noteFormatter.format(corrected.text());

            // Never lose words: a fix may change a word, never the number of them.
            if (wordCount(body) != wordCount(original)) {
                log.warn("AI tidy: note {} result discarded, word count changed", noteId);
                leave(noteId, version, "word_count_changed");
                return CompletableFuture.completedFuture(null);
            }

            String title = titleFor(note, body);
            boolean textChanged = !body.equals(original);

            if (!textChanged && Objects.equals(title, note.getAiTitle())) {
                leave(noteId, version, "unchanged");
                return CompletableFuture.completedFuture(null);
            }

            // Only a text change has an original worth restoring; a new title alone does not.
            LocalDateTime now = LocalDateTime.now();
            int saved = noteRepository.applyTidy(
                    noteId, version, body, textChanged ? original : null, title, now);
            if (saved == 0) {
                // Edited (or deleted) while the job ran: the user's version wins.
                noteRepository.deferTidy(noteId, now.plusMinutes(EDITED_RETRY_MINUTES));
                count("edited_during_job");
                log.debug("AI tidy: note {} changed during the job, result discarded", noteId);
            } else {
                count("tidied");
                log.info("AI tidy: note {} tidied (version {} -> {})", noteId, version, version + 1);
            }
        } catch (RuntimeException failure) {
            recordFailure(note, version, failure);
        }
        return CompletableFuture.completedFuture(null);
    }

    /** Nothing to save at this version: remember it, so the note waits for its next edit. */
    private void leave(Long noteId, long version, String outcome) {
        noteRepository.markTidied(noteId, version);
        count(outcome);
    }

    private void recordFailure(Note note, long version, RuntimeException failure) {
        int attempt = note.getTidyAttempts() + 1;
        if (attempt >= properties.getMaxAttempts()) {
            // Give up quietly at this version. An edit moves the version on,
            // and the note is then tried again with a fresh set of attempts.
            noteRepository.markTidied(note.getId(), version);
            count("gave_up");
            log.warn("AI tidy: note {} failed {} times, leaving it until it is edited", note.getId(), attempt, failure);
            return;
        }
        long waitMinutes = FAILURE_BACKOFF_MINUTES << Math.min(attempt - 1, 10);
        noteRepository.recordTidyFailure(note.getId(), LocalDateTime.now().plusMinutes(waitMinutes));
        count("failed");
        log.warn("AI tidy: note {} failed (attempt {} of {}), retrying in {} min",
                note.getId(), attempt, properties.getMaxAttempts(), waitMinutes, failure);
    }

    /**
     * The note's title, inferred from its whole text. Keeps the title it had when
     * the note is too short, today's allowance is used up, or nothing usable came back.
     */
    private String titleFor(Note note, String body) {
        String current = note.getAiTitle();
        if (wordCount(body) < properties.getMinWordsForTitle()) {
            return current;
        }
        int granted = userRepository.takeTidyTitle(
                note.getOwnerId(), LocalDate.now(), properties.getDailyTitleCapPerUser());
        if (granted == 0) {
            countTitle("cap_reached");
            return current;
        }
        try {
            Optional<String> title = titleEngine.inferTitle(body)
                    .map(String::strip)
                    .filter(candidate -> !candidate.isEmpty());
            countTitle(title.isPresent() ? "inferred" : "none");
            return title.orElse(current);
        } catch (RuntimeException failure) {
            // A title is optional: the corrections still go ahead without a new one.
            countTitle("engine_failed");
            log.warn("AI tidy: no title for note {}, title engine failed", note.getId(), failure);
            return current;
        }
    }

    private void count(String outcome) {
        meterRegistry.counter("notare.tidy.notes", "outcome", outcome).increment();
    }

    private void countTitle(String outcome) {
        meterRegistry.counter("notare.tidy.titles", "outcome", outcome).increment();
    }

    private static long wordCount(String text) {
        return Arrays.stream(text.split("[^\\p{L}\\p{N}']+"))
                .filter(word -> !word.isEmpty())
                .count();
    }
}
