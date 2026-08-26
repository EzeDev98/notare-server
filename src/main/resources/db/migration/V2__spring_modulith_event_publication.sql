-- V2__spring_modulith_event_publication.sql
--
-- Table for Spring Modulith's JPA-based event publication registry
-- (spring-modulith-starter-jpa, pinned to 1.1.12 in pom.xml). This is NOT
-- one of the app's own @Entity classes — it's an internal entity
-- (org.springframework.modulith.events.jpa.JpaEventPublication) that
-- Spring Modulith registers itself once spring-modulith-starter-jpa is on
-- the classpath, used to track @TransactionalEventListener /
-- @ApplicationModuleListener publications so they can be replayed if a
-- listener fails mid-transaction.
--
-- Because Hibernate manages this as a real entity, prod's
-- hibernate.ddl-auto=validate expects this table to already exist and
-- match Hibernate's model exactly — same reasoning as every other table
-- in V1. dev doesn't hit this because ddl-auto=update there creates it
-- automatically on first boot.
--
-- Schema copied verbatim from the version-matched docs (1.1.12), not the
-- current docs — the current release has extra columns (status,
-- completion_attempts, last_resubmission_date) added in later versions
-- that don't exist in 1.1.12's entity and would fail validation just the
-- same way the missing table did:
-- https://docs.spring.io/spring-modulith/reference/1.1/appendix.html#schemas.postgresql

CREATE TABLE event_publication
(
    id               UUID                     NOT NULL,
    listener_id      TEXT                     NOT NULL,
    event_type       TEXT                     NOT NULL,
    serialized_event TEXT                     NOT NULL,
    publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date  TIMESTAMP WITH TIME ZONE,

    PRIMARY KEY (id)
);