-- =====================================================================
-- V3 — Local MVP demo data
--
-- Fictional, non-loginable identities and discoverable tour data for local
-- development. Synthetic OIDC subjects ensure real users are still created
-- exclusively through the Google OIDC flow.
--
-- The seed includes approved guides, participant/admin role coverage, ACTIVE
-- offerings, and weekday guide availability. Materialized occurrences cover
-- the initial 30-day booking window so participant slot reads work immediately.
-- =====================================================================

INSERT INTO users (
  id,
  oidc_subject,
  email,
  email_verified,
  last_active_role,
  account_status,
  age_band,
  first_name,
  last_name,
  display_name,
  preferred_language,
  timezone
) VALUES
  ('10000000-0000-0000-0000-000000000001', 'seed-guide-ava-rivera', 'ava.rivera@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Ava', 'Rivera', 'Ava Rivera', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-000000000002', 'seed-guide-noah-kim', 'noah.kim@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Noah', 'Kim', 'Noah Kim', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-000000000003', 'seed-guide-priya-shah', 'priya.shah@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Priya', 'Shah', 'Priya Shah', 'en-US', 'America/New_York'),
  ('10000000-0000-0000-0000-000000000004', 'seed-guide-ethan-brooks', 'ethan.brooks@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Ethan', 'Brooks', 'Ethan Brooks', 'en-US', 'America/Chicago'),
  ('10000000-0000-0000-0000-000000000005', 'seed-participant-mia-chen', 'mia.chen@seed.campustours.local', true, 'PARTICIPANT', 'ACTIVE', 'ADULT', 'Mia', 'Chen', 'Mia Chen', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-000000000006', 'seed-participant-liam-walsh', 'liam.walsh@seed.campustours.local', true, 'PARTICIPANT', 'ACTIVE', 'ADULT', 'Liam', 'Walsh', 'Liam Walsh', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-000000000007', 'seed-participant-sofia-patel', 'sofia.patel@seed.campustours.local', true, 'PARTICIPANT', 'ACTIVE', 'ADULT', 'Sofia', 'Patel', 'Sofia Patel', 'en-US', 'America/New_York'),
  ('10000000-0000-0000-0000-000000000008', 'seed-participant-jordan-lee', 'jordan.lee@seed.campustours.local', true, 'PARTICIPANT', 'ACTIVE', 'ADULT', 'Jordan', 'Lee', 'Jordan Lee', 'en-US', 'America/Chicago'),
  ('10000000-0000-0000-0000-000000000009', 'seed-participant-emma-wilson', 'emma.wilson@seed.campustours.local', true, 'PARTICIPANT', 'ACTIVE', 'ADULT', 'Emma', 'Wilson', 'Emma Wilson', 'en-US', 'America/New_York'),
  ('10000000-0000-0000-0000-000000000010', 'seed-admin-olivia-martin', 'olivia.martin@seed.campustours.local', true, NULL, 'ACTIVE', 'ADULT', 'Olivia', 'Martin', 'Olivia Martin', 'en-US', 'America/Los_Angeles')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role) VALUES
  ('10000000-0000-0000-0000-000000000001', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000002', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000003', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000004', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000005', 'PARTICIPANT'),
  ('10000000-0000-0000-0000-000000000006', 'PARTICIPANT'),
  ('10000000-0000-0000-0000-000000000007', 'PARTICIPANT'),
  ('10000000-0000-0000-0000-000000000008', 'PARTICIPANT'),
  ('10000000-0000-0000-0000-000000000009', 'PARTICIPANT'),
  ('10000000-0000-0000-0000-000000000010', 'ADMIN')
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO guide_profiles (
  user_id,
  university_id,
  major,
  class_year,
  bio,
  languages,
  specialties,
  application_status,
  verification_status,
  base_price_cents,
  currency,
  approved_at
)
SELECT
  seed.user_id,
  university.id,
  seed.major,
  seed.class_year,
  seed.bio,
  seed.languages::jsonb,
  seed.specialties::jsonb,
  'APPROVED'::guide_application_status,
  'VERIFIED'::guide_verification_status,
  seed.base_price_cents,
  'USD',
  now()
