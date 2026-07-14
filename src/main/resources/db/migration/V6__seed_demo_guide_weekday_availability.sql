-- =====================================================================
-- V6 — Complete local MVP demo guide weekday availability
--
-- V5 establishes each seeded guide's Monday rule. This migration adds the
-- remaining Tuesday–Friday rules with one unique rule id per guide/day, then
-- materializes their initial occurrences. Keeping this as a new migration
-- preserves the forward-only Flyway history for databases that already ran V5.
-- =====================================================================

WITH inserted_rules AS (
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
    seed.day_of_week,
    '10:00'::time,
    240,
    seed.timezone,
    CURRENT_DATE,
    true
  FROM (
    VALUES
      ('21000000-0000-0000-0000-000000000001'::uuid, 'seed-guide-ava-rivera', 'America/Los_Angeles', 2::smallint),
      ('21000000-0000-0000-0000-000000000002'::uuid, 'seed-guide-ava-rivera', 'America/Los_Angeles', 3::smallint),
      ('21000000-0000-0000-0000-000000000003'::uuid, 'seed-guide-ava-rivera', 'America/Los_Angeles', 4::smallint),
      ('21000000-0000-0000-0000-000000000004'::uuid, 'seed-guide-ava-rivera', 'America/Los_Angeles', 5::smallint),
      ('21000000-0000-0000-0000-000000000005'::uuid, 'seed-guide-noah-kim', 'America/Los_Angeles', 2::smallint),
      ('21000000-0000-0000-0000-000000000006'::uuid, 'seed-guide-noah-kim', 'America/Los_Angeles', 3::smallint),
      ('21000000-0000-0000-0000-000000000007'::uuid, 'seed-guide-noah-kim', 'America/Los_Angeles', 4::smallint),
      ('21000000-0000-0000-0000-000000000008'::uuid, 'seed-guide-noah-kim', 'America/Los_Angeles', 5::smallint),
      ('21000000-0000-0000-0000-000000000009'::uuid, 'seed-guide-priya-shah', 'America/New_York', 2::smallint),
      ('21000000-0000-0000-0000-000000000010'::uuid, 'seed-guide-priya-shah', 'America/New_York', 3::smallint),
      ('21000000-0000-0000-0000-000000000011'::uuid, 'seed-guide-priya-shah', 'America/New_York', 4::smallint),
      ('21000000-0000-0000-0000-000000000012'::uuid, 'seed-guide-priya-shah', 'America/New_York', 5::smallint),
      ('21000000-0000-0000-0000-000000000013'::uuid, 'seed-guide-ethan-brooks', 'America/Chicago', 2::smallint),
      ('21000000-0000-0000-0000-000000000014'::uuid, 'seed-guide-ethan-brooks', 'America/Chicago', 3::smallint),
      ('21000000-0000-0000-0000-000000000015'::uuid, 'seed-guide-ethan-brooks', 'America/Chicago', 4::smallint),
      ('21000000-0000-0000-0000-000000000016'::uuid, 'seed-guide-ethan-brooks', 'America/Chicago', 5::smallint)
  ) AS seed(rule_id, guide_subject, timezone, day_of_week)
  JOIN users guide_user ON guide_user.oidc_subject = seed.guide_subject
  JOIN guide_profiles guide_profile ON guide_profile.user_id = guide_user.id
  ON CONFLICT (id) DO NOTHING
  RETURNING id, guide_id, day_of_week, start_local, window_min, timezone
), rule_dates AS (
  SELECT
    rule.*,
    day::date AS occurrence_date
  FROM inserted_rules rule
  CROSS JOIN generate_series(CURRENT_DATE, CURRENT_DATE + 30, interval '1 day') AS day
  WHERE EXTRACT(DOW FROM day)::smallint = rule.day_of_week
), occurrences AS (
  SELECT
    rule.id AS rule_id,
    rule.guide_id,
    ((rule.occurrence_date + rule.start_local) AT TIME ZONE rule.timezone) AS start_at,
    ((rule.occurrence_date + rule.start_local) AT TIME ZONE rule.timezone)
      + make_interval(mins => rule.window_min) AS end_at
  FROM rule_dates rule
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
FROM occurrences;
