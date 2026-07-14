-- =====================================================================
-- V5 — Local MVP demo guide availability
--
-- Depends on V3's approved fixture guides and V4's ACTIVE offerings.
-- Each seeded guide receives a repeatable weekday schedule plus booking
-- settings. The occurrence rows are the materialized availability cache,
-- seeded for the current 30-day booking window so participant slot reads are
-- useful immediately after Flyway runs. The availability service may safely
-- re-materialize that cache from these persisted rules at any time.
-- =====================================================================

INSERT INTO guide_booking_settings (
  guide_id,
  acceptance_mode,
  response_deadline_min,
  min_notice_min,
  max_advance_days,
  buffer_before_min,
  buffer_after_min,
  durations_offered,
  timezone
)
SELECT
  guide_profile.id,
  'MANUAL'::acceptance_mode,
  90,
  60,
  30,
  0,
  15,
  '[30,45,60]'::jsonb,
  seed.timezone
FROM (
  VALUES
    ('seed-guide-ava-rivera', 'America/Los_Angeles'),
    ('seed-guide-noah-kim', 'America/Los_Angeles'),
    ('seed-guide-priya-shah', 'America/New_York'),
    ('seed-guide-ethan-brooks', 'America/Chicago')
) AS seed(guide_subject, timezone)
JOIN users guide_user ON guide_user.oidc_subject = seed.guide_subject
JOIN guide_profiles guide_profile ON guide_profile.user_id = guide_user.id
ON CONFLICT (guide_id) DO NOTHING;

-- Monday through Friday, 10:00–14:00 in each guide's own timezone.
-- PostgreSQL's extract(dow) convention matches the API: Sunday=0 .. Saturday=6.
INSERT INTO guide_availability_rules (
  id,
  guide_id,
  day_of_week,
  start_local,
  window_min,
  timezone,
  effective_from,
  active
)
SELECT
  seed.rule_id,
  guide_profile.id,
  weekday.day_of_week,
  '10:00'::time,
  240,
  seed.timezone,
  CURRENT_DATE,
  true
FROM (
  VALUES
    ('20000000-0000-0000-0000-000000000001'::uuid, 'seed-guide-ava-rivera', 'America/Los_Angeles'),
    ('20000000-0000-0000-0000-000000000002'::uuid, 'seed-guide-noah-kim', 'America/Los_Angeles'),
    ('20000000-0000-0000-0000-000000000003'::uuid, 'seed-guide-priya-shah', 'America/New_York'),
    ('20000000-0000-0000-0000-000000000004'::uuid, 'seed-guide-ethan-brooks', 'America/Chicago')
) AS seed(rule_id, guide_subject, timezone)
JOIN users guide_user ON guide_user.oidc_subject = seed.guide_subject
JOIN guide_profiles guide_profile ON guide_profile.user_id = guide_user.id
CROSS JOIN (VALUES (1::smallint), (2::smallint), (3::smallint), (4::smallint), (5::smallint))
  AS weekday(day_of_week)
ON CONFLICT (id) DO NOTHING;

-- Materialize the initial 30-day cache from the seeded rules. Adding the interval
-- after converting the local start to an instant matches the application's
-- ZonedDateTime.plus(Duration) projection behaviour across DST transitions.
WITH seed_rules AS (
  SELECT
    rule.id AS rule_id,
    rule.guide_id,
    rule.day_of_week,
    rule.start_local,
    rule.window_min,
    rule.timezone
  FROM guide_availability_rules rule
  JOIN guide_profiles guide_profile ON guide_profile.id = rule.guide_id
  JOIN users guide_user ON guide_user.id = guide_profile.user_id
  WHERE guide_user.oidc_subject IN (
    'seed-guide-ava-rivera',
    'seed-guide-noah-kim',
    'seed-guide-priya-shah',
    'seed-guide-ethan-brooks'
  )
    AND rule.id BETWEEN
      '20000000-0000-0000-0000-000000000001'::uuid AND
      '20000000-0000-0000-0000-000000000004'::uuid
), rule_dates AS (
  SELECT
    rule.*,
    day::date AS occurrence_date
  FROM seed_rules rule
  CROSS JOIN generate_series(CURRENT_DATE, CURRENT_DATE + 30, interval '1 day') AS day
  WHERE EXTRACT(DOW FROM day)::smallint = rule.day_of_week
), occurrences AS (
  SELECT
    rule_id,
    guide_id,
    ((occurrence_date + start_local) AT TIME ZONE timezone) AS start_at,
    ((occurrence_date + start_local) AT TIME ZONE timezone)
      + make_interval(mins => window_min) AS end_at
  FROM rule_dates
)
INSERT INTO guide_availability_occurrences (
  guide_id,
  during_start_at,
  during_end_at,
  source_rule_id
)
SELECT
  guide_id,
  start_at,
  end_at,
  rule_id
FROM occurrences
WHERE NOT EXISTS (
  SELECT 1
  FROM guide_availability_occurrences existing
  WHERE existing.source_rule_id = occurrences.rule_id
    AND existing.during_start_at = occurrences.start_at
    AND existing.during_end_at = occurrences.end_at
);