FROM (
  VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid, 'ucla', 'Computer Science', '2027', 'UCLA computer science student who loves showing visitors the campus community.', '["en-US","es"]', '["GENERAL_CAMPUS","MAJOR_SPECIFIC"]', 4200::bigint),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'stanford', 'Mechanical Engineering', '2026', 'Stanford student guide focused on labs, design spaces, and student projects.', '["en-US","ko"]', '["GENERAL_CAMPUS","DORM_HOUSING"]', 4500::bigint),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'nyu', 'International Relations', '2028', 'NYU student sharing an international student perspective on life in New York City.', '["en-US","hi"]', '["INTERNATIONAL_STUDENT","DINING_STUDENT_LIFE"]', 4000::bigint),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'uchicago', 'Economics', '2027', 'UChicago guide who enjoys discussing campus traditions, academics, and study culture.', '["en-US"]', '["GENERAL_CAMPUS","FRESHMAN"]', 3800::bigint)
) AS seed(user_id, university_slug, major, class_year, bio, languages, specialties, base_price_cents)
JOIN universities university ON university.slug = seed.university_slug
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO participant_profiles (
  user_id,
  participant_type,
  grade_level,
  intended_major,
  interests,
  guardian_required
) VALUES
  ('10000000-0000-0000-0000-000000000005', 'PROSPECTIVE', '11', 'Computer Science', '["GENERAL_CAMPUS","MAJOR_SPECIFIC"]'::jsonb, false),
  ('10000000-0000-0000-0000-000000000006', 'TRANSFER', NULL, 'Business', '["TRANSFER","DINING_STUDENT_LIFE"]'::jsonb, false),
  ('10000000-0000-0000-0000-000000000007', 'INTERNATIONAL', '12', 'International Relations', '["INTERNATIONAL_STUDENT"]'::jsonb, false),
  ('10000000-0000-0000-0000-000000000008', 'PROSPECTIVE', '10', NULL, '["DORM_HOUSING","FRESHMAN"]'::jsonb, false),
  ('10000000-0000-0000-0000-000000000009', 'PARENT', NULL, NULL, '["PARENT_FOCUSED"]'::jsonb, false)
ON CONFLICT (user_id) DO NOTHING;

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

-- Every demo guide is available Monday–Friday, 10:00–14:00 locally.
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
  seed.day_of_week,
  '10:00'::time,
  240,
  seed.timezone,
  CURRENT_DATE,
  true
FROM (
  VALUES
    ('20000000-0000-0000-0000-000000000001'::uuid, 'seed-guide-ava-rivera', 'America/Los_Angeles', 1::smallint),
    ('20000000-0000-0000-0000-000000000002'::uuid, 'seed-guide-ava-rivera', 'America/Los_Angeles', 2::smallint),
    ('20000000-0000-0000-0000-000000000003'::uuid, 'seed-guide-ava-rivera', 'America/Los_Angeles', 3::smallint),
    ('20000000-0000-0000-0000-000000000004'::uuid, 'seed-guide-ava-rivera', 'America/Los_Angeles', 4::smallint),
    ('20000000-0000-0000-0000-000000000005'::uuid, 'seed-guide-ava-rivera', 'America/Los_Angeles', 5::smallint),
    ('20000000-0000-0000-0000-000000000006'::uuid, 'seed-guide-noah-kim', 'America/Los_Angeles', 1::smallint),
    ('20000000-0000-0000-0000-000000000007'::uuid, 'seed-guide-noah-kim', 'America/Los_Angeles', 2::smallint),
    ('20000000-0000-0000-0000-000000000008'::uuid, 'seed-guide-noah-kim', 'America/Los_Angeles', 3::smallint),
    ('20000000-0000-0000-0000-000000000009'::uuid, 'seed-guide-noah-kim', 'America/Los_Angeles', 4::smallint),
    ('20000000-0000-0000-0000-000000000010'::uuid, 'seed-guide-noah-kim', 'America/Los_Angeles', 5::smallint),
    ('20000000-0000-0000-0000-000000000011'::uuid, 'seed-guide-priya-shah', 'America/New_York', 1::smallint),
    ('20000000-0000-0000-0000-000000000012'::uuid, 'seed-guide-priya-shah', 'America/New_York', 2::smallint),
    ('20000000-0000-0000-0000-000000000013'::uuid, 'seed-guide-priya-shah', 'America/New_York', 3::smallint),
    ('20000000-0000-0000-0000-000000000014'::uuid, 'seed-guide-priya-shah', 'America/New_York', 4::smallint),
    ('20000000-0000-0000-0000-000000000015'::uuid, 'seed-guide-priya-shah', 'America/New_York', 5::smallint),
    ('20000000-0000-0000-0000-000000000016'::uuid, 'seed-guide-ethan-brooks', 'America/Chicago', 1::smallint),
    ('20000000-0000-0000-0000-000000000017'::uuid, 'seed-guide-ethan-brooks', 'America/Chicago', 2::smallint),
    ('20000000-0000-0000-0000-000000000018'::uuid, 'seed-guide-ethan-brooks', 'America/Chicago', 3::smallint),
    ('20000000-0000-0000-0000-000000000019'::uuid, 'seed-guide-ethan-brooks', 'America/Chicago', 4::smallint),
    ('20000000-0000-0000-0000-000000000020'::uuid, 'seed-guide-ethan-brooks', 'America/Chicago', 5::smallint)
) AS seed(rule_id, guide_subject, timezone, day_of_week)
JOIN users guide_user ON guide_user.oidc_subject = seed.guide_subject
JOIN guide_profiles guide_profile ON guide_profile.user_id = guide_user.id
ON CONFLICT (id) DO NOTHING;

