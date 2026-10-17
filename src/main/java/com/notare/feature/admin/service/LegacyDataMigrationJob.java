package com.notare.feature.admin.service;

import com.notare.feature.note.dto.request.CreateNoteRequest;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.share.dto.request.ShareNoteRequest;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.service.NoteShareService;
import com.notare.feature.voicenote.dto.response.VoiceUpload;
import com.notare.feature.voicenote.service.VoiceNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Step 18e: the one-off move of v1.0 data into the new shape (master 9.8, decision D-038).
 *
 * <p>Idempotent: every row it handles is marked {@code migrated_at}, and every note or clip it makes
 * has a fixed client id, so a second run finds them and changes nothing. Rows it cannot finish (a
 * share whose recipient is not a connected person) stay unmarked and are tried again next time.
 *
 * <p>Reads the legacy rows with SQL because the owning modules hide trashed and removed rows; every
 * write goes through the owning module's public service. Runs outside one big transaction on
 * purpose: each note, clip and share is its own unit, so one bad row cannot undo the rest.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyDataMigrationJob {

    private static final DateTimeFormatter TITLE_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final JdbcTemplate jdbc;
    private final NoteService noteService;
    private final VoiceNoteService voiceNoteService;
    private final NoteShareService noteShareService;

    /** Counts per table, logged and returned so the operator can compare runs. */
    public record Report(
            int clipClientIdsFilled,
            int standaloneClipsMoved,
            int sharedClipCopiesMade,
            int voiceSharesMigrated,
            int voiceSharesSkipped,
            int failures) {
    }

    private record Clip(long id, long ownerId, LocalDateTime createdAt) {
    }

    private record ShareRow(long shareId, long clipId, long recipientId, long ownerId,
                            Long noteId, boolean standalone, LocalDateTime clipCreatedAt) {
    }

    private static final class Counts {
        int clientIds;
        int moved;
        int copies;
        int migrated;
        int skipped;
        int failures;
    }

    public Report run() {
        Counts counts = new Counts();
        log.warn("Legacy data migration started.");

        fillClipClientIds(counts);
        moveStandaloneClips(counts);
        migrateVoiceShares(counts);

        Report report = new Report(counts.clientIds, counts.moved, counts.copies,
                counts.migrated, counts.skipped, counts.failures);
        log.warn("Legacy data migration finished: {}", report);
        return report;
    }

    /** Clips that predate step 17b have no client id; the sync needs one on every clip. */
    private void fillClipClientIds(Counts counts) {
        List<Long> ids = jdbc.queryForList("select id from voice_notes where client_id is null", Long.class);
        for (Long id : ids) {
            try {
                counts.clientIds += jdbc.update(
                        "update voice_notes set client_id = ?, updated_at = current_timestamp "
                                + "where id = ? and client_id is null",
                        UUID.randomUUID().toString(), id);
            } catch (RuntimeException e) {
                counts.failures++;
                log.error("Could not give clip {} a client id", id, e);
            }
        }
        log.warn("voice_notes: {} client ids filled.", counts.clientIds);
    }

    /** A clip that belongs to no note becomes the only content of a new note "Voice clip, {date}". */
    private void moveStandaloneClips(Counts counts) {
        List<Clip> clips = jdbc.query(
                "select id, owner_id, created_at from voice_notes "
                        + "where note_id is null and migrated_at is null and deleted = false and removed = false "
                        + "order by id",
                (rs, i) -> new Clip(rs.getLong("id"), rs.getLong("owner_id"),
                        rs.getTimestamp("created_at").toLocalDateTime()));

        for (Clip clip : clips) {
            try {
                long noteId = createClipNote(clip.ownerId(), "legacy-clip-" + clip.id(), clip.createdAt());
                voiceNoteService.attachStandaloneClip(clip.ownerId(), clip.id(), noteId);
                jdbc.update("update voice_notes set migrated_at = current_timestamp where id = ?", clip.id());
                counts.moved++;
            } catch (RuntimeException e) {
                counts.failures++;
                log.error("Could not move standalone clip {} into a note", clip.id(), e);
            }
        }
        log.warn("voice_notes: {} standalone clips moved into notes.", counts.moved);
    }

    /**
     * D-038. A shared clip that was standalone is shared through its new note, which holds only the
     * clip. A shared clip attached to a note is copied into a new note and that note is shared, so
     * nobody receives the owner's text by accident. Every migrated share is Viewer (READ).
     */
    private void migrateVoiceShares(Counts counts) {
        List<ShareRow> rows = jdbc.query(
                "select vs.id as share_id, vs.voice_note_id, vs.shared_with_user_id, v.owner_id, v.note_id, "
                        + "v.migrated_at, v.created_at "
                        + "from voice_shares vs join voice_notes v on v.id = vs.voice_note_id "
                        + "where vs.migrated_at is null and v.deleted = false and v.removed = false "
                        + "order by vs.voice_note_id, vs.id",
                (rs, i) -> new ShareRow(
                        rs.getLong("share_id"), rs.getLong("voice_note_id"),
                        rs.getLong("shared_with_user_id"), rs.getLong("owner_id"),
                        (Long) rs.getObject("note_id"), rs.getTimestamp("migrated_at") != null,
                        rs.getTimestamp("created_at").toLocalDateTime()));

        Map<Long, List<ShareRow>> byClip = new LinkedHashMap<>();
        for (ShareRow row : rows) {
            byClip.computeIfAbsent(row.clipId(), id -> new java.util.ArrayList<>()).add(row);
        }

        for (List<ShareRow> shares : byClip.values()) {
            ShareRow first = shares.get(0);
            try {
                Long targetNoteId = first.standalone() ? first.noteId() : copyIntoNewNote(first, counts);
                if (targetNoteId == null) {
                    counts.skipped += shares.size();
                    continue;
                }
                for (ShareRow share : shares) {
                    shareAsViewer(share, targetNoteId, counts);
                }
            } catch (RuntimeException e) {
                counts.failures++;
                log.error("Could not migrate the shares of clip {}", first.clipId(), e);
            }
        }
        log.warn("voice_shares: {} migrated, {} skipped (recipient not connected or error).",
                counts.migrated, counts.skipped);
    }

    private Long copyIntoNewNote(ShareRow share, Counts counts) {
        long noteId = createClipNote(share.ownerId(), "legacy-share-" + share.clipId(), share.clipCreatedAt());
        VoiceUpload copy = voiceNoteService.copyClip(
                share.ownerId(), share.clipId(), noteId, "legacy-share-clip-" + share.clipId());
        if (copy.created()) {
            counts.copies++;
        }
        return noteId;
    }

    private void shareAsViewer(ShareRow share, long noteId, Counts counts) {
        Integer existing = jdbc.queryForObject(
                "select count(*) from note_shares where note_id = ? and shared_with_user_id = ?",
                Integer.class, noteId, share.recipientId());
        boolean alreadyShared = existing != null && existing > 0;

        if (!alreadyShared) {
            try {
                ShareNoteRequest request = new ShareNoteRequest();
                request.setSharedWithUserId(share.recipientId());
                request.setPermission(SharePermission.READ);
                noteShareService.shareNote(share.ownerId(), noteId, request);
            } catch (RuntimeException e) {
                // Most often: the recipient is not an accepted connection. Left unmarked, so a run
                // after they connect finishes it.
                counts.skipped++;
                log.warn("Voice share {} skipped (owner {}, recipient {}): {}",
                        share.shareId(), share.ownerId(), share.recipientId(), e.getMessage());
                return;
            }
        }
        jdbc.update("update voice_shares set migrated_at = current_timestamp where id = ?", share.shareId());
        counts.migrated++;
    }

    private long createClipNote(long ownerId, String clientId, LocalDateTime clipDate) {
        CreateNoteRequest request = new CreateNoteRequest();
        request.setContent("Voice clip, " + clipDate.format(TITLE_DATE));
        return noteService.createIdempotent(ownerId, request, clientId).note().id();
    }
}
