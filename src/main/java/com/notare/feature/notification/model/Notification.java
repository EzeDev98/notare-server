package com.notare.feature.notification.model;

import com.notare.feature.notification.enums.NotificationTargetType;
import com.notare.feature.notification.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * One thing the app is telling one user.
 *
 * <p><b>A notification is a historical record, not a live view.</b> That single
 * idea drives most of the design below. It describes something that happened at
 * a moment in time, and it must stay readable and truthful afterwards — after
 * the note is renamed, after the share is revoked, after the note is deleted
 * entirely. Anything resolved at READ time would quietly rewrite history or
 * collapse into a hole.
 *
 * <p>Consequences: the label is snapshotted rather than joined, almost every
 * field is immutable, and nothing in Step 8 cleans these rows up when their
 * subject disappears — unlike the grants in 7F, which had to die with their
 * cause because they conferred access. A notification confers nothing.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "notifications",
        indexes = {
                // The listing: a user's notifications, newest first.
                @Index(name = "idx_notifications_recipient_created", columnList = "recipient_id, created_at"),
                // The unread badge — a count query that runs on nearly every
                // page load, so it gets its own index rather than riding the one
                // above and filtering.
                @Index(name = "idx_notifications_recipient_read_at", columnList = "recipient_id, read_at")
        }
        // Deliberately NO unique constraint. Being shared the same note twice,
        // or invited again after a cancellation, are real sequences of events
        // and each deserves its own row. Uniqueness here would silently swallow
        // the second one.
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Who this is for. Every query in this module is scoped by it. */
    @Column(name = "recipient_id", nullable = false, updatable = false)
    private Long recipientId;

    /**
     * Who caused it — the inviter, the sharer. NULLABLE, because not everything
     * has a human behind it: a finished transcription is the system telling you
     * about your own file. Modelling that as "actor = you" would be a lie the
     * UI then has to special-case ("You transcribed your note"), so absence is
     * represented as absence.
     */
    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    /** What happened. Immutable — a notification never becomes a different notification. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private NotificationType type;

    /** What it points at, and its id. Both nullable together: a notification needn't have a destination. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30, updatable = false)
    private NotificationTargetType targetType;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    /**
     * The subject's name AS IT WAS when this happened — the note title, usually.
     *
     * <p>This is the snapshot that makes the record durable. Join to the note at
     * read time and "Femi shared 'Q3 Plan' with you" silently becomes "Femi
     * shared 'Q3 Plan (final v2)' with you" after a rename, or an empty gap once
     * the note is deleted. Neither is what happened. Storing the label costs a
     * short column and buys a notification list that stays true and never
     * breaks.
     *
     * <p>Nullable, and never a substitute for the note itself: it's a label for
     * display, while targetId is the thing to open. If the target is gone, the
     * client can still render the sentence and simply not link it.
     */
    @Column(name = "target_label", length = 255, updatable = false)
    private String targetLabel;

    /**
     * When the recipient read it. NULL means unread — so "unread" is
     * {@code readAt IS NULL} and needs no separate boolean.
     *
     * <p>A timestamp rather than a flag because it's strictly more information
     * for the same storage, and the pair (boolean, timestamp) is a redundancy
     * that eventually disagrees with itself.
     *
     * <p>The ONLY mutable field on this entity. Everything else is
     * updatable = false, which is what "historical record" means in practice.
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** When it happened. Also the listing's sort key. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // No @LastModifiedDate. The only mutation this row can undergo is being
    // read, and readAt already timestamps exactly that — an updatedAt would be
    // a second copy of the same fact, free to drift.
    //
    // No @Version either: the only writer is the recipient marking their own
    // notification read, and marking it read twice is harmless.
}