-- Seed the derived occurrence cache for the current booking window. The availability
-- service can safely replace these rows from the persisted rules at any later time.
WITH seed_rules AS (
  SELECT
    id AS rule_id,
    guide_id,
    day_of_week,
    start_local,
    window_min,
    timezone
  FROM guide_availability_rules
  WHERE id BETWEEN
    '20000000-0000-0000-0000-000000000001'::uuid AND
    '20000000-0000-0000-0000-000000000020'::uuid
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

INSERT INTO tour_offerings (
  guide_id,
  university_id,
  title,
  slug,
  description,
  topic,
  duration_min,
  price_cents,
  currency,
  languages,
  status
)
SELECT
  guide_profile.id,
  university.id,
  seed.title,
  seed.slug,
  seed.description,
  seed.topic::tour_topic,
  seed.duration_min,
  seed.price_cents,
  'USD',
  seed.languages::jsonb,
  'ACTIVE'::tour_status
FROM (
  VALUES
    ('seed-guide-ava-rivera', 'ucla', 'Campus life and hidden study spots', 'campus-life-hidden-study-spots', 'Explore UCLA study spaces, student traditions, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 60, 4200, '["en-US","es"]'),
    ('seed-guide-ava-rivera', 'ucla', 'Computer science pathways at UCLA', 'computer-science-pathways', 'See where computer science students learn, build projects, and find community at UCLA.', 'MAJOR_SPECIFIC', 45, 3600, '["en-US","es"]'),
    ('seed-guide-ava-rivera', 'ucla', 'Dorms and first-year living', 'dorms-first-year-living', 'A practical look at UCLA residence halls, dining, and the first-year student experience.', 'DORM_HOUSING', 30, 2800, '["en-US"]'),
    ('seed-guide-noah-kim', 'stanford', 'Engineering labs and maker spaces', 'engineering-labs-maker-spaces', 'Visit Stanford engineering spaces and hear how students collaborate on hands-on projects.', 'MAJOR_SPECIFIC', 60, 4500, '["en-US","ko"]'),
    ('seed-guide-noah-kim', 'stanford', 'Stanford campus essentials for freshmen', 'campus-essentials-freshmen', 'A first-time visitor tour covering landmarks, resources, and day-to-day campus navigation.', 'FRESHMAN', 45, 3500, '["en-US"]'),
    ('seed-guide-priya-shah', 'nyu', 'International student life in New York', 'international-student-life-new-york', 'Learn what it is like to arrive, study, and build community as an international student at NYU.', 'INTERNATIONAL_STUDENT', 60, 4000, '["en-US","hi"]'),
    ('seed-guide-priya-shah', 'nyu', 'Dining and student life around NYU', 'dining-student-life-nyu', 'Explore NYU student hangouts, dining options, and the neighborhoods surrounding campus.', 'DINING_STUDENT_LIFE', 45, 3400, '["en-US"]'),
    ('seed-guide-ethan-brooks', 'uchicago', 'UChicago academics and campus traditions', 'academics-campus-traditions', 'Discover the academic culture, classic campus spaces, and traditions that shape UChicago life.', 'GENERAL_CAMPUS', 60, 3800, '["en-US"]'),
    ('seed-guide-ethan-brooks', 'uchicago', 'Your first quarter at UChicago', 'first-quarter-uchicago', 'A prospective-student tour focused on settling in, finding support, and building routines.', 'FRESHMAN', 30, 2600, '["en-US"]'),
    ('seed-guide-ethan-brooks', 'uchicago', 'Parent guide to UChicago campus life', 'parent-guide-campus-life', 'A parent-focused overview of housing, student services, safety, and campus support.', 'PARENT_FOCUSED', 45, 3200, '["en-US"]')
) AS seed(guide_subject, university_slug, title, slug, description, topic, duration_min, price_cents, languages)
JOIN users guide_user ON guide_user.oidc_subject = seed.guide_subject
JOIN guide_profiles guide_profile ON guide_profile.user_id = guide_user.id
JOIN universities university ON university.slug = seed.university_slug
ON CONFLICT (guide_id, slug) DO NOTHING;
