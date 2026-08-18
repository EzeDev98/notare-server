package com.notare.feature.voiceshare.repository;

import com.notare.feature.voiceshare.model.VoiceShare;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Mirrors {@link com.notare.feature.share.repository.NoteShareRepository}
 * exactly. Finders here follow the same rule as every other repository in
 * the project: the scoping is baked into the QUERY, not applied as an
 * if-check afterwards. A caller who isn't entitled to a row gets an empty
 * Optional and a 404, so the API never confirms that someone else's voice
 * note exists.
 */
public interface VoiceShareRepository extends JpaRepository<VoiceShare, Long> {

    /**
     * The access check for the recipient path: "do I have a grant on this
     * voice note, and at what level?" The level is compared with
     * SharePermission.satisfies(...) by the service — this method
     * deliberately doesn't filter by permission, so a READ-only recipient
     * attempting a write can be told 403 rather than 404. They're allowed to
     * know the voice note exists; they just can't edit it.
     */
    Optional<VoiceShare> findByVoiceNoteIdAndSharedWithUserId(Long voiceNoteId, Long sharedWithUserId);

    /**
     * The owner-side handle on one grant, addressed by (voice note,
     * recipient) rather than by share id — that's how the API will express
     * it (DELETE /voice-notes/{id}/shares/{userId}), and it saves the client
     * having to track an opaque share id it never sees.
     */
    Optional<VoiceShare> findByVoiceNoteIdAndOwnerIdAndSharedWithUserId(
            Long voiceNoteId, Long ownerId, Long sharedWithUserId);

    /** Friendly pre-check before the unique constraint fires on a duplicate share. */
    boolean existsByVoiceNoteIdAndSharedWithUserId(Long voiceNoteId, Long sharedWithUserId);

    /**
     * "Who is this voice note shared with?" — owner-scoped, so passing
     * someone else's voiceNoteId returns an empty page rather than leaking
     * their collaborator list.
     */
    Page<VoiceShare> findByVoiceNoteIdAndOwnerId(Long voiceNoteId, Long ownerId, Pageable pageable);

    /** "Voice notes shared with me" — the recipient's collection. */
    Page<VoiceShare> findBySharedWithUserId(Long sharedWithUserId, Pageable pageable);

    /**
     * Revoke everything one owner granted to one person. Used when a
     * collaboration is cancelled: ending the relationship must also end
     * every voice-note access it authorised, or removing a collaborator
     * would leave live grants behind.
     *
     * <p>Derived deletes load the matching rows and delete them one by one,
     * so this needs an active transaction — the caller supplies it. That's
     * fine at these volumes and it lets entity callbacks run; a @Modifying
     * bulk delete would be the move if the row counts ever got large.
     */
    void deleteByOwnerIdAndSharedWithUserId(Long ownerId, Long sharedWithUserId);

    /**
     * Drop every grant on a voice note. Needed when a voice note is
     * permanently deleted — otherwise its grants outlive it as orphan rows,
     * and a future voice note reusing that id would inherit them.
     */
    void deleteByVoiceNoteId(Long voiceNoteId);
}
