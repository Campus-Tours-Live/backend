-- =====================================================================
-- V3 — Local MVP demo accounts
--
-- These are fictional, non-loginable fixture identities for local development.
-- Their OIDC subjects are deliberately synthetic: real users continue to be
-- provisioned only through the Google OIDC sign-up flow.
--
-- The four approved guide profiles provide stable supply-side records for
-- later demo tour-offering seeds. The remaining accounts cover participant
-- and admin role-dependent development paths.
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
