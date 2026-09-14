-- =====================================================================
-- V4 -- Broader demo availability for local booking-flow testing
--
-- V3 made the demo catalog bookable on weekday mornings. Keep that
-- applied migration immutable and add weekend choices forward-only so a
-- tester can complete the flow on any day of the week.
-- =====================================================================

CREATE TEMP TABLE ctl120_demo_guides ON COMMIT DROP AS
SELECT DISTINCT ON (guide_profile.id)
  guide_profile.id AS guide_id,
  settings.timezone
FROM users guide_user
JOIN guide_profiles guide_profile ON guide_profile.user_id = guide_user.id
JOIN guide_universities guide_university ON guide_university.guide_profile_id = guide_profile.id
JOIN universities university ON university.id = guide_university.university_id
JOIN guide_booking_settings settings ON settings.guide_id = guide_profile.id
WHERE guide_user.oidc_subject LIKE 'seed-guide-%'
  AND guide_profile.guide_status = 'VERIFIED'::guide_application_status
  AND guide_university.verification_status = 'VERIFIED'::guide_verification_status
  AND university.status = 'ACTIVE'::university_status
ORDER BY guide_profile.id, university.slug;

-- Add one daytime window on weekends. These rows are source availability;
-- the occurrence cache below is derived.
CREATE TEMP TABLE ctl120_additional_windows (
  day_of_week smallint NOT NULL,
  start_local time NOT NULL,
  window_min integer NOT NULL
) ON COMMIT DROP;

INSERT INTO ctl120_additional_windows (day_of_week, start_local, window_min)
SELECT weekend.day_of_week, '11:00'::time, 240
FROM (VALUES (0::smallint), (6::smallint)) AS weekend(day_of_week);

-- Serialize every source/cache mutation with guide writes and the horizon job.
SELECT pg_advisory_xact_lock(hashtextextended(guide_id::text, 0))
FROM ctl120_demo_guides
ORDER BY guide_id;

-- Java chooses the earlier offset during a DST overlap; PostgreSQL chooses the later one.
-- Resolve the small set of modern overlap sizes explicitly so SQL matches ZonedDateTime.of.
CREATE FUNCTION pg_temp.ctl120_resolve_local(local_date date, local_time time, zone_name text)
RETURNS timestamptz
LANGUAGE sql STABLE STRICT AS $$
  WITH input AS (
    SELECT local_date + local_time AS wall,
           (local_date + local_time) AT TIME ZONE zone_name AS fallback
  )
  SELECT COALESCE(
    min(candidate) FILTER (WHERE candidate AT TIME ZONE zone_name = wall),
    max(fallback)
  )
  FROM input
  CROSS JOIN LATERAL (
    VALUES (fallback - interval '2 hours'), (fallback - interval '1 hour'),
           (fallback - interval '30 minutes'), (fallback)
  ) candidates(candidate)
$$;

INSERT INTO guide_availability_rules (
  guide_id,
  day_of_week,
  start_local,
  window_min,
  timezone,
  effective_from,
  active
)
SELECT
  demo_guide.guide_id,
  slot_window.day_of_week,
  slot_window.start_local,
  slot_window.window_min,
  demo_guide.timezone,
  CURRENT_DATE,
  true
FROM ctl120_demo_guides demo_guide
CROSS JOIN ctl120_additional_windows slot_window
WHERE NOT EXISTS (
  SELECT 1
  FROM guide_availability_rules existing
  WHERE existing.guide_id = demo_guide.guide_id
    AND existing.day_of_week = slot_window.day_of_week
    AND existing.start_local = slot_window.start_local
    AND existing.window_min = slot_window.window_min
    AND existing.active = true
    AND existing.effective_from <= CURRENT_DATE
    AND existing.effective_to IS NULL
);

-- Re-project the local booking window using the same precedence as AvailabilityProjection:
-- recurring rules minus UNAVAILABLE exceptions, then ADDITIONAL exceptions win on overlap.
CREATE TEMP TABLE ctl120_target_dates ON COMMIT DROP AS
SELECT
  demo_guide.guide_id,
  demo_guide.timezone,
  day::date AS occurrence_date
