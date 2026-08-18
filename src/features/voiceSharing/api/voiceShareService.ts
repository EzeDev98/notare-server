import type {
  CreateVoiceShareRequest,
  UpdateVoiceShareRequest,
  VoiceShareResponse,
} from '@/features/voiceSharing/types';
import { httpClient } from '@/lib/http/client';
import type { PagedResponse } from '@/lib/http/types';

/**
 * The owner side's HTTP surface for a voice note's grants:
 * `/voice-notes/{voiceNoteId}/shares[/{userId}]`. Mirrors
 * `features/sharing/api/shareService.ts` exactly — same reasoning applies
 * 1:1, just against `/voice-notes` instead of `/notes`. Never
 * `/shared-voice-notes` — that collection is the recipient's view of the
 * same underlying rows, not the owner's (see `sharedVoiceNoteService.ts`).
 *
 * Every method here is scoped to a voice note the CALLER owns — a 404 on any
 * of these means "not your voice note, or no such voice note", conflated on
 * purpose, same pattern shareService establishes.
 */

function shares(voiceNoteId: number): string {
  return `/voice-notes/${String(voiceNoteId)}/shares`;
}

interface Cancellable {
  signal?: AbortSignal;
}

export const voiceShareService = {
  /** All grants on one voice note. No pagination controls exposed, same
   * reasoning as shareService.listShares — a voice note's collaborator
   * count never fills a page in practice. */
  listShares(
    voiceNoteId: number,
    { signal }: Cancellable = {},
  ): Promise<PagedResponse<VoiceShareResponse>> {
    return httpClient.get<PagedResponse<VoiceShareResponse>>(shares(voiceNoteId), {
      ...(signal ? { signal } : {}),
    });
  },

  /**
   * 201 + `Location`. Reads the created grant from the response BODY, same
   * as shareService.createShare and every other 201 in this codebase.
   */
  createShare(
    voiceNoteId: number,
    body: CreateVoiceShareRequest,
    { signal }: Cancellable = {},
  ): Promise<VoiceShareResponse> {
    return httpClient.post<VoiceShareResponse>(shares(voiceNoteId), {
      body,
      ...(signal ? { signal } : {}),
    });
  },

  /** Permission change. `permission` is the only mutable field on a grant. */
  updateShare(
    voiceNoteId: number,
    userId: number,
    body: UpdateVoiceShareRequest,
    { signal }: Cancellable = {},
  ): Promise<VoiceShareResponse> {
    return httpClient.put<VoiceShareResponse>(`${shares(voiceNoteId)}/${String(userId)}`, {
      body,
      ...(signal ? { signal } : {}),
    });
  },

  /** Revoke. The only revocation path for a voice-note grant. */
  revokeShare(voiceNoteId: number, userId: number, { signal }: Cancellable = {}): Promise<void> {
    return httpClient.delete<void>(`${shares(voiceNoteId)}/${String(userId)}`, {
      ...(signal ? { signal } : {}),
    });
  },
};
