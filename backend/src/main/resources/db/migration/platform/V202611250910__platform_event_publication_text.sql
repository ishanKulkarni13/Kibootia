-- An event that grows past 4,000 characters failed to be stored and so failed the transaction that raised it (ADR 0059).
-- TEXT holds 65,535 bytes. Hibernate validates the column against the registry's mapped length at startup; the
-- integration tests start the application, so a type it refuses fails the build, not a deployment.
ALTER TABLE event_publication MODIFY serialized_event TEXT NOT NULL;
