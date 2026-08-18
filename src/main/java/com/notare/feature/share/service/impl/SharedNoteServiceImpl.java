package com.notare.feature.share.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InsufficientNotePermissionException;
import com.notare.common.exception.NoteShareNotFoundException;
import com.notare.feature.note.dto.request.AutoSaveNoteRequest;
import com.notare.feature.note.dto.request.UpdateNoteRequest;
import com.notare.feature.note.dto.response.NoteResponse;
import com.notare.feature.note.dto.response.NoteSummaryResponse;
import com.notare.feature.note.service.NoteService;
import com.notare.feature.share.dto.response.SharedNoteSummaryResponse;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.share.mapper.NoteShareMapper;
import com.notare.feature.share.model.NoteShare;
import com.notare.feature.share.repository.NoteShareRepository;
import com.notare.feature.share.service.SharedNoteService;
import com.notare.feature.voiceshare.dto.request.ShareVoiceNoteRequest;
import com.notare.feature.voiceshare.service.VoiceShareService;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharedNoteServiceImpl implements SharedNoteService {

    private final NoteShareRepository noteShareRepository;
    private final NoteShareMapper noteShareMapper;

    /** Cross-module, PUBLIC API. This is the only class that calls the pre-authorized accessors. */
    private final NoteService noteService;

    /**
     * Cross-module, PUBLIC API. Added for the note-attached-voice-notes
     * capability: listing a note's attachments and letting a WRITE
     * collaborator record a new one directly onto a shared note.
     */
    private final VoiceNoteService voiceNoteService;

    /**
     * Also cross-module, PUBLIC API. Used ONLY to create the courtesy grant
     * back to the note's owner after a collaborator's upload — never to
     * gate access to an EXISTING voice note, which stays this class's own
     * job via requireGrant on the parent note.
     */
    private final VoiceShareService voiceShareService;

    /**
     * Resolves a page of grants into a page of notes with ONE extra query, not
     * one per row.
     *
     * <p>The obvious implementation — loop the grants, fetch each note — is the
     * N+1 that has been flagged since Step 6: a page of 20 becomes 21 queries,
     * and it degrades linearly with page size, so it looks fine in dev and hurts
     * in production. Instead: collect every note id on the page, resolve them all
     * at once, then join in memory.
     */
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SharedNoteSummaryResponse> listSharedWithMe(Long userId, Pageable pageable) {
        Page<NoteShare> page = noteShareRepository.findBySharedWithUserId(userId, pageable);

        List<Long> noteIds = page.getContent().stream()
                .map(NoteShare::getNoteId)
                .toList();

        // One batch query for the whole page, keyed by note id so the join below
        // doesn't depend on the order rows came back in.
        Map<Long, NoteSummaryResponse> summaries = noteService.getAuthorizedNoteSummaries(noteIds);

        List<SharedNoteSummaryResponse> content = page.getContent().stream()
                .map(share -> {
                    NoteSummaryResponse summary = summaries.get(share.getNoteId());
                    // Absent means the note was soft-deleted (or hard-deleted)
                    // after the grant was made. The grant is stale; drop the row
                    // rather than render an entry with a hole where the note
                    // should be. A trashed note is invisible to collaborators for
                    // the same reason it's invisible in the owner's own list.
                    return summary == null ? null : noteShareMapper.toSharedNote(share, summary);
                })
                .filter(Objects::nonNull)
                .toList();

        // KNOWN INCONSISTENCY, accepted for the MVP: totalElements counts grants,
        // including ones whose note has since been trashed, so a page can return
        // fewer rows than the count implies. The clean fix is to filter in the
        // database — a JPQL query joining NoteShare to Note on deleted = false —
        // but that puts the Note entity inside this module's queries, which is a
        // tighter coupling than the service-call boundary used everywhere else.
        // Worth doing if trashed shared notes ever become common; not worth the
        // coupling before then.
        return PagedResponse.of(content, page);
    }

    @Override
    @Transactional(readOnly = true)
    public NoteResponse getSharedNote(Long userId, Long noteId) {
        requireGrant(userId, noteId, SharePermission.READ);
        // Only reached once a grant is confirmed — this is the "authorized"
        // the accessor's name is claiming.
        return noteService.getAuthorizedNote(noteId);
    }

    @Override
    @Transactional
    public NoteResponse updateSharedNote(Long userId, Long noteId, UpdateNoteRequest request) {
        requireGrant(userId, noteId, SharePermission.WRITE);
        // Routes into the SAME version-checked write the owner uses, so an owner
        // and a collaborator editing simultaneously hit Step 2's optimistic lock
        // and one of them gets a 409 carrying the current version. That race is
        // no longer hypothetical — this method is what makes it real.
        return noteService.updateAuthorizedNote(noteId, request);
    }

    @Override
    @Transactional
    public NoteResponse autoSaveSharedNote(Long userId, Long noteId, AutoSaveNoteRequest request) {
        // WRITE, same as the full update. The size of the write is not the
        // question the permission answers.
        requireGrant(userId, noteId, SharePermission.WRITE);

        // Routes into the same version-checked partial write the owner's PATCH
        // uses, so a collaborator's auto-save and an owner's auto-save race
        // against each other through one optimistic lock rather than two
        // implementations that agree today.
        return noteService.autoSaveAuthorizedNote(noteId, request);
    }

    /**
     * The single authorization gate for this whole service. Two distinct
     * failures, two distinct answers:
     *
     * <ul>
     *   <li><b>No grant at all -> 404.</b> As far as this user is concerned the
     *       note doesn't exist, and the response must not suggest otherwise —
     *       a different status for "exists but not yours" would let anyone map
     *       out note ids by probing.</li>
     *   <li><b>Grant too weak -> 403.</b> A READ recipient attempting a write
     *       already knows the note exists; they can open it. Hiding it here
     *       would be incoherent, and the honest answer is "you may not do
     *       that".</li>
     * </ul>
     *
     * <p>The comparison goes through {@link SharePermission#satisfies} rather
     * than an equality check, so WRITE naturally satisfies a READ requirement
     * and the implication rule stays in one place.
     */
    private NoteShare requireGrant(Long userId, Long noteId, SharePermission required) {
        NoteShare share = noteShareRepository
                .findByNoteIdAndSharedWithUserId(noteId, userId)
                .orElseThrow(() -> new NoteShareNotFoundException("Note not found."));

        if (!share.getPermission().satisfies(required)) {
            throw new InsufficientNotePermissionException(
                    "You have read-only access to this note.");
        }
        return share;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VoiceNoteResponse> listAttachedVoiceNotes(Long userId, Long noteId) {
        requireGrant(userId, noteId, SharePermission.READ);
        return voiceNoteService.getAuthorizedVoiceNotesByNote(noteId);
    }

    /**
     * Deliberately NOT @Transactional at this level, same reasoning as
     * VoiceNoteService#upload itself: file storage I/O must not happen while
     * holding a database connection open. The two writes this method makes —
     * the voice note row (inside voiceNoteService.upload, its own boundary)
     * and the courtesy VoiceShare grant (inside voiceShareService.shareVoiceNote,
     * its own @Transactional boundary) — are two separate, sequential
     * transactions, not one atomic unit. That is an accepted tradeoff, not an
     * oversight: seeing the tradeoff explained below.
     */
    @Override
    public VoiceNoteResponse uploadVoiceNote(Long userId, Long noteId, MultipartFile file, Integer durationSeconds) {
        NoteShare share = requireGrant(userId, noteId, SharePermission.WRITE);

        VoiceNoteResponse uploaded = voiceNoteService.upload(userId, file, noteId, durationSeconds);

        // Courtesy grant back to the note's owner, best-effort. The recording
        // itself has already succeeded and stored bytes on disk by this point;
        // failing the whole request over a grant-creation hiccup would leave
        // the recipient thinking their reply was lost when it wasn't. Worst
        // case on failure here: the note owner has to be re-shared this one
        // voice note by hand, or simply re-open the note (the note-attachment
        // listing above finds it regardless of any VoiceShare grant — the
        // grant only matters for the voice note's OWN direct endpoints).
        try {
            ShareVoiceNoteRequest grantRequest = new ShareVoiceNoteRequest();
            grantRequest.setSharedWithUserId(share.getOwnerId());
            grantRequest.setPermission(SharePermission.READ);
            voiceShareService.shareVoiceNote(userId, uploaded.id(), grantRequest);
        } catch (RuntimeException ex) {
            log.warn("Could not auto-share voice note {} back to note owner {}: {}",
                    uploaded.id(), share.getOwnerId(), ex.getMessage());
        }

        return uploaded;
    }

    /**
     * Gated by the PARENT NOTE's grant, then a belt-and-suspenders check that
     * the voice note ACTUALLY belongs to that note — without it, a caller
     * with READ on note A could pass note A's id alongside note B's
     * voiceNoteId and stream note B's audio, so long as they could guess or
     * enumerate a valid voice note id. The path noteId must not be trusted
     * just because it FORMS a valid URL; it has to match the voice note's own
     * record of which note it's attached to.
     */
    @Override
    @Transactional(readOnly = true)
    public VoiceNoteContent getAttachedVoiceNoteAudio(Long userId, Long noteId, Long voiceNoteId) {
        requireGrant(userId, noteId, SharePermission.READ);

        VoiceNoteResponse voiceNote = voiceNoteService.getAuthorizedVoiceNote(voiceNoteId);
        if (!noteId.equals(voiceNote.noteId())) {
            throw new NoteShareNotFoundException("Note not found.");
        }

        return voiceNoteService.getAuthorizedAudio(voiceNoteId);
    }
}