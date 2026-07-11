-- =====================================================================
-- CampusToursLive.ai — Availability Engine v2 (V4)
-- CTL-54: rebuild guide availability on a START + DURATION recurrence model
--   (RFC 5545 / DTSTART+DURATION style) instead of the V1 wall-clock
--   start_local/end_local model. See:
--   docs/superpowers/specs/2026-07-11-availability-recurrence-materialized-occurrences-design.md
--
-- Availability never launched, so guide_availability_rules and
-- availability_exceptions (both defined in V1__schema.sql) are EMPTY in every
-- environment: this migration reshapes them in place rather than expand/migrate/
-- contract (forward-only: a new V4 file; V1 itself is never edited).
-- =====================================================================

-- ---------------------------------------------------------------------
-- guide_availability_rules: was (day_of_week, start_local, end_local wall-clock).
-- Now (day_of_week, start_local, window_min) — start + duration, no end column,
-- no 24:00 sentinel, no wraparound. window_min is the length of the
-- AVAILABILITY WINDOW (orthogonal to tour_offerings.duration_min / the guide's
-- durations_offered — those combine with window_min only at slot-generation time).
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS guide_availability_rules;

CREATE TABLE guide_availability_rules (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  guide_id        UUID NOT NULL REFERENCES guide_profiles(id) ON DELETE CASCADE,
  day_of_week     SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),  -- 0=Sunday
  start_local     TIME NOT NULL,
  window_min      INTEGER NOT NULL CHECK (window_min > 0),  -- availability-WINDOW length, minutes
  timezone        TEXT NOT NULL DEFAULT 'America/Los_Angeles',  -- IANA tz; must equal guide_booking_settings.timezone (existing cascade invariant, kept)
  effective_from  DATE NOT NULL DEFAULT CURRENT_DATE,
  effective_to    DATE,
  active          BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_avail_guide ON guide_availability_rules(guide_id, day_of_week);

-- ---------------------------------------------------------------------
-- availability_exceptions: was `type` (UNAVAILABLE_ALL_DAY | UNAVAILABLE_RANGE |
-- ADDITIONAL) with nullable start_local/end_local. Now `kind` (UNAVAILABLE |
-- ADDITIONAL) with NOT NULL start_local + window_min: there is no separate
-- ALL_DAY kind under start+window — "all-day unavailable" is UNAVAILABLE with
-- start_local='00:00', window_min=1440.
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS availability_exceptions;
DROP TYPE IF EXISTS availability_exception_type;

CREATE TYPE availability_exception_kind AS ENUM ('UNAVAILABLE', 'ADDITIONAL');

CREATE TABLE availability_exceptions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  guide_id        UUID NOT NULL REFERENCES guide_profiles(id) ON DELETE CASCADE,
  exception_date  DATE NOT NULL,
  kind            availability_exception_kind NOT NULL,
  start_local     TIME NOT NULL,
  window_min      INTEGER NOT NULL CHECK (window_min > 0),
  reason          TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_avail_exc_guide ON availability_exceptions(guide_id, exception_date);

-- ---------------------------------------------------------------------
-- guide_availability_occurrences: the materialized, coalesced, net-available
-- projection (rule/exception layer -> UTC occurrences). A DERIVED cache —
-- wholesale re-derived + replaced by the projection engine (CTL-54 Task 3),
-- never edited row-by-row. source_rule_id/source_exception_id are informational
-- only (a net/coalesced interval doesn't map 1:1 to a single input row) and are
-- nullable + ON DELETE SET NULL so a later rule/exception delete doesn't
-- cascade-delete occurrence history before the next rematerialize replaces it.
--
-- The logical "during tstzrange" is represented here as two physical
-- TIMESTAMPTZ columns (during_start_at / during_end_at) — the same shape this
-- schema already uses for bookings.reserved_start_at/reserved_end_at — and the
-- tstzrange is computed inline in the EXCLUDE constraint below (as
-- excl_guide_no_overlap already does for bookings), rather than stored as a
-- physical range column. This avoids introducing a Hibernate range-type
-- dependency for Task 1 while giving the identical GIST invariant; the pure
-- projection (Task 2) still works in terms of intervals, and containment
-- queries (Task 6) use tstzrange(during_start_at, during_end_at) @> ... .
-- ---------------------------------------------------------------------
CREATE TABLE guide_availability_occurrences (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  guide_id             UUID NOT NULL REFERENCES guide_profiles(id) ON DELETE CASCADE,
  during_start_at      TIMESTAMPTZ NOT NULL,
  during_end_at        TIMESTAMPTZ NOT NULL,
  source_rule_id       UUID REFERENCES guide_availability_rules(id) ON DELETE SET NULL,
  source_exception_id  UUID REFERENCES availability_exceptions(id) ON DELETE SET NULL,
  generated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (during_start_at < during_end_at)
);
CREATE INDEX ix_avail_occ_guide ON guide_availability_occurrences(guide_id, during_start_at);

-- Invariant backstop (NOT a user-facing conflict check — the projection
-- coalesces the net-available set into a disjoint union before insert, so this
-- should never fire in normal operation; if it does, the persistence layer
-- (Task 3) maps it to an internal error, never a guide-facing "conflict").
ALTER TABLE guide_availability_occurrences ADD CONSTRAINT excl_guide_occurrence_no_overlap
  EXCLUDE USING gist (
    guide_id WITH =,
    tstzrange(during_start_at, during_end_at) WITH &&
  );

-- END V4