FROM ctl120_demo_guides demo_guide
CROSS JOIN generate_series(CURRENT_DATE - 2, CURRENT_DATE + 32, interval '1 day') AS day;

CREATE TEMP TABLE ctl120_projected_occurrences ON COMMIT DROP AS
WITH rule_ranges AS (
  SELECT
    target.guide_id,
    target.occurrence_date,
    tstzrange(
      pg_temp.ctl120_resolve_local(target.occurrence_date, rule.start_local, rule.timezone),
      pg_temp.ctl120_resolve_local(target.occurrence_date, rule.start_local, rule.timezone)
        + make_interval(mins => rule.window_min),
      '[)'
    ) AS during
  FROM ctl120_target_dates target
  JOIN guide_availability_rules rule
    ON rule.guide_id = target.guide_id
   AND rule.day_of_week = EXTRACT(DOW FROM target.occurrence_date)::smallint
   AND rule.active = true
   AND rule.effective_from <= target.occurrence_date
   AND (rule.effective_to IS NULL OR rule.effective_to >= target.occurrence_date)
), rule_unions AS (
  SELECT guide_id, occurrence_date, range_agg(during) AS available
  FROM rule_ranges
  GROUP BY guide_id, occurrence_date
), exception_ranges AS (
  SELECT
    target.guide_id,
    target.occurrence_date,
    exception.kind,
    tstzrange(
      pg_temp.ctl120_resolve_local(target.occurrence_date, exception.start_local, target.timezone),
      pg_temp.ctl120_resolve_local(target.occurrence_date, exception.start_local, target.timezone)
        + make_interval(mins => exception.window_min),
      '[)'
    ) AS during
  FROM ctl120_target_dates target
  JOIN availability_exceptions exception
    ON exception.guide_id = target.guide_id
   AND exception.exception_date = target.occurrence_date
), exception_unions AS (
  SELECT
    guide_id,
    occurrence_date,
    COALESCE(range_agg(during) FILTER (WHERE kind = 'UNAVAILABLE'), '{}'::tstzmultirange)
      AS unavailable,
    COALESCE(range_agg(during) FILTER (WHERE kind = 'ADDITIONAL'), '{}'::tstzmultirange)
      AS additional
  FROM exception_ranges
  GROUP BY guide_id, occurrence_date
), projected_days AS (
  SELECT
    target.guide_id,
    target.occurrence_date,
    (COALESCE(rules.available, '{}'::tstzmultirange)
      - COALESCE(exceptions.unavailable, '{}'::tstzmultirange))
      + COALESCE(exceptions.additional, '{}'::tstzmultirange) AS available
  FROM ctl120_target_dates target
  LEFT JOIN rule_unions rules USING (guide_id, occurrence_date)
  LEFT JOIN exception_unions exceptions USING (guide_id, occurrence_date)
)
SELECT projected.guide_id, lower(segment) AS start_at, upper(segment) AS end_at
FROM projected_days projected
CROSS JOIN LATERAL unnest(projected.available) AS segment
WHERE upper(segment) > ((CURRENT_DATE - 1)::timestamp AT TIME ZONE 'UTC')
  AND lower(segment) < ((CURRENT_DATE + 32)::timestamp AT TIME ZONE 'UTC');

DELETE FROM guide_availability_occurrences existing
USING ctl120_demo_guides demo_guide
WHERE existing.guide_id = demo_guide.guide_id
  AND existing.during_end_at > ((CURRENT_DATE - 1)::timestamp AT TIME ZONE 'UTC')
  AND existing.during_start_at < ((CURRENT_DATE + 32)::timestamp AT TIME ZONE 'UTC');

INSERT INTO guide_availability_occurrences (guide_id, during_start_at, during_end_at)
SELECT guide_id, start_at, end_at
FROM ctl120_projected_occurrences;
