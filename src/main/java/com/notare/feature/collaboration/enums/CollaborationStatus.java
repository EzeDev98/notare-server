package com.notare.feature.collaboration.enums;

/**
 * The state of a collaboration invitation:
 *   PENDING  -> owner sent it, invitee hasn't responded
 *   ACCEPTED -> invitee accepted; the two are now collaborators
 *   REJECTED -> invitee declined
 */
public enum CollaborationStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}