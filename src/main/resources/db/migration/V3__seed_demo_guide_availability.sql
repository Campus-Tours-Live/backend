-- =====================================================================
-- V3 -- Demo guide availability for bookable tour slots
--
-- V2 intentionally seeded marketplace tours without availability rows, so
-- GET /offerings/{id}/slots returned [] for the local/demo catalog. This
-- migration keeps slots derived from the existing availability model:
-- guide_booking_settings + guide_availability_rules + materialized
-- guide_availability_occurrences.
-- =====================================================================

CREATE TEMP TABLE ctl120_demo_guides ON COMMIT DROP AS
SELECT DISTINCT ON (guide_profile.id)
  guide_profile.id AS guide_id,
  COALESCE(settings.timezone, university.timezone, guide_user.timezone, 'America/Los_Angeles') AS timezone
FROM users guide_user
JOIN guide_profiles guide_profile ON guide_profile.user_id = guide_user.id
JOIN guide_universities guide_university ON guide_university.guide_profile_id = guide_profile.id
JOIN universities university ON university.id = guide_university.university_id
LEFT JOIN guide_booking_settings settings ON settings.guide_id = guide_profile.id
WHERE guide_user.oidc_subject LIKE 'seed-guide-%'
  AND guide_profile.guide_status = 'VERIFIED'::guide_application_status
  AND guide_university.verification_status = 'VERIFIED'::guide_verification_status
  AND university.status = 'ACTIVE'::university_status
ORDER BY guide_profile.id, university.slug;

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
  guide_id,
  'MANUAL'::acceptance_mode,
  90,
  60,
  30,
  0,
  15,
  '[30,45,60,90]'::jsonb,
  timezone
FROM ctl120_demo_guides
ON CONFLICT (guide_id) DO NOTHING;

-- Monday through Friday, 10:00-14:00 in the guide/university timezone.
-- PostgreSQL extract(dow) uses Sunday=0 .. Saturday=6, matching the API.
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
  weekday.day_of_week,
  '10:00'::time,
  240,
  demo_guide.timezone,
  CURRENT_DATE,
  true
FROM ctl120_demo_guides demo_guide
CROSS JOIN (VALUES (1::smallint), (2::smallint), (3::smallint), (4::smallint), (5::smallint))
  AS weekday(day_of_week)
WHERE NOT EXISTS (
  SELECT 1
  FROM guide_availability_rules existing
  WHERE existing.guide_id = demo_guide.guide_id
    AND existing.day_of_week = weekday.day_of_week
    AND existing.start_local = '10:00'::time
    AND existing.window_min = 240
    AND existing.active = true
);

CREATE TEMP TABLE ctl120_demo_rules ON COMMIT DROP AS
SELECT
  rule.id AS rule_id,
  rule.guide_id,
  rule.day_of_week,
  rule.start_local,
  rule.window_min,
  rule.timezone
FROM guide_availability_rules rule
JOIN ctl120_demo_guides demo_guide ON demo_guide.guide_id = rule.guide_id
WHERE rule.active = true
  AND rule.day_of_week BETWEEN 1 AND 5
  AND rule.start_local = '10:00'::time
  AND rule.window_min = 240
  AND rule.effective_from <= CURRENT_DATE + 30
  AND (rule.effective_to IS NULL OR rule.effective_to >= CURRENT_DATE);

-- Seed the derived occurrence cache for the current 30-day booking window.
-- Future guide writes/rematerialization can replace these rows from the rules.
WITH rule_dates AS (
  SELECT
    rule.*,
    day::date AS occurrence_date
  FROM ctl120_demo_rules rule
  CROSS JOIN generate_series(CURRENT_DATE, CURRENT_DATE + 30, interval '1 day') AS day
  WHERE EXTRACT(DOW FROM day)::smallint = rule.day_of_week
), occurrences AS (
  SELECT
    rule_id,
    guide_id,
    ((occurrence_date + start_local) AT TIME ZONE timezone) AS start_at,
    ((occurrence_date + start_local) AT TIME ZONE timezone) + make_interval(mins => window_min) AS end_at
  FROM rule_dates
)
INSERT INTO guide_availability_occurrences (
  guide_id,
  during_start_at,
  during_end_at,
  source_rule_id
)
SELECT
  occurrence.guide_id,
  occurrence.start_at,
  occurrence.end_at,
  occurrence.rule_id
FROM occurrences occurrence
WHERE NOT EXISTS (
  SELECT 1
  FROM guide_availability_occurrences existing
  WHERE existing.guide_id = occurrence.guide_id
    AND tstzrange(existing.during_start_at, existing.during_end_at) &&
        tstzrange(occurrence.start_at, occurrence.end_at)
);
