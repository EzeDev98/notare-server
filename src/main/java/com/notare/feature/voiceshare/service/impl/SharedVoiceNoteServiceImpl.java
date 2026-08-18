package com.notare.feature.voiceshare.service.impl;

import com.notare.common.dto.PagedResponse;
import com.notare.common.exception.InsufficientVoiceNotePermissionException;
import com.notare.common.exception.VoiceShareNotFoundException;
import com.notare.feature.share.enums.SharePermission;
import com.notare.feature.voicenote.dto.request.VoiceNoteContent;
import com.notare.feature.voicenote.dto.response.VoiceNoteResponse;
import com.notare.feature.voicenote.service.VoiceNoteService;
import com.notare.feature.voiceshare.dto.response.SharedVoiceNoteSummaryResponse;
import com.notare.feature.voiceshare.mapper.VoiceShareMapper;
import com.notare.feature.voiceshare.model.VoiceShare;
import com.notare.feature.voiceshare.service.SharedVoiceNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors {@link com.notare.feature.share.service.impl.SharedNoteServiceImpl}
 * 1:1, including the N+1-avoidance pattern in {@link #listSharedWithMe} and
 * the two-distinct-failures shape of {@link #requireGrant}.
 */
@Service
@RequiredArgsConstructor
public class SharedVoiceNoteServiceImpl implements SharedVoiceNoteService {

    private final VoiceShareRepository voiceShareRepository;
    private final VoiceShareMapper voiceShareMapper;

    /** Cross-module, PUBLIC API. This is the only class that calls the pre-authorized accessors. */
    private final VoiceNoteService voiceNoteService;

    /**
     * Resolves a page of grants into a page of voice notes with ONE extra
     * query, not one per row — same N+1-avoidance as SharedNoteServiceImpl:
     * collect every voice note id on the page, batch-resolve them, join in
     * memory.
     */
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SharedVoiceNoteSummaryResponse> listSharedWithMe(Long userId, Pageable pageable) {
        Page<VoiceShare> page = voiceShareRepository.findBySharedWithUserId(userId, pageable);

        List<Long> voiceNoteIds = page.getContent().stream()
                .map(VoiceShare::getVoiceNoteId)
                .toList();

        Map<Long, VoiceNoteResponse> voiceNotes = voiceNoteService.getAuthorizedVoiceNoteSummaries(voiceNoteIds);

        List<SharedVoiceNoteSummaryResponse> content = page.getContent().stream()
                .map(share -> {
                    VoiceNoteResponse voiceNote = voiceNotes.get(share.getVoiceNoteId());
                    // Absent means the voice note was deleted after the grant was
                    // made. The grant is stale; drop the row rather than render
                    // an entry with a hole where the voice note should be.
                    return voiceNote == null ? null : voiceShareMapper.toSharedVoiceNote(share, voiceNote);
                })
                .filter(Objects::nonNull)
                .toList();

        // Same KNOWN INCONSISTENCY as SharedNoteServiceImpl, accepted for the
        // same reason: totalElements counts grants including ones whose voice
        // note has since been deleted, so a page can return fewer rows than
        // the count implies. Not worth the cross-module query coupling to fix
        // before this becomes a real problem in practice.
        return PagedResponse.of(content, page);
    }

    @Override
    @Transactional(readOnly = true)
    public SharedVoiceNoteSummaryResponse getSharedVoiceNote(Long userId, Long voiceNoteId) {
        VoiceShare share = requireGrant(userId, voiceNoteId, SharePermission.READ);
        VoiceNoteResponse voiceNote = voiceNoteService.getAuthorizedVoiceNote(voiceNoteId);
        return voiceShareMapper.toSharedVoiceNote(share, voiceNote);
    }

    @Override
    @Transactional(readOnly = true)
    public VoiceNoteContent getAudio(Long userId, Long voiceNoteId) {
        requireGrant(userId, voiceNoteId, SharePermission.READ);
        // Only reached once a grant is confirmed — this is the "authorized"
        // the accessor's name is claiming.
        return voiceNoteService.getAuthorizedAudio(voiceNoteId);
    }

    /**
     * The single authorization gate for this whole service. Same two-distinct-
     * failures shape as SharedNoteServiceImpl#requireGrant:
     *
     * <ul>
     *   <li><b>No grant at all -> 404.</b> As far as this user is concerned the
     *       voice note doesn't exist.</li>
     *   <li><b>Grant too weak -> 403.</b> Reserved for a future WRITE capability;
     *       every current caller only ever requires READ, so this branch isn't
     *       reachable yet, but the gate is written to the general contract
     *       rather than hardcoded to READ.</li>
     * </ul>
     */
    private VoiceShare requireGrant(Long userId, Long voiceNoteId, SharePermission required) {
        VoiceShare share = voiceShareRepository
                .findByVoiceNoteIdAndSharedWithUserId(voiceNoteId, userId)
                .orElseThrow(() -> new VoiceShareNotFoundException("Voice note not found."));

        if (!share.getPermission().satisfies(required)) {
            throw new InsufficientVoiceNotePermissionException(
                    "You have read-only access to this voice note.");
        }
        return share;
    }
}
