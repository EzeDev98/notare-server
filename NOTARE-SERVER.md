# Notare Server — Implementation Reference

Backend for Notare, a note-taking application with voice notes, transcription, collaboration, sharing, notifications and offline sync.

This document covers everything built across Steps 1–10, the reasoning behind each decision, the full endpoint surface, and the known gaps and bugs still outstanding. It is written to be read by someone picking the project up cold, and by future-you deciding whether a past decision still holds.

**Status:** all ten planned steps complete and manually tested. Cross-cutting hardening not yet started.

**Verified against code on 2026-10-09** (overhaul step 1g). Where this document and the controllers differ, the code wins (B-20).

---

## 1. Stack and build

| Concern | Choice |
|---|---|
| Framework | Spring Boot **3.2.5** |
| Language | Java 21 |
| Modularity | Spring Modulith 1.1.12 |
| Persistence | Spring Data JPA / Hibernate |
| DB (dev) | MySQL, `ddl-auto=update`, port 2001 |
| DB (prod) | PostgreSQL, `ddl-auto=validate` |
| DB (test) | H2 |
| Sessions / refresh tokens | Redis |
| Auth | JWT (jjwt 0.13.0) + refresh-token sessions |
| Email | Mailjet |
| API docs | springdoc-openapi 2.5.0 (`/swagger-ui/index.html`) |
| Boilerplate | Lombok |

`spring.jpa.open-in-view=false` — lazy loading never leaks into the view layer; every fetch is explicit.

### A note on the version pairing

Boot and Modulith and springdoc all move together and none of them guarantee cross-version compatibility:

- Boot 3.2.x → Modulith **1.1.x** → springdoc **2.5.x**
- Boot 4.x → Modulith 2.x → springdoc 3.x

The project was briefly on Boot 4.1.0 with springdoc 2.5.0, which produced a Swagger endpoint that returned 403 while the app otherwise started fine. **When you bump Spring Boot, check all three.** This class of failure doesn't fail at startup — only one feature quietly dies.

> **Boot 3.2.x is past OSS support.** Boot 3.5.x + springdoc 2.8.x + Modulith 1.4.x would get security patches with an almost identical pom. Noted, not urgent, your call.

---

## 2. Package layout

```
com.notare
├── config/                     AppConfig, WebMvcConfig, SpringDoc*, OtpProperties
├── common/
│   ├── audit/                  SystemAuditor, SecurityAuditorAware
│   ├── constant/
│   ├── dto/                    PagedResponse<T>
│   ├── exception/              all custom exceptions + GlobalExceptionHandler
│   ├── security/               WebSecurityConfig, JwtService, JwtAuthenticationFilter
│   ├── storage/                FileStorageService, LocalFileStorageService, StorageProperties
│   └── web/                    @ClientIp, @UserAgent + argument resolvers
└── feature/
    ├── auth/ login/ logout/ passwordreset/ session/ user/    (pre-existing)
    ├── note/            Steps 1–3
    ├── voicenote/       Step 4
    ├── transcription/   Step 5
    ├── collaboration/   Step 6
    ├── share/           Step 7
    ├── notification/    Step 8
    └── sync/            Step 9
```

Each feature module follows the same internal shape:

```
feature/<name>/
├── config/       @Component @ConfigurationProperties classes
├── dto/request/  inbound, validated, bound from JSON
├── dto/response/ outbound records
├── enums/
├── event/        domain events published by this module
├── mapper/       hand-written, @Component (no MapStruct)
├── model/        JPA entities
├── repository/
├── service/      interface
├── service/impl/ implementation
└── web/          controllers
```

---

## 3. Conventions that hold everywhere

These are the rules the whole codebase obeys. Breaking one should be a deliberate, documented decision.

### 3.1 Ownership is enforced by the query, never by an `if`

Entities store a plain `Long ownerId`, **not** a `@ManyToOne User`. Every repository finder carries the owner:

```java
Optional<Note> findByIdAndOwnerIdAndDeletedFalse(Long id, Long ownerId);
```

A caller who isn't entitled gets an empty `Optional` → 404. Authorization is structurally impossible to forget, because there is no unscoped path to the row.

Plain `Long` ids also mean **no foreign keys between modules**, which is what makes Modulith boundaries real — and what later made the system-auditor sentinel possible (§3.7).

### 3.2 404 protects existence; 403 denies an action

- **404** — "as far as you're concerned this doesn't exist." The answer for anyone with no relationship to the resource. It must not confirm the row is real, or status codes become an id-enumeration oracle.
- **403** — "this exists, you can see it, but you may not do that." Only safe once the caller is already inside the door.

The only domain 403 in the application is `InsufficientNotePermissionException` — a READ collaborator attempting a write. They can already open the note; hiding it would be incoherent.

This reasoning also governs **control flow**, not just status codes. In `shareNote`, the ownership check runs *before* the collaborator check — otherwise probing a stranger's note id would return 400 with a non-collaborator and 404 with a real one, and the difference confirms which ids exist.

### 3.3 Delete by entity, never by id

```java
Note note = noteRepository.findByIdAndOwnerId(noteId, ownerId).orElseThrow(...);
noteRepository.delete(note);          // never deleteById(noteId)
```

`deleteById` drops the ownership predicate entirely.

### 3.4 Cross-module calls go through public services

The share module calls `NoteService`, never `NoteRepository`. Where a module needs data another module's DTO doesn't expose, a small **carrier type** is added to the owning module rather than either breaking the boundary or widening a public DTO — `NoteChange` (§8) and `VoiceNoteContent` are both this.

### 3.5 Enums are always `@Enumerated(STRING)`

Reordering constants must never reinterpret stored rows. **Adding a constant is safe; removing one is not** — historical rows still reference it and Hibernate will fail to read them. Retire a value by ceasing to create it.

### 3.6 Response DTOs are nested, not flattened

`SharedNoteSummaryResponse` embeds `NoteSummaryResponse`; `SyncNoteResponse` embeds `NoteResponse`. The owning module keeps sole control of that entity's wire shape, so a field added there appears everywhere automatically instead of being forgotten in a parallel copy.

### 3.7 Scalars are wrapped

`UnreadCountResponse(long unreadCount)` rather than a bare `7`. A top-level JSON scalar can never grow a second field without breaking every client at once.

### 3.8 N+1 is avoided by batch-and-map

Load a page's related rows in **one** query and return a `Map` keyed by id. Never zip positionally — `IN` order isn't guaranteed and missing ids shift everything. **Mappers never touch repositories.**

### 3.9 Configuration properties use `@Component`

```java
@Getter @Setter @Component
@ConfigurationProperties(prefix = "notare.voice")
public class VoiceNoteProperties { ... }
```

Not `@EnableConfigurationProperties`. Mixing both registers the bean twice and fails startup with "required a single bean, but 2 were found" (this happened — see §12.3).

`grep -rn "EnableConfigurationProperties" src/` should return nothing.

### 3.10 Security is deny-by-default

`WebSecurityConfig` ends with `anyRequest().authenticated()` after an explicit allowlist, so **new endpoints are protected without touching security config**.

Public routes: `POST /login/**`, `POST /auth/refresh`, `POST /otp/**`, `POST /user/register`, `POST /password/**`, `/public/**`, `/error`, and the springdoc paths. A `maliciousProbesMatcher()` denies requests ending in `.php`, `.asp`, `.env`, `.cgi`, `.sh`, `.bat`.

`/error` **must** stay permitted. ERROR dispatches re-enter the filter chain; without it, every unhandled 404/500 comes back as a 403 that masks the real failure (see §12.4).

---

## 4. Error model

