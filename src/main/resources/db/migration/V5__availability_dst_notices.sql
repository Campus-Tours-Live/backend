-- =====================================================================
-- CampusToursLive.ai — Availability DST notices (V5)
-- CTL-54 Task 3: persist the projection's "DST-adjusted days" signal so the
--   resolved-availability read model (Task 5b) can surface a per-guide warning
--   ("a daylight-saving transition shifted/absorbed one of your windows on
--   these dates") instead of the persistence layer computing-and-discarding it.
--
-- Like guide_availability_occurrences, this is a DERIVED cache: wholesale
-- re-derived + replaced per guide on every rematerialize (delete-by-guide +
-- insert-from-projection), never edited row-by-row. Forward-only: a new V5
-- file; earlier migrations are never edited.
-- =====================================================================
CREATE TABLE guide_availability_dst_notices (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  guide_id       UUID NOT NULL REFERENCES guide_profiles(id) ON DELETE CASCADE,
  adjusted_date  DATE NOT NULL,
  generated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_avail_dst_guide ON guide_availability_dst_notices(guide_id);

-- END V5
