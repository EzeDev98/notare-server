package com.notare.feature.share.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InsufficientNotePermissionException;
import com.notare.common.exception.NoteShareNotFoundException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SharedNoteServiceImpl implements SharedNoteService {

    private final NoteShareRepository noteShareRepository;
    private final NoteShareMapper noteShareMapper;

    /** Cross-module, PUBLIC API. This is the only class that calls the pre-authorized accessors. */
    private final NoteService noteService;

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
}