RFC 9457 `ProblemDetail`, produced by a single `@RestControllerAdvice` (`GlobalExceptionHandler`). Custom exceptions extend `RuntimeException(String)` and live in `common/exception`.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "This note has been modified since you loaded it.",
  "instance": "/api/v1/notes/1",
  "code": "STALE_VERSION",
  "currentVersion": 6
}
```

Extension members (`currentVersion`) sit at the **root**, per RFC 9457 — not nested under a `properties` object. That only works because Boot's auto-configured `ObjectMapper` registers `ProblemDetailJacksonMixin`. Declaring your own `@Bean ObjectMapper` silently breaks it (see §12.5).

### Status code map

| Status | Meaning here | Example exceptions |
|---|---|---|
| 400 | Malformed or structurally invalid request | `InvalidCollaborationException`, `InvalidNoteShareException`, `InvalidVoiceUploadException` |
| 403 | Authenticated, permitted to see it, not permitted to do it | `InsufficientNotePermissionException` |
| 404 | Doesn't exist, or isn't yours (indistinguishable on purpose) | `NoteNotFoundException`, `NoteShareNotFoundException`, `CollaborationNotFoundException`, `NotificationNotFoundException`, `VoiceNoteNotFoundException`, `TranscriptionNotFoundException` |
| 409 | Conflict with current state | `NoteConflictException` (carries `currentVersion`), `CollaborationAlreadyExistsException`, `NoteAlreadySharedException` |
| 413 | Payload too large | `VoiceFileTooLargeException` |
| 415 | Unsupported media type | `UnsupportedAudioFormatException` |

---

## 5. Notes — Steps 1, 2, 3

### Entity

`Note`: `ownerId`, `clientId`, `title` VARCHAR(255), `content` TEXT, `pinned`, `archived`, `deleted`, `deletedAt`, `@Version version`, `createdAt`, `updatedAt`, `lastModifiedBy`.

**Manual soft delete** — `deleted` + `deletedAt` booleans rather than Hibernate's `@SoftDelete`. The reason at the time was that restore and permanent-delete need to *find* trashed rows, which `@SoftDelete` hides globally. It paid off again in Step 9: because a trashed note is still a row, delta sync gets **tombstones for free**.

`pinned`, `archived` and `deleted` are three orthogonal booleans, not one status enum — a note can be pinned *and* archived.

Indexes: `(owner_id)`, `(owner_id, deleted)`, `(owner_id, updated_at)`. Unique: `(owner_id, client_id)`.

### Optimistic locking — two layers

**Layer 1 — explicit version check.** `requireMatchingVersion` compares the client's version to the row's, and throws `NoteConflictException` carrying `currentVersion`. Catches the common case (client editing from stale state) with a clean 409, body code `STALE_VERSION`. Applies to legacy-mode notes only: a collaborative note's text is refused earlier with 409 `BODY_IS_COLLABORATIVE`. PUT and PATCH without a `version` are a 400. The sync path sends no version and relies on its operation ledger.

**Layer 2 — Hibernate `@Version`.** `saveAndFlush` forces the UPDATE immediately; a genuinely concurrent write that passed layer 1 fails Hibernate's own check → `ObjectOptimisticLockingFailureException` → 409.

> **The version is a token you echo back, not a counter you increment.** The comparison is `.equals()`. Sending `version + 1` conflicts exactly as sending a stale one does. The reason equality is required: if the server accepted anything ≥ current, a client could send `999999` and bypass every check — last-write-wins with extra steps.

**Auto-save (PATCH) vs full update (PUT).**

| | PUT `/notes/{id}` | PATCH `/notes/{id}` |
|---|---|---|
| Semantics | full replacement | partial |
| `null` field | clears it | leaves it unchanged |
| Clearing a field | send `null` | send `""` |
| DTO | `UpdateNoteRequest` | `AutoSaveNoteRequest` |

Auto-save is **idempotent for free**: if the applied values equal what's stored, Hibernate's dirty-checking issues no UPDATE, so `@Version` doesn't increment and re-sending returns the same version.

### Search

```java
@Query("""
    SELECT n FROM Note n
    WHERE n.ownerId = :ownerId AND n.deleted = false
      AND (LOWER(n.title) LIKE LOWER(CONCAT('%', :term, '%'))
        OR LOWER(n.content) LIKE LOWER(CONCAT('%', :term, '%')))
    """)
```

Written as explicit JPQL because a **derived method name cannot express that grouping** — it would bind the AND/OR wrong and apply the owner filter to only one side of the OR. The parentheses are the whole point.

`/notes/search` sits before `/{noteId}` in routing terms — Spring matches literal segments ahead of templates, so there's no ambiguity.

---

## 6. Voice notes and file storage — Step 4

`VoiceNote`: `ownerId`, optional `noteId` link, `contentType`, immutable `storageKey`, `sizeBytes`, `durationSeconds`. **Hard delete** (no soft delete, no `@Version`) — a recording is either there or it isn't, and a trashed binary blob helps nobody.

`FileStorageService` is a Strategy interface with three implementations:

| Implementation | Active when | Stores |
|---|---|---|
| `LocalFileStorageService` | `@Profile("!prod")` | `notare.storage.local.location` on disk |
| `R2FileStorageService` | `prod` and `notare.storage.backend=r2` (the default if unset) | Cloudflare R2, S3-compatible |
| `PostgresFileStorageService` | `prod` and `notare.storage.backend=postgres` | `file_blobs` table in the same Postgres database (temporary, until R2 exists) |

In prod, `STORAGE_BACKEND` picks the backend. R2 reads `R2_*` variables first, then the `AWS_*` variables Neon's console hands out. If `r2` is selected without its settings, `R2FileStorageService` fails fast at startup.

### The three lessons here

**Upload is deliberately NOT `@Transactional`.** Never hold a database connection open across file I/O — under load the pool drains while threads wait on disk or network.

**The upload is a saga, not a transaction:**

```
validate → store file → try { save row } catch { delete file; rethrow }
```

The compensating delete is what keeps storage and database consistent without a distributed transaction.

**Delete is the reverse order:** remove the row first, then the file best-effort. A missing row with an orphan file is recoverable housekeeping; a row pointing at a missing file is a broken download.

### Streaming

`GET /{id}/stream` returns `ResourceRegion` with HTTP Range support (206 Partial Content), so audio scrubbing works. Served **through the controller**, not as a static resource, so the ownership check still applies.

`spring.servlet.multipart.max-file-size=25MB` — Spring's default is 1MB, which rejects almost any audio. `VoiceUploadValidator` enforces a stricter, friendlier limit (20MB) below it and returns 415/413/400 instead of a raw container error.

### MIME allowlist

`.m4a` is an MP4 container with AAC audio, and clients disagree about naming it: macOS/curl say `audio/x-m4a`, Chrome on Windows says `audio/mp4`, some say `audio/m4a`. All are now allowlisted.

> The declared content type is **client-supplied and spoofable**. Allowlisting it produces both false negatives (valid files rejected) and a real spoofing hole. The durable fix is magic-byte sniffing — MP4/M4A carry `ftyp` at bytes 4–7, WAV starts `RIFF....WAVE`, OGG starts `OggS`. **Not built.** See §13.

---

## 7. Transcription — Step 5

`Transcription` is 1:1 with a voice note (`UNIQUE voice_note_id`); re-transcribing updates the existing row. Status: `PENDING → PROCESSING → COMPLETED | FAILED`.

`TranscriptionEngine` is a Strategy; `StubTranscriptionEngine` (`@Profile("!prod")`) simulates a 1.5s delay so `PROCESSING` is observable.

### Async design

```
POST /voice-notes/{id}/transcription
  → TranscriptionServiceImpl.submit()  [@Transactional]
      publishes TranscriptionRequestedEvent
  → 202 Accepted + Location: poll URL

  (transaction commits)

  → TranscriptionWorker  [@Async + @TransactionalEventListener(AFTER_COMMIT)]
      short tx: set PROCESSING
      NO tx:    engine.transcribe()      ← the long call
      short tx: set COMPLETED / FAILED
```

**`submit` is idempotent.** `PENDING`/`PROCESSING`/`COMPLETED` return as-is; `FAILED` resets to `PENDING` and republishes.

**Who may ask (step 17d, B-11).** `POST` is for the note's owner and anyone with WRITE on it; a Viewer gets 403, and anyone with no access gets 404. `GET` is open to Viewers too. The job belongs to the clip's owner (`owner_id`), so the audio is found; `requested_by` is who asked, and the "ready" or "failed" notice goes to them. `requested_language` defaults to `auto`. `StuckTranscriptionRecovery` re-queues stale jobs for every owner and the worker never uses the asker to find the audio, so shared clips are covered.

**`AsyncConfig` defines a dedicated bounded executor** (`transcriptionExecutor`, core 2 / max 4 / queue 50) rather than using Spring's default. That's bulkhead isolation — transcription can saturate its own pool without starving request handling, and the bounded queue provides backpressure instead of unbounded thread creation.

**`TranscriptionWorker` is a separate bean, and it has to be.** `@Async` works through a Spring AOP proxy; a call from one method to another *inside the same bean* bypasses the proxy entirely and the annotation is silently ignored — the "async" work would run synchronously on the request thread.

Transcript search is a **separate endpoint** (`/transcriptions/search`) rather than merged into note search. A unified cross-entity ranked search across two tables with two response shapes needs a real search index (Elasticsearch/OpenSearch). Deferred, not faked.

---

## 8. Collaborations — Step 6

`Collaboration` models the **relationship**, not a person: `ownerId`, `collaboratorId`, `status`, `UNIQUE(owner_id, collaborator_id)`, `lastModifiedBy`.

### State machine

| From | Action | To | Who may act |
|---|---|---|---|
| `PENDING` | accept | `ACCEPTED` | invitee only |
| `PENDING` | reject | `REJECTED` | invitee only |
| any | cancel (DELETE) | *row removed* | owner only |

- Same-status transition → idempotent no-op, 200.
- Opposite terminal state (accept a rejected invite) → 400.
- **"Invitee only" is enforced by the query** (`findByIdAndCollaboratorId`), so a mismatch is 404, not 403.

### API shape decisions

**`/collaborations` is top-level.** A collaboration belongs to neither party, so `/users/{id}/collaborations` would force an arbitrary choice of owner. `/sent` and `/received` express both ends.

**`/accept` and `/reject` are POST action sub-paths, not `PATCH {status}`.** A general-purpose write to `status` hands clients the ability to set any value and moves the state machine into the request body.

**Reject and cancel are deliberately different.** The invitee's "no" leaves an auditable `REJECTED` row; the owner's withdrawal deletes it.

**`/sent` and `/received` are two literal paths, not `?direction=`.** They're two queries against two different indexed columns.

### User search (added to the existing user module)

`GET /user/search?q=` matches `username`, `email`, and `CONCAT(firstName,' ',lastName)`, excludes self. **A blank term returns an empty page, not the whole directory** — the opposite of note search, because this is other people's data. `UserSummaryResponse` exposes no email.

> Partial email matching is retained; mild enumeration accepted as a trade-off for usability.

---

## 9. Shared notes and permissions — Step 7

### The design choice

Until Step 7, ownership scoping *was* the authorization model. Sharing breaks it: a note becomes reachable by a non-owner. Two options:

- **(a) Separate collection** — `/notes/**` stays strictly owner-only and unchanged; a new `/shared-notes` collection handles "shared with me". Share module → note module, no cycle. **Chosen.**
- **(b) Unified `/notes/{id}`** for owners and collaborators — nicer for clients, but requires dependency inversion and rewrites every note access path. Rejected.

Cost of (a): one note has two URLs and the client must know which collection it's looking at. Google Docs makes the same trade with "Shared with me".

### `NoteShare`

`noteId`, `ownerId`, `sharedWithUserId` — **all `updatable = false`**. On an ordinary entity that's tidiness; on a grant row it's a security property, since re-pointing a grant is a silent privilege change. `permission` is the only mutable field. `UNIQUE(note_id, shared_with_user_id)`.

`ownerId` is **denormalized** onto the grant. Safe specifically because `Note.ownerId` is itself immutable — denormalization is only dangerous when the source can change.

### `SharePermission`

```java
public enum SharePermission {
    READ, WRITE;
    public boolean satisfies(SharePermission required) {
        return this.ordinal() >= required.ordinal();
    }
}
```

"WRITE implies READ" lives in **one method**. Constants are ordered weakest-first because `satisfies` ranks by `ordinal()` — the only use of ordinal, since the column is `@Enumerated(STRING)`.

**WRITE = edit title and content only.** Not delete, not pin, not archive. Pin and archive are the owner's private view state; delete is irreversible from a collaborator's side. Widening later is easy; clawing back is not.

### Two services, deliberately

`NoteShareService` (owner side) asks *"is this your note?"*. `SharedNoteService` (recipient side) asks *"do you hold a grant, and is it strong enough?"*. Same rows, different question — two authorization models in one class is how the wrong one eventually gets applied.

`requireGrant` is the single gate: no grant → 404 with the message `"Note not found."` (must not hint), grant too weak → 403.

### The pre-authorized accessors — a known sharp edge

`NoteService` exposes three methods that take **no user id and check nothing**:

```java
NoteResponse getAuthorizedNote(Long noteId);
NoteResponse updateAuthorizedNote(Long noteId, UpdateNoteRequest request);
Map<Long, NoteSummaryResponse> getAuthorizedNoteSummaries(Collection<Long> noteIds);
```

"Authorized" is a claim the **caller** makes, not something these verify. They are the hole option (a) punches in the ownership invariant, and what holds it shut is naming and discipline — not the compiler.

**`SharedNoteServiceImpl` is the only class permitted to call them.** Worth grepping for occasionally. If it ever gets loose, the real fix is extracting a narrow `NoteAccessPort` interface that only the share module depends on.

`updateAuthorizedNote` is character-for-character identical to `updateNote` below the lookup, on purpose — both writers must pass the same version gate or Step 2's optimistic locking silently breaks now that a two-writer race is finally possible.

### Cleanup (7F)

`CollaborationRemovedEvent` and `NotePermanentlyDeletedEvent` are consumed **`BEFORE_COMMIT`** so grants die atomically with their cause. If cleanup ran after commit and failed, the cancel would be durable while grants survived — a removed collaborator retaining live access, silently.

Soft delete deliberately does **not** publish: a trashed note can be restored and its collaborators should still be there, and shared-note queries already filter `deleted = false`.

> ⚠️ **This cleanup is currently broken.** See §12.1.

---

## 10. Notifications — Step 8

### The governing idea

**A notification is a historical record, not a live view.** Everything follows from that:

- `targetLabel` is **snapshotted** — joining to the note at read time would rewrite history on rename ("Femi shared 'Q3 Plan'" silently becomes "'Q3 Plan (final v2)'") and leave a hole after deletion.
- Nearly every column is `updatable = false`.
- **Nothing cleans these rows up** when their subject disappears — the exact inverse of 7F. Grants conferred access; a notification confers nothing.

### Entity

`recipientId`, `actorId` (**nullable** — a finished transcription has no human actor, and "You transcribed your note" would be a lie the UI must special-case), `type`, `targetType`, `targetId`, `targetLabel`, `readAt`, `createdAt`.

`readAt` is the only mutable field, and **unread is `readAt IS NULL`**. A timestamp beats a boolean: strictly more information for the same storage, and `(isRead, readAt)` is a redundancy that eventually disagrees with itself.

No unique constraint — being shared the same note twice is two real events.

### Types are named for the event, not the sentence

`NOTE_SHARED`, not `SOMEONE_SHARED_A_NOTE_WITH_YOU`. The server sends **facts** (type, actor, target, label) and the client composes the wording. Phrasing and translation never freeze into historical rows.

Cost: the client must handle an unfamiliar `NotificationType` gracefully with a generic fallback, or adding a type server-side breaks old clients.

Values: `COLLABORATION_INVITED`, `COLLABORATION_ACCEPTED`, `COLLABORATION_REJECTED`, `NOTE_SHARED`, `NOTE_SHARE_PERMISSION_CHANGED`, `NOTE_SHARE_REVOKED`, `TRANSCRIPTION_COMPLETED`, `TRANSCRIPTION_FAILED`.

### Delivery

```
domain event → listener → NotificationDispatcher → List<NotificationChannel>
                                                     └── InAppNotificationChannel → NotificationService.create()
```

Persistence is treated as **just another channel**, not the "real" behaviour with delivery bolted on. Adding email or push is one `@Component` implementing `NotificationChannel` — no edit to the dispatcher or any listener.

**Each channel is wrapped in its own try/catch.** Without it, an email provider timing out aborts the loop and silently kills the unrelated in-app notification. Exceptions are logged and swallowed — defensible *only* because of the log line.

`NotificationService.create` is `@Transactional(REQUIRES_NEW)` so a failed notification insert can never mark a caller's transaction rollback-only. An invitation without its notification is an annoyance; an invitation *rolled back* by a notification failure is a broken feature.

**No `POST /notifications` exists and none ever should.** An endpoint letting one user create a notification for another is a spam vector wearing a feature's name.

`markAllAsRead` is the project's only bulk `UPDATE`. It bypasses the persistence context and skips entity callbacks — which is why the timestamp is generated in the service and passed in, and why every row in one sweep carries the identical instant.

---

## 11. Offline sync — Step 9

Two halves: **pull** (delta feed) and **push** (batch replay of a queued offline edit log).

### Cursor design

`GET /sync?since=<ISO timestamp>` with a deliberate **overlap window** (`notare.sync.overlap-seconds`, default 5).

A transaction that began before a cursor was issued but commits after it would otherwise fall into the gap between two windows and be **lost forever with no signal**. Overlap converts silent data loss into occasionally re-sending rows the client discards — being wrong in the recoverable direction.

Three details that are each a bug if reversed:

1. The clock snapshot is taken **before** the query. Taken after, a note written during paging carries an `updatedAt` earlier than the returned cursor and is never requested again.
2. The sort is `(updatedAt, id)` and is **rebuilt inside `NoteServiceImpl`, discarding the caller's**. Paging sorted only by `updatedAt` is undefined whenever timestamps tie — and they tie constantly, since MySQL `DATETIME` is second-precision unless you ask for fractional. A tied sort can show one row twice and skip another entirely.
3. While `hasMore` is true, `nextCursor` is the **same `since` the client sent**, and paging is by page number within a fixed window. The tempting "return the last row's `updatedAt`" design **infinite-loops** when a whole page shares one timestamp — a bulk import does exactly that, because the comparison is `>=`.

> **The cursor is always the server's clock.** A device running five minutes fast would skip five minutes of changes permanently.

### Batch push

**Not one transaction.** Each operation runs in its own, via `SyncOperationApplier` — a **separate bean**, because `@Transactional` works through an AOP proxy and a same-bean call bypasses it silently. If that happened here, a conflict on operation 7 would roll back the six that succeeded: exactly what Step 9 exists to prevent.

`SyncPushServiceImpl` has **no `@Transactional` anywhere**, and that absence is load-bearing.

Per-operation results, HTTP 200 for the batch even when every operation conflicted:

| `SyncResultStatus` | Meaning | What the client does |
|---|---|---|
| `APPLIED` | Worked, or had already worked (replay) | drop from queue |
| `CONFLICT` | Someone wrote first; the **server's current note** is in the payload | keep local copy, show a diff |
| `NOT_FOUND` | Note gone from another device | drop silently |
| `INVALID` | Malformed; retry will never help | bug report |

Returning the **full note** on conflict rather than a bare version is the difference between a usable offline app and a frustrating one: with only a version, twenty conflicts means twenty follow-up requests before the user sees anything.

**Unexpected exceptions deliberately propagate** and fail the whole batch with a 500. There is no "retry later" status, so classifying a transient database error as `INVALID` would tell the client to give up and *discard the user's edit*. Losing data silently is worse than failing loudly, and the ledger makes retrying safe.

### Idempotency — two mechanisms

**`Note.clientId`** makes CREATE idempotent. An offline-created note has no server id; the client generates a UUID and sends it. Nullable (online notes have none), which is why uniqueness is the composite `(owner_id, client_id)` — both MySQL and PostgreSQL permit many NULLs in a unique index.

**`SyncOperation`** (the ledger) makes UPDATE idempotent. Without it: the client sends "set note 7 from version 4", the server applies it and bumps to 5, the response is lost, the client retries — and version 4 is now stale, so the server reports a **conflict against the user's own successful write**. That phantom conflict is worse than a duplicate, because the user can't tell it from a real one.

**Only successes are recorded.** A conflicted or failed operation must stay retriable; recording failures would make the first failure permanent.

The ledger check, the note change, and the ledger write all happen in **one transaction** — a separately-committing ledger could disagree with the note change.

### Tombstones

Soft delete gives tombstones free (the row still exists). **Permanent delete leaves nothing**, and "the note stopped appearing in the feed" is indistinguishable from "nothing changed" — so the ghost note stays cached forever.

`NoteTombstone` closes that. **Principle: absence is not a synchronizable fact.** Anything a client must be told about has to leave a row behind.

Tombstones are unpaginated and sent only on incremental syncs. A first-sync client has nothing to evict, and paginating would mean merging two independently paged sources — hard to get right, impossible to page consistently.

### Clips and transcripts (step 17c)

`GET /sync` also returns `clips[]`: id, noteId, clientId, durationSeconds, sizeBytes, mimeType, `deleted`, and `transcript` (`status`, `text`, `language`; null when the clip has none). It is paged with the notes — same page number and size, `hasMore` true while either list has more — so the cursor rules above are unchanged.

- **What moves a clip's `updatedAt`:** the upload, a delete, a trash or restore of its note (the native updates set it explicitly), and every transcription status change (`touchClip`). One marker, one query: a transcript finishing on device A reaches device B on its next pull.
- **`deleted` means hidden**, for either reason: the note is in the trash (the clip comes back with the note), or the person deleted the clip alone (`removed`, which never comes back; its file is removed at once). A hard-deleted clip leaves with its note's tombstone.
- **Batch:** `CLIP_DELETE` with `clipId`. No ledger entry: deleting an already-deleted clip is `NOT_FOUND`, which the client treats as done.
- **Scope:** the caller's own clips (`owner_id`). A clip an Editor adds to someone else's note belongs to the Editor and reaches the note's owner through the shared-note path, not this feed. Revisit if the owner's devices must receive Editors' clips.

### Trash purge (step 17e)

`TrashPurgeJob` (`feature/note/job`, 03:30 daily) deletes notes whose `purge_at` has passed, one transaction per note, publishing `NotePermanentlyDeletedEvent` so shares, clips, transcripts, stored files, collaboration data and the sync tombstone go with it. `purge_at` is set on soft delete and cleared on restore. It is returned as `purgeAt` in `TrashedNoteResponse` and, for trashed notes, in the sync pull's `trashed[]`. ShedLock (`shedlock` table, `SchedulerLockConfig`) lets only one instance run `TrashPurgeJob` and `SyncRetentionJob`.

### Retention

`SyncRetentionJob` (`@Scheduled(cron = "0 15 3 * * *")`) sweeps both tables in one transaction, using `notare.sync.retention-days` (default 30).

**The cutoff is a correctness setting, not a disk-space one.** Too short: swept ledger rows cause phantom conflicts, swept tombstones cause ghost notes. Too long: unbounded growth. Both failures are silent and look like bugs elsewhere.

> ⚠️ `@Scheduled` runs on **every instance**. This job is idempotent so concurrent runs are harmless, but that's a property of *this* job. The next scheduled job needs ShedLock or a designated scheduler node.

### What sync deliberately excludes

- **Shared notes** stay online-only. Offline collaborator edits multiply the conflict surface, and recipients don't have auto-save anyway.
- **Audio bytes** stay out. A JSON batch cannot carry audio, so a clip is created by the multipart upload (`POST /notes/{noteId}/voice-notes`, idempotent on the clip's client id). Everything else about clips syncs (see *Clips and transcripts* above).
- **CRDT/OT auto-merge** is out of scope — a different architecture, not a feature.

---

## 12. Auditing — Step 10

`AuditorAware<Long>`, not `<String>`. Usernames are **mutable**; a rename would silently rewrite every historical audit row. Ids don't rot.

### The system auditor

Three contexts write with no authenticated principal: the transcription worker (`@Async` — `SecurityContextHolder` is thread-local and *not* inherited), the retention job (scheduler thread), and registration (the User row predates authentication).

`SystemAuditor.ID = 0L` is recorded instead of null. Null would mean **both** "the system did this" *and* "auditing is misconfigured or this row predates it", and no query could tell them apart.

This is only possible because audit columns are plain `Long`s with **no foreign key to `users`** — the Step 1 decoupling decision paying off a third time. `0` can't collide because IDENTITY sequences start at 1.

> `0` must never reach a user-facing display. Clients must special-case it rather than rendering "user 0".

**The security context is deliberately not propagated into the async worker.** The transcription worker genuinely *is* acting as the system; recording the submitter would misattribute a machine action to a person.

### The trap in `SecurityAuditorAware`

Spring Security does **not** leave `authentication` null on an unauthenticated request — it installs an `AnonymousAuthenticationToken`, and that token reports `isAuthenticated() == true`. So the intuitive check passes for anonymous callers and you end up reading a user id off the string `"anonymousUser"`:

```java
// WRONG
if (auth != null && auth.isAuthenticated()) { ... }

// RIGHT
if (auth != null && auth.isAuthenticated()
        && !(auth instanceof AnonymousAuthenticationToken)) { ... }
```

The principal is resolved with an `instanceof` **pattern match, not a cast** — a `ClassCastException` here would be thrown during a JPA flush, surfacing far from its cause.

`auditorAwareRef = "securityAuditorAware"` is matched by **bean name**. Renaming the class without updating that string compiles cleanly and fails at startup.

### Which entities got audit columns — and why so few

The instinct is to bolt audit columns onto everything. The test applied instead: *what does this column tell me that I couldn't already work out?*

| Entity | `lastModifiedBy` | Reasoning |
|---|---|---|
| `Note` | ✅ | The one genuinely non-derivable audit column. Before Step 7 the answer was always `ownerId`; **sharing made the question real** — a WRITE collaborator edits a note they don't own and nothing else records it. |
| `Collaboration` | ✅ | Two writers: owner creates, invitee accepts/rejects. Currently *derivable* (`status != PENDING` implies the invitee acted) but recorded anyway, because that inference breaks the day the state machine gains a transition. |
| `NoteShare` | ❌ | Only the owner can ever create or modify a grant. Both fields would permanently equal `ownerId`. |
| `createdBy` anywhere | ❌ | Notes and collaborations are only ever created by their owner. **A column that can never disagree with another column isn't an audit trail — it's duplication that looks like rigour.** |

Both columns are nullable and have to be: rows written before Step 10 have no value, and `ddl-auto` cannot add a `NOT NULL` column to a populated table. Null unambiguously means "written before auditing recorded who" — which only works because `getCurrentAuditor()` never returns `Optional.empty()`.

`lastModifiedBy` is surfaced on `NoteResponse` (detail) only, **not** `NoteSummaryResponse` — in an owner's own list it's themselves on nearly every row, a column of noise paid for on every page load. It reaches the shared-note endpoints for free because those return the same record.

---

## 13. Complete endpoint reference

All paths are prefixed `/api/v1`. Everything requires a Bearer token except where marked **public**.

### Auth / account (pre-existing)

| Method | Path | Notes |
|---|---|---|
| POST | `/user/register` | **public** |
| GET | `/user/me` | current user profile |
| GET | `/user/lookup?ids=1,2,3` | resolves ids to public profiles; bare list, not paged; also accepts `ids=1&ids=2` |
| GET | `/user/search?q=&page=&size=` | blank `q` → empty page |
| POST | `/login/login` | **public** |
| POST | `/auth/refresh` | **public** |
| POST | `/activation/resend` | **public**; always answers 200 with a generic message |
| POST | `/password/forgot-password` | **public** |
| POST | `/password/verify-reset-otp` | **public** |
| POST | `/password/reset-password` | **public** |
| POST | `/logout/logout` | |
| POST | `/logout/logout-all` | |
| GET | `/session/sessions` | one entry per signed-in device (step 17h) |
| DELETE | `/session/sessions/{sessionId}` | signs out that device only; 404 if it is not the caller's (step 17h) |

### Operator and internal

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/admin/database/wipe` | 200 | header `X-Admin-Secret`; 401 if wrong; 502 if the internal-auth tenant wipe fails (retry is safe) |
| POST | `/internal/user-events` | 200 | header `X-Internal-Secret`, called by internal-auth-server; body `internalAuthUserId`, `event` |
| GET | `/keep-alive` | 200 | **public** |

### Notes

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/notes` | 201 + `Location` | optional `clientId` (1 to 64 chars): posting the same one again returns the existing note with **200** |
| GET | `/notes/{noteId}` | 200 | |
| GET | `/notes?page=&size=&sort=` | 200 | default sort `pinned,updatedAt` DESC |
| GET | `/notes/search?q=&page=&size=` | 200 | blank `q` matches all |
| PUT | `/notes/{noteId}` | 200 | full replace; requires `version` |
| PATCH | `/notes/{noteId}` | 200 | auto-save; partial; requires `version` |
| DELETE | `/notes/{noteId}` | 204 | soft delete |
| GET | `/notes/trash?page=&size=` | 200 | default sort `deletedAt` DESC |
| POST | `/notes/{noteId}/restore` | 200 | |
| POST | `/notes/{noteId}/pin` | 200 | owner only |
| DELETE | `/notes/{noteId}/pin` | 200 | returns the note |
| POST | `/notes/{noteId}/archive` | 200 | owner only |
| DELETE | `/notes/{noteId}/archive` | 200 | returns the note |
| DELETE | `/notes/{noteId}/permanent` | 204 | irreversible |

### Voice notes

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/voice-notes?noteId=&durationSeconds=` | 201 | `multipart/form-data`, field `file` |
| GET | `/voice-notes/{voiceNoteId}` | 200 | metadata |
| GET | `/voice-notes?page=&size=` | 200 | |
| GET | `/voice-notes/by-note/{noteId}` | 200 | list, unpaged |
| GET | `/voice-notes/{voiceNoteId}/download` | 200 | full file |
| GET | `/voice-notes/{voiceNoteId}/stream` | 200 / 206 | HTTP Range |
| DELETE | `/voice-notes/{voiceNoteId}` | 204 | hard delete |

### Transcription

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/voice-notes/{voiceNoteId}/transcription` | 202 + `Location` | idempotent |
| GET | `/voice-notes/{voiceNoteId}/transcription` | 200 | poll for status |
| GET | `/transcriptions/search?q=&page=&size=` | 200 | |

### Collaborations

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/collaborations` | 201 + `Location` | body: `collaboratorId` |
| POST | `/collaborations/{id}/accept` | 200 | invitee only |
| POST | `/collaborations/{id}/reject` | 200 | invitee only |
| DELETE | `/collaborations/{id}` | 204 | owner only |
| GET | `/collaborations/sent?page=&size=` | 200 | |
| GET | `/collaborations/received?page=&size=` | 200 | |

### Sharing a voice clip — owner side

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/voice-notes/{voiceNoteId}/shares` | 201 + `Location` | body: `ShareVoiceNoteRequest` |
| GET | `/voice-notes/{voiceNoteId}/shares?page=&size=` | 200 | |
| PUT | `/voice-notes/{voiceNoteId}/shares/{userId}` | 200 | body: permission |
| DELETE | `/voice-notes/{voiceNoteId}/shares/{userId}` | 204 | |

### Sharing — owner side

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/notes/{noteId}/shares` | 201 + `Location` | body: `sharedWithUserId`, `permission` |
| GET | `/notes/{noteId}/shares?page=&size=` | 200 | |
| PUT | `/notes/{noteId}/shares/{userId}` | 200 | body: `permission` |
| DELETE | `/notes/{noteId}/shares/{userId}` | 204 | |

### Sharing — recipient side

| Method | Path | Success | Notes |
|---|---|---|---|
| GET | `/shared-notes?page=&size=` | 200 | sorted by when shared |
| GET | `/shared-notes/{noteId}` | 200 | requires READ |
| PUT | `/shared-notes/{noteId}` | 200 | requires WRITE; 403 if READ only |
| PATCH | `/shared-notes/{noteId}` | 200 | auto-save; partial; requires WRITE and `version` |
| GET | `/shared-notes/{noteId}/voice-notes` | 200 | clips attached to the shared note |
| POST | `/shared-notes/{noteId}/voice-notes?durationSeconds=` | 201 + `Location` | `multipart/form-data`, field `file` |
| GET | `/shared-notes/{noteId}/voice-notes/{voiceNoteId}/download` | 200 | |
| GET | `/shared-notes/{noteId}/voice-notes/{voiceNoteId}/stream` | 200 / 206 | HTTP Range |

### Sharing a voice clip — recipient side

| Method | Path | Success | Notes |
|---|---|---|---|
| GET | `/shared-voice-notes?page=&size=` | 200 | |
| GET | `/shared-voice-notes/{voiceNoteId}` | 200 | metadata |
| GET | `/shared-voice-notes/{voiceNoteId}/download` | 200 | |
| GET | `/shared-voice-notes/{voiceNoteId}/stream` | 200 / 206 | HTTP Range |

### Notifications

| Method | Path | Success | Notes |
|---|---|---|---|
| GET | `/notifications?unreadOnly=&page=&size=` | 200 | |
| GET | `/notifications/unread-count` | 200 | |
| POST | `/notifications/{id}/read` | 200 | idempotent |
| POST | `/notifications/read-all` | 200 | returns `markedCount` |
| DELETE | `/notifications/{id}` | 204 | real delete |

### Sync

| Method | Path | Success | Notes |
|---|---|---|---|
| GET | `/sync?since=&page=&size=` | 200 | omit `since` for first sync; no `sort` param by design |
| POST | `/sync/batch` | 200 | max 200 operations; per-operation results |

---

## 14. Known bugs

### 14.1 🔴 Grant cleanup on permanent delete never fires

**There are two classes named `NotePermanentlyDeletedEvent`:**

| Class | Signature | Published by | Listened to by |
|---|---|---|---|
| `feature.note.event.NotePermanentlyDeletedEvent` | `(Long noteId, Long ownerId)` | `NoteServiceImpl` ✅ | `NoteTombstoneListener` ✅ |
| `feature.share.event.NotePermanentlyDeletedEvent` | `(Long noteId)` | *nothing* | `NoteShareCleanupListener` ❌ |

`NoteShareCleanupListener` lives in `feature.share.event`, so the unqualified type name in its handler resolves to the **same-package copy** — the old 7F version that nothing publishes. **The listener never fires.**

**Consequence:** permanently deleting a note leaves its `note_shares` rows orphaned. Since ids can be reused after a restore or table reset, an orphan grant is a latent access bug, not just untidy data.

**Fix:** delete `feature/share/event/NotePermanentlyDeletedEvent.java` and add `import com.notare.feature.note.event.NotePermanentlyDeletedEvent;` to `NoteShareCleanupListener`. Then verify with a test that permanently deleting a shared note removes the grant.

This is the failure mode I warned about in Step 8 in a different form: same-package resolution wins over intent, silently, with no compiler complaint.

### 14.2 🟡 Module cycle — `CollaborationRemovedEvent` is in the wrong module

`CollaborationRemovedEvent` lives in `feature.share.event` but is **published by `CollaborationServiceImpl`**. So:

- share → collaboration (`CollaborationService.isAcceptedCollaborator`)
- collaboration → share (this event class)

That's a cycle, and it defeats the boundary the events were introduced to preserve. It works at runtime — the class resolves — but a Spring Modulith `ApplicationModules.of(...).verify()` test should fail on it.

**Fix:** move the class to `feature.collaboration.event` and update the import in `NoteShareCleanupListener`.

### 14.3 🟢 Redundant configuration classes

- `config/SchedulingConfig.java` — `@EnableScheduling` is already on `AppConfig`. Delete it.
- `feature/transcription/config/AsyncConfig.java` still carries `@EnableAsync`, duplicating `AppConfig`. Harmless (idempotent) but remove it for one obvious home. **Its `transcriptionExecutor` bean must stay.**

### 14.4 🟢 Doubled path segments

`/api/v1/login/login`, `/api/v1/logout/logout`, `/api/v1/logout/logout-all`, `/api/v1/session/sessions`, `/api/v1/session/sessions/{id}` — the class-level `@RequestMapping` and the method-level mapping repeat the same noun. Cosmetic, but it's a public API contract, so changing it later costs a client migration. Cheapest to fix now if it's going to be fixed at all.

---

## 15. Hardening backlog

### Blocks production deployment

| Item | Why it matters |
|---|---|
| **Flyway: built (V1 to V11, see Migrations)** | Flyway runs in prod (`src/main/resources/db/migration`, V1 to V4 were the first four: baseline schema, Modulith event publication, `users.internal_auth_id`, drop local password and OTP tables). Prod runs `ddl-auto=validate`; dev has Flyway off and uses `ddl-auto=update`. Every entity change needs a new migration file with the next number. The manual DDL in `docs/` (`file_blobs_manual_ddl.sql`, `voice_shares_manual_ddl.sql`) must be folded into migrations before they are relied on. |
| **File storage: built, needs a staging test** | `R2FileStorageService` and `PostgresFileStorageService` exist for prod, chosen by `STORAGE_BACKEND` (see §6). Test the chosen backend on staging before release. |

### Correctness and security

| Item | Notes |
|---|---|
| **User id → name lookup** | Built: `GET /user/lookup?ids=1,2,3` → `List<UserSummaryResponse>`. Known trade-off: ids are sequential, so an authenticated caller can walk the endpoint and enumerate public profiles. The per-request cap bounds the rate; the `/sync` rate limiter below should cover this path too. |
| **Search wildcard escaping** | `%` and `_` in a search term act as SQL wildcards. Needs `ESCAPE` handling. |
| **Magic-byte file validation** | Content type is client-declared and spoofable; the allowlist also produces false rejections. |
| **Rate limiting on `/sync`** | `GET /sync` is designed to be polled and nothing bounds it. Needs bucket4j or a Redis limiter. |
| **Modulith verification test** | `ApplicationModules.of(NotareApplication.class).verify()` would catch §14.2 and any future cycle. Not currently present. |

### Feature gaps deliberately deferred

| Item | Step | Notes |
|---|---|---|
| Shared-with-me `totalElements` overcounts | 7 | Counts grants including trashed notes. Needs a JPQL `NoteShare→Note` join. |
| No sort-by-note-recency on shared-with-me | 7 | Only the grant's own columns are sortable. Same join fixes it. |
| `SharedNoteDetailResponse` | 7 | Shared reads currently expose the owner's `pinned`/`archived`. |
| Revoke doesn't notify an open client | 7 | Access is gone server-side; the client learns on its next request. |
| Notification payload column | 8 | Permission-changed can't say promotion vs demotion. |
| `AFTER_COMMIT` retry could double-notify | 8 | No unique constraint, by design. Cosmetic in-app; matters once email exists. |
| No notification for `CollaborationRemovedEvent` | 8 | Would need a new `NotificationType`. |
| Email / push channels | 8 | Seam is ready — one `@Component`. |
| Real-time SSE/WebSocket | 8 | Polling for now. |
| Monotonic change-log for sync | 9 | Timestamp cursor + overlap is the pragmatic version; an outbox is the correct one. |
| Unified ranked search | 3, 5 | Needs Elasticsearch/OpenSearch. |
| Extract `NoteAccessPort` | 7 | If the pre-authorized accessors ever get called outside `SharedNoteServiceImpl`. |

---

## 16. Configuration reference

| Property | Default | Purpose |
|---|---|---|
| `app.title` | `api/v1` | Path prefix for every controller |
| `spring.jpa.open-in-view` | `false` | No lazy loading in the view layer |
| `spring.servlet.multipart.max-file-size` | `25MB` | Hard container ceiling |
| `spring.servlet.multipart.max-request-size` | `30MB` | |
| `notare.voice.max-size` | `20MB` | App-level limit; must stay ≤ multipart ceiling |
| `notare.voice.max-duration-seconds` | `600` | Only checked when the client supplies a duration |
| `notare.storage.local.location` | `./uploads/voice-notes` | Local storage root (dev); env `VOICE_STORAGE_LOCATION` |
| `notare.storage.backend` | `r2` | Prod only: `r2` or `postgres`; env `STORAGE_BACKEND` |
| `notare.storage.r2.*` | *(empty)* | `endpoint`, `bucket`, `access-key-id`, `secret-access-key`, `region` (default `auto`); env `R2_*`, falling back to `AWS_*` |
| `spring.flyway.enabled` | `true` (prod), `false` (dev) | Prod also sets `connect-retries=10` |
| `app.admin.wipe-secret` | env `ADMIN_WIPE_SECRET` | Checked as `X-Admin-Secret` by `/admin/database/wipe` |
| `internal-auth.user-events.secret` | env `INTERNAL_AUTH_USER_EVENTS_SECRET` | Required by `/internal/user-events`; unset means every call is rejected |
| `notare.sync.overlap-seconds` | `5` | Delta window rewind |
| `notare.sync.default-page-size` | `100` | |
| `notare.sync.max-page-size` | `500` | |
| `notare.sync.retention-days` | `30` | Ledger + tombstone sweep window |
| `notare.trash.retention-days` | `30` | Days a trashed note stays in Recently deleted (`purge_at = deleted_at + this`) |
| `notare.trash.purge-batch-size` | `200` | Notes `TrashPurgeJob` reads per page |
| `app.otp.*` | length 6, 10 min, 5 attempts, 60s cooldown | |
| `app.jwt.secret` | *(required)* | Base64, ≥ 32 bytes. No default — startup fails fast |
| `app.jwt.expiration-ms` | `900000` | 15 minutes |
| `app.session.refresh-token-expiry-days` | `30` | |
| `app.session.max-concurrent-sessions` | `5` | |

> **An explicit property replaces a `@ConfigurationProperties` field default wholesale** — Spring binds over the initializer, it does not merge. Setting a list in `application.properties` and then editing the Java default changes nothing at runtime. This cost real debugging time on `notare.voice.allowed-content-types`.

---

## 17. Principles worth carrying forward

Distilled from ten steps. These generalise beyond this codebase.

**Event phase is a design decision with a stated cost, every time.** The same question got four different answers:

| Phase | Used for | Because |
|---|---|---|
| `AFTER_COMMIT` + `@Async` | transcription, notifications | Slow side-effects whose failure costs a transcript or an email |
| `BEFORE_COMMIT` | grant cleanup (7F), tombstones (9F) | Revokes access / is the only record of something irreversible — must fail *with* its cause |
| `REQUIRES_NEW` | notification create | Must never roll back its trigger |

The deciding question is always: *what does it cost for this side-effect to succeed or fail independently?*

**`@TransactionalEventListener` silently never fires when published outside a transaction.** No error, no warning — Spring drops the event. Fix with `fallbackExecution = true`, **not** by wrapping the publisher in `@Transactional` (which usually undoes an earlier good decision).

**`@Async` and `@Transactional` both work through AOP proxies, so a same-bean call bypasses them entirely.** Silently. This is why `TranscriptionWorker` and `SyncOperationApplier` are separate beans.

**Events are complete statements of what happened, never pointers.** A listener runs later and would report the world as it is *now*. This is why note titles travel inside share events.

**A config class that merely restates a framework default is not neutral — it takes ownership of that area and disables whatever else the framework was doing.** This bit three times: `StorageConfig` (duplicate bean, startup failure), the `maven-compiler-plugin` block (silently killed Lombok), `JacksonConfig` (silently broke RFC 9457 conformance). For every class in `config/`, ask what it does that Boot wouldn't. "Nothing" is a future outage with a delay fuse.

**Absence is not a synchronizable fact.** Anything a client must be told about has to leave a row behind.

**Idempotence is handled at the source.** Capture state *before* a transition and only act if something changed — not by deduplicating afterwards.

**A column that can never disagree with another column is duplication that looks like rigour.**

---

*Last updated: 7 August 2026 — after Step 10, before cross-cutting hardening.*

---

## Voice clips as children of a note (step 17b)

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/notes/{noteId}/voice-notes` | 201 + `Location`, or 200 | multipart: `file`, `clientId` (1 to 64 chars, required), `durationSeconds`. Owner or Editor. The same `clientId` again returns the stored clip with **200** and stores nothing. No access is 404, a Viewer is 403. |

- **Schema (V6):** `voice_notes.client_id` (unique per owner, `uq_voice_notes_owner_client_id`), `deleted`, `deleted_at`. `VoiceNote` carries `@SQLRestriction("deleted = false")`, so a hidden clip is absent from every query; the three life-cycle queries are native so they can reach it.
- **Clips follow their note** (`VoiceNoteLifecycleListener`, BEFORE_COMMIT, same transaction as the note change): trashing a note hides its clips, `NoteRestoredEvent` brings them back, a permanent delete removes the rows and publishes `VoiceNoteDeletedEvent` per clip (voice shares go with them). The stored files (R2 or Postgres storage) are removed after the commit, best effort.
- **Older endpoints** `POST /voice-notes` and `POST /shared-notes/{noteId}/voice-notes` still work. They are removed, and `voice_notes.note_id` becomes NOT NULL, after the data migration of step 18.

### Leave a note and copy a clip (step 17f)

`DELETE /shared-notes/{noteId}/access` removes the caller's own grant (404 for anyone without one) and publishes `NoteShareRevokedEvent` with `left = true`: the live channel closes and clip access is cleaned up, but the leaver gets no notification. `POST /voice-notes/{id}/copy` with `{ targetNoteId, clientId }` copies a clip the caller can read (owner, grant on its note, or a share on the clip) into one of their own notes, duplicating the stored object inside the storage backend (`FileStorageService.copy`). Idempotent on `(owner, clientId)`: 201 first time, 200 on a repeat.

---

## Change password and delete account (step 17g)

| Method | Path | Success | Notes |
|---|---|---|---|
| POST | `/user/password/change` | 204 | `{ currentPassword, newPassword }`. Forwarded to the auth service's `POST /auth/change-password`. A wrong current password is **400 "Incorrect password."**; a new password that breaks the rules is a 400 with the reason. The caller stays signed in: the auth service keeps one session per user, so there is no other session to end. |
| DELETE | `/user/me` | 204 | `{ username }` as confirmation (400 "The username does not match." otherwise). Deletes the account and everything it owns. |

**Account deletion is resumable.** `account_deletions` records the step, and each step runs in its own transaction:
`STARTED` (publish `UserDeletionRequested`: notes go through the normal permanent delete, so shares, collaboration data, clips, stored files and tombstones cascade; this user's clips, grants, connections and transcripts go), then `DATA_DELETED` (publish `UserDeletionSweep`: tombstones and ledger, notifications, authorship columns on other people's rows), then `SWEPT` (delete the auth user; already gone counts), then `AUTH_DELETED` (delete the local user and the progress row, so nothing keeps the id). A failure keeps the row: calling again continues, and `AccountDeletionRecoveryJob` continues any deletion stuck for 10 minutes.

Edits this person made to other people's collaborative notes stay with the note, credited to its owner. Voice clips they added to other people's notes are deleted with their stored files.

---

## Sessions (step 17h)

`GET /session/sessions` returns one entry per device the account is signed in on:
`{ sessionId, deviceLabel, userAgent, ipAddress, createdAt, lastActiveAt, expiresAt, current }`.

- `current` is true for the session whose token made the request.
- `deviceLabel` is "Mac, Chrome", parsed from the User-Agent when the session is created.
- `lastActiveAt` is the last sign-in or token refresh.
- `DELETE /session/sessions/{sessionId}` signs out that one device. The others keep working.

internal-auth-server owns all of this. The limit is its `max_active_sessions` setting on the client application, 1 by default, so other apps are unchanged. Notare is set to 5 with `PATCH {app.title}/client-applications/{clientId}` and `{ "revokePriorSessionsOnLogin": false, "maxActiveSessions": 5 }`. Both are needed: the auth server refuses more than one session while a sign-in still revokes the others. The 6th sign-in signs out the device used least recently.

---

## Notifications (step 17i)

`GET /notifications` returns each notification as `{ id, type, actorId, targetType, targetId, targetLabel, previousPermission, newPermission, readAt, createdAt }`.

`previousPermission` and `newPermission` (`READ` or `WRITE`) are set only on the two permission-changed types. They are null on everything else and on rows created before V11. The client says Viewer for `READ` and Editor for `WRITE`, and tells promotion from demotion by comparing the pair.

**Types.** Names are stable and never removed. Clients show "Something new happened" for a type they do not know.

| Type | Recipient | Actor | Target |
|---|---|---|---|
| `COLLABORATION_INVITED` | invitee | owner | collaboration |
| `COLLABORATION_ACCEPTED` | owner | invitee | collaboration |
| `COLLABORATION_REJECTED` | owner | invitee | collaboration |
| `COLLABORATION_REMOVED` | removed collaborator | owner | none |
| `NOTE_SHARED` | person shared with | owner | note |
| `NOTE_SHARE_PERMISSION_CHANGED` | person shared with | owner | note |
| `NOTE_SHARE_REVOKED` | person shared with | owner | none |
| `TRANSCRIPTION_COMPLETED` | clip owner | none | note |
| `TRANSCRIPTION_FAILED` | clip owner | none | note |
| `VOICE_NOTE_SHARED` | person shared with | owner | voice note |
| `VOICE_NOTE_SHARE_PERMISSION_CHANGED` | person shared with | owner | voice note |
| `VOICE_NOTE_SHARE_REVOKED` | person shared with | owner | none |

`COLLABORATION_REMOVED` is sent only when an owner removes an accepted collaborator. Withdrawing a pending invitation sends nothing.

---

## Safety and hygiene (step 17j)

**Rate limits (B-15).** Per user, per minute, counted in Redis. Over the limit is **429** with a `Retry-After` header in seconds.

| Endpoint | Setting | Starting value |
|---|---|---|
| `GET /sync` | `notare.rate-limit.sync-per-minute` | 30 |
| `GET /user/lookup` and `GET /user/search` (one shared counter) | `notare.rate-limit.lookup-per-minute` | 20 |

The window opens on a user's first request and lasts a minute. If Redis cannot be reached, requests are let through and a warning is logged.

**Search (B-18).** `%`, `_` and the escape character are escaped in the term before the `LIKE` in note, transcript and user search (`LikeEscaper`, `ESCAPE '!'`).

**Note text cap (B-19).** One setting, `notare.note.max-content-length` (default 100000 characters), applied by `@MaxNoteLength` to create, autosave and update. It is an abuse cap. The product limit of 20,000 and its counter live in the client. Notes have no `title` in the request, response or sync contract; shared-note responses still return a `title` derived from the first line of the body.

**Voice uploads (B-22).** The format is decided by the file's first bytes, not the declared type: WebM (`1A 45 DF A3`), Ogg (`OggS`), MP4/M4A (`ftyp` at byte 4), WAV (`RIFF` and `WAVE`) and MP3 (`ID3` or `FF FB`). Anything else is a 400. The 20 MB and 10-minute caps are unchanged. `notare.voice.allowed-content-types` is removed.

---

## Minimum client version (step 17k)

The client sends its version in the `X-Notare-Client-Version` header (for example `0.1.0`). If it is lower than `notare.client.min-version` (environment variable `CLIENT_MIN_VERSION`, default `0.0.0`, which turns nobody away), the server answers **426** with a problem body:

```json
{ "title": "Update required", "status": 426, "code": "CLIENT_TOO_OLD", "minimumVersion": "0.2.0" }
```

- It applies to every client endpoint. It does not apply to `/internal/**`, `/admin/**`, `/keep-alive` or the WebSocket.
- A request with no header, or a header that is not a version, is let through.
- Missing minor and patch parts count as 0, and a leading `v` or a suffix such as `-beta.1` is ignored.
- A malformed `notare.client.min-version` stops the application from starting.
- It runs before the rate limits, so an outdated client does not use up its allowance.
- `Retry-After` is an exposed CORS header, so the browser client can read it on a 429.

---

## Migrations

Flyway runs in prod (`src/main/resources/db/migration`) with `ddl-auto=validate`. Dev has Flyway off and uses `ddl-auto=update`. Every entity change needs the next numbered file.

| Version | What it does | Step |
|---|---|---|
| V1 | Baseline schema | 1 |
| V2 | Spring Modulith event publication | 1 |
| V3 | `users.internal_auth_id` | 11 |
| V4 | Drop local password and OTP tables | 11 |
| V5 | Collaboration: body mode, persisted updates and snapshots | 15 |
| V6 | Voice clips as children of a note, with client ids | 17b |
| V7 | Clip sync | 17c |
| V8 | Transcription for editors (`requested_by`) | 17d |
| V9 | Trash and purge (`purge_at`) | 17e |
| V10 | Account deletions (resumable progress) | 17g |
| V11 | Notification permission columns (`previous_permission`, `new_permission`) | 17i |

---

## Real-time editing ticket (step 15)

| Method | Path | Notes |
|---|---|---|
| POST | `/notes/{noteId}/collab-ticket` | returns a short-lived ticket the client presents when opening the collaboration WebSocket; the protocol is in `docs/collab-protocol.md` |
