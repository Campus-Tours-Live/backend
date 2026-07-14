-- =====================================================================
-- V3 -- Local MVP demo data
--
-- Fictional, non-loginable identities and discoverable tour data for local
-- development. Synthetic OIDC subjects ensure real users are still created
-- exclusively through the Google OIDC flow.
--
-- 20 approved guides across 5 schools (UCLA, Stanford, NYU, UChicago, MIT),
-- each with 6 ACTIVE tour offerings (120 total) covering varied topics,
-- durations, and languages, plus weekday guide availability (Mon-Fri,
-- 10:00-14:00 local) and 30 days of materialized occurrences so participant
-- slot reads work immediately.
--
-- No participant/admin rows are seeded: the public tour catalog (GET /tours)
-- reads only offerings + approved guides + universities, and the acting
-- participant is the live OIDC-authenticated user, not a pre-seeded account.
--
-- This file is machine-generated (scripts, seed=80) as STATIC SQL: the variety
-- is fixed at generation time, so the migration is fully reproducible.
-- =====================================================================

INSERT INTO users (
  id, oidc_subject, email, email_verified, last_active_role,
  account_status, age_band, first_name, last_name, display_name,
  preferred_language, timezone
) VALUES
  ('10000000-0000-0000-0000-000000000001', 'seed-guide-lucas-santos', 'lucas.santos@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Lucas', 'Santos', 'Lucas Santos', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-000000000002', 'seed-guide-priya-shah', 'priya.shah@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Priya', 'Shah', 'Priya Shah', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-000000000003', 'seed-guide-wei-zhang', 'wei.zhang@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Wei', 'Zhang', 'Wei Zhang', 'en-US', 'America/New_York'),
  ('10000000-0000-0000-0000-000000000004', 'seed-guide-noah-kim', 'noah.kim@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Noah', 'Kim', 'Noah Kim', 'en-US', 'America/Chicago'),
  ('10000000-0000-0000-0000-000000000005', 'seed-guide-jordan-lee', 'jordan.lee@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Jordan', 'Lee', 'Jordan Lee', 'en-US', 'America/New_York'),
  ('10000000-0000-0000-0000-000000000006', 'seed-guide-aisha-khan', 'aisha.khan@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Aisha', 'Khan', 'Aisha Khan', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-000000000007', 'seed-guide-mia-chen', 'mia.chen@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Mia', 'Chen', 'Mia Chen', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-000000000008', 'seed-guide-ethan-brooks', 'ethan.brooks@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Ethan', 'Brooks', 'Ethan Brooks', 'en-US', 'America/New_York'),
  ('10000000-0000-0000-0000-000000000009', 'seed-guide-leah-cohen', 'leah.cohen@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Leah', 'Cohen', 'Leah Cohen', 'en-US', 'America/Chicago'),
  ('10000000-0000-0000-0000-00000000000a', 'seed-guide-nina-novak', 'nina.novak@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Nina', 'Novak', 'Nina Novak', 'en-US', 'America/New_York'),
  ('10000000-0000-0000-0000-00000000000b', 'seed-guide-ava-rivera', 'ava.rivera@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Ava', 'Rivera', 'Ava Rivera', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-00000000000c', 'seed-guide-zoe-nguyen', 'zoe.nguyen@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Zoe', 'Nguyen', 'Zoe Nguyen', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-00000000000d', 'seed-guide-sofia-patel', 'sofia.patel@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Sofia', 'Patel', 'Sofia Patel', 'en-US', 'America/New_York'),
  ('10000000-0000-0000-0000-00000000000e', 'seed-guide-ravi-iyer', 'ravi.iyer@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Ravi', 'Iyer', 'Ravi Iyer', 'en-US', 'America/Chicago'),
  ('10000000-0000-0000-0000-00000000000f', 'seed-guide-liam-walsh', 'liam.walsh@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Liam', 'Walsh', 'Liam Walsh', 'en-US', 'America/New_York'),
  ('10000000-0000-0000-0000-000000000010', 'seed-guide-diego-morales', 'diego.morales@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Diego', 'Morales', 'Diego Morales', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-000000000011', 'seed-guide-omar-haddad', 'omar.haddad@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Omar', 'Haddad', 'Omar Haddad', 'en-US', 'America/Los_Angeles'),
  ('10000000-0000-0000-0000-000000000012', 'seed-guide-kofi-mensah', 'kofi.mensah@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Kofi', 'Mensah', 'Kofi Mensah', 'en-US', 'America/New_York'),
  ('10000000-0000-0000-0000-000000000013', 'seed-guide-hana-sato', 'hana.sato@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Hana', 'Sato', 'Hana Sato', 'en-US', 'America/Chicago'),
  ('10000000-0000-0000-0000-000000000014', 'seed-guide-emma-wilson', 'emma.wilson@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', 'Emma', 'Wilson', 'Emma Wilson', 'en-US', 'America/New_York')
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role) VALUES
  ('10000000-0000-0000-0000-000000000001', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000002', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000003', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000004', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000005', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000006', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000007', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000008', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000009', 'GUIDE'),
  ('10000000-0000-0000-0000-00000000000a', 'GUIDE'),
  ('10000000-0000-0000-0000-00000000000b', 'GUIDE'),
  ('10000000-0000-0000-0000-00000000000c', 'GUIDE'),
  ('10000000-0000-0000-0000-00000000000d', 'GUIDE'),
  ('10000000-0000-0000-0000-00000000000e', 'GUIDE'),
  ('10000000-0000-0000-0000-00000000000f', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000010', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000011', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000012', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000013', 'GUIDE'),
  ('10000000-0000-0000-0000-000000000014', 'GUIDE')
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO guide_profiles (
  user_id, university_id, major, class_year, bio, languages, specialties,
  application_status, verification_status, base_price_cents, currency, approved_at
)
SELECT
  seed.user_id, university.id, seed.major, seed.class_year, seed.bio,
  seed.languages::jsonb, seed.specialties::jsonb,
  'APPROVED'::guide_application_status, 'VERIFIED'::guide_verification_status,
  seed.base_price_cents, 'USD', now()
FROM (
  VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid, 'ucla', 'Biology', '2029', 'UCLA Biology student (class of 2029) who loves showing visitors around campus.', '["zh", "de"]', '["MAJOR_SPECIFIC", "DINING_STUDENT_LIFE"]', 4100::bigint),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'stanford', 'Public Health', '2027', 'Stanford Public Health student (class of 2027) who loves showing visitors around campus.', '["ko", "hi", "pt"]', '["DORM_HOUSING", "DINING_STUDENT_LIFE"]', 3300::bigint),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'nyu', 'Computer Science', '2026', 'NYU Computer Science student (class of 2026) who loves showing visitors around campus.', '["pt"]', '["INTERNATIONAL_STUDENT", "DINING_STUDENT_LIFE"]', 5900::bigint),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'uchicago', 'Data Science', '2026', 'UChicago Data Science student (class of 2026) who loves showing visitors around campus.', '["ar"]', '["DORM_HOUSING", "TRANSFER"]', 5000::bigint),
    ('10000000-0000-0000-0000-000000000005'::uuid, 'mit', 'Neuroscience', '2028', 'MIT Neuroscience student (class of 2028) who loves showing visitors around campus.', '["en-US", "ar"]', '["MAJOR_SPECIFIC", "DINING_STUDENT_LIFE"]', 4000::bigint),
    ('10000000-0000-0000-0000-000000000006'::uuid, 'ucla', 'Neuroscience', '2028', 'UCLA Neuroscience student (class of 2028) who loves showing visitors around campus.', '["en-US", "pt", "de"]', '["FRESHMAN", "DORM_HOUSING"]', 3300::bigint),
    ('10000000-0000-0000-0000-000000000007'::uuid, 'stanford', 'Economics', '2026', 'Stanford Economics student (class of 2026) who loves showing visitors around campus.', '["en-US"]', '["INTERNATIONAL_STUDENT", "DINING_STUDENT_LIFE"]', 4500::bigint),
    ('10000000-0000-0000-0000-000000000008'::uuid, 'nyu', 'Computer Science', '2026', 'NYU Computer Science student (class of 2026) who loves showing visitors around campus.', '["ar"]', '["DINING_STUDENT_LIFE", "FRESHMAN"]', 4500::bigint),
    ('10000000-0000-0000-0000-000000000009'::uuid, 'uchicago', 'Architecture', '2027', 'UChicago Architecture student (class of 2027) who loves showing visitors around campus.', '["ko"]', '["FRESHMAN", "MAJOR_SPECIFIC"]', 5000::bigint),
    ('10000000-0000-0000-0000-00000000000a'::uuid, 'mit', 'Biology', '2026', 'MIT Biology student (class of 2026) who loves showing visitors around campus.', '["zh"]', '["FRESHMAN", "TRANSFER"]', 3800::bigint),
    ('10000000-0000-0000-0000-00000000000b'::uuid, 'ucla', 'Environmental Science', '2028', 'UCLA Environmental Science student (class of 2028) who loves showing visitors around campus.', '["pt"]', '["TRANSFER", "DORM_HOUSING"]', 4800::bigint),
    ('10000000-0000-0000-0000-00000000000c'::uuid, 'stanford', 'Psychology', '2028', 'Stanford Psychology student (class of 2028) who loves showing visitors around campus.', '["pt", "hi"]', '["DORM_HOUSING", "INTERNATIONAL_STUDENT"]', 3000::bigint),
    ('10000000-0000-0000-0000-00000000000d'::uuid, 'nyu', 'English', '2028', 'NYU English student (class of 2028) who loves showing visitors around campus.', '["ja", "en-US"]', '["MAJOR_SPECIFIC", "PARENT_FOCUSED"]', 6000::bigint),
    ('10000000-0000-0000-0000-00000000000e'::uuid, 'uchicago', 'International Relations', '2028', 'UChicago International Relations student (class of 2028) who loves showing visitors around campus.', '["pt"]', '["INTERNATIONAL_STUDENT", "DINING_STUDENT_LIFE"]', 4000::bigint),
    ('10000000-0000-0000-0000-00000000000f'::uuid, 'mit', 'Design', '2028', 'MIT Design student (class of 2028) who loves showing visitors around campus.', '["de"]', '["DINING_STUDENT_LIFE", "GENERAL_CAMPUS"]', 3600::bigint),
    ('10000000-0000-0000-0000-000000000010'::uuid, 'ucla', 'Biology', '2027', 'UCLA Biology student (class of 2027) who loves showing visitors around campus.', '["es"]', '["TRANSFER", "DINING_STUDENT_LIFE"]', 5700::bigint),
    ('10000000-0000-0000-0000-000000000011'::uuid, 'stanford', 'Computer Science', '2029', 'Stanford Computer Science student (class of 2029) who loves showing visitors around campus.', '["es"]', '["DORM_HOUSING", "TRANSFER"]', 4900::bigint),
    ('10000000-0000-0000-0000-000000000012'::uuid, 'nyu', 'Mechanical Engineering', '2027', 'NYU Mechanical Engineering student (class of 2027) who loves showing visitors around campus.', '["fr"]', '["PARENT_FOCUSED", "GENERAL_CAMPUS"]', 4000::bigint),
    ('10000000-0000-0000-0000-000000000013'::uuid, 'uchicago', 'Psychology', '2027', 'UChicago Psychology student (class of 2027) who loves showing visitors around campus.', '["ja"]', '["FRESHMAN", "PARENT_FOCUSED"]', 4200::bigint),
    ('10000000-0000-0000-0000-000000000014'::uuid, 'mit', 'Film & Media', '2026', 'MIT Film & Media student (class of 2026) who loves showing visitors around campus.', '["ar", "ja"]', '["GENERAL_CAMPUS", "PARENT_FOCUSED"]', 3200::bigint)
) AS seed(user_id, university_slug, major, class_year, bio, languages, specialties, base_price_cents)
JOIN universities university ON university.slug = seed.university_slug
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO guide_booking_settings (
  guide_id, acceptance_mode, response_deadline_min, min_notice_min, max_advance_days,
  buffer_before_min, buffer_after_min, durations_offered, timezone
)
SELECT
  guide_profile.id, 'MANUAL'::acceptance_mode, 90, 60, 30, 0, 15,
  '[30,45,60,90]'::jsonb, seed.timezone
FROM (
  VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid, 'America/Los_Angeles'),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'America/Los_Angeles'),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'America/New_York'),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'America/Chicago'),
    ('10000000-0000-0000-0000-000000000005'::uuid, 'America/New_York'),
    ('10000000-0000-0000-0000-000000000006'::uuid, 'America/Los_Angeles'),
    ('10000000-0000-0000-0000-000000000007'::uuid, 'America/Los_Angeles'),
    ('10000000-0000-0000-0000-000000000008'::uuid, 'America/New_York'),
    ('10000000-0000-0000-0000-000000000009'::uuid, 'America/Chicago'),
    ('10000000-0000-0000-0000-00000000000a'::uuid, 'America/New_York'),
    ('10000000-0000-0000-0000-00000000000b'::uuid, 'America/Los_Angeles'),
    ('10000000-0000-0000-0000-00000000000c'::uuid, 'America/Los_Angeles'),
    ('10000000-0000-0000-0000-00000000000d'::uuid, 'America/New_York'),
    ('10000000-0000-0000-0000-00000000000e'::uuid, 'America/Chicago'),
    ('10000000-0000-0000-0000-00000000000f'::uuid, 'America/New_York'),
    ('10000000-0000-0000-0000-000000000010'::uuid, 'America/Los_Angeles'),
    ('10000000-0000-0000-0000-000000000011'::uuid, 'America/Los_Angeles'),
    ('10000000-0000-0000-0000-000000000012'::uuid, 'America/New_York'),
    ('10000000-0000-0000-0000-000000000013'::uuid, 'America/Chicago'),
    ('10000000-0000-0000-0000-000000000014'::uuid, 'America/New_York')
) AS seed(user_id, timezone)
JOIN guide_profiles guide_profile ON guide_profile.user_id = seed.user_id
ON CONFLICT (guide_id) DO NOTHING;

-- Every demo guide is available Monday-Friday, 10:00-14:00 locally.
-- PostgreSQL's extract(dow) convention matches the API: Sunday=0 .. Saturday=6.
INSERT INTO guide_availability_rules (
  id, guide_id, day_of_week, start_local, window_min, timezone, effective_from, active
)
SELECT
  seed.rule_id, guide_profile.id, seed.day_of_week, '10:00'::time, 240,
  seed.timezone, CURRENT_DATE, true
FROM (
  VALUES
    ('20000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000001'::uuid, 'America/Los_Angeles', 1::smallint),
    ('20000000-0000-0000-0000-000000000002'::uuid, '10000000-0000-0000-0000-000000000001'::uuid, 'America/Los_Angeles', 2::smallint),
    ('20000000-0000-0000-0000-000000000003'::uuid, '10000000-0000-0000-0000-000000000001'::uuid, 'America/Los_Angeles', 3::smallint),
    ('20000000-0000-0000-0000-000000000004'::uuid, '10000000-0000-0000-0000-000000000001'::uuid, 'America/Los_Angeles', 4::smallint),
    ('20000000-0000-0000-0000-000000000005'::uuid, '10000000-0000-0000-0000-000000000001'::uuid, 'America/Los_Angeles', 5::smallint),
    ('20000000-0000-0000-0000-000000000006'::uuid, '10000000-0000-0000-0000-000000000002'::uuid, 'America/Los_Angeles', 1::smallint),
    ('20000000-0000-0000-0000-000000000007'::uuid, '10000000-0000-0000-0000-000000000002'::uuid, 'America/Los_Angeles', 2::smallint),
    ('20000000-0000-0000-0000-000000000008'::uuid, '10000000-0000-0000-0000-000000000002'::uuid, 'America/Los_Angeles', 3::smallint),
    ('20000000-0000-0000-0000-000000000009'::uuid, '10000000-0000-0000-0000-000000000002'::uuid, 'America/Los_Angeles', 4::smallint),
    ('20000000-0000-0000-0000-00000000000a'::uuid, '10000000-0000-0000-0000-000000000002'::uuid, 'America/Los_Angeles', 5::smallint),
    ('20000000-0000-0000-0000-00000000000b'::uuid, '10000000-0000-0000-0000-000000000003'::uuid, 'America/New_York', 1::smallint),
    ('20000000-0000-0000-0000-00000000000c'::uuid, '10000000-0000-0000-0000-000000000003'::uuid, 'America/New_York', 2::smallint),
    ('20000000-0000-0000-0000-00000000000d'::uuid, '10000000-0000-0000-0000-000000000003'::uuid, 'America/New_York', 3::smallint),
    ('20000000-0000-0000-0000-00000000000e'::uuid, '10000000-0000-0000-0000-000000000003'::uuid, 'America/New_York', 4::smallint),
    ('20000000-0000-0000-0000-00000000000f'::uuid, '10000000-0000-0000-0000-000000000003'::uuid, 'America/New_York', 5::smallint),
    ('20000000-0000-0000-0000-000000000010'::uuid, '10000000-0000-0000-0000-000000000004'::uuid, 'America/Chicago', 1::smallint),
    ('20000000-0000-0000-0000-000000000011'::uuid, '10000000-0000-0000-0000-000000000004'::uuid, 'America/Chicago', 2::smallint),
    ('20000000-0000-0000-0000-000000000012'::uuid, '10000000-0000-0000-0000-000000000004'::uuid, 'America/Chicago', 3::smallint),
    ('20000000-0000-0000-0000-000000000013'::uuid, '10000000-0000-0000-0000-000000000004'::uuid, 'America/Chicago', 4::smallint),
    ('20000000-0000-0000-0000-000000000014'::uuid, '10000000-0000-0000-0000-000000000004'::uuid, 'America/Chicago', 5::smallint),
    ('20000000-0000-0000-0000-000000000015'::uuid, '10000000-0000-0000-0000-000000000005'::uuid, 'America/New_York', 1::smallint),
    ('20000000-0000-0000-0000-000000000016'::uuid, '10000000-0000-0000-0000-000000000005'::uuid, 'America/New_York', 2::smallint),
    ('20000000-0000-0000-0000-000000000017'::uuid, '10000000-0000-0000-0000-000000000005'::uuid, 'America/New_York', 3::smallint),
    ('20000000-0000-0000-0000-000000000018'::uuid, '10000000-0000-0000-0000-000000000005'::uuid, 'America/New_York', 4::smallint),
    ('20000000-0000-0000-0000-000000000019'::uuid, '10000000-0000-0000-0000-000000000005'::uuid, 'America/New_York', 5::smallint),
    ('20000000-0000-0000-0000-00000000001a'::uuid, '10000000-0000-0000-0000-000000000006'::uuid, 'America/Los_Angeles', 1::smallint),
    ('20000000-0000-0000-0000-00000000001b'::uuid, '10000000-0000-0000-0000-000000000006'::uuid, 'America/Los_Angeles', 2::smallint),
    ('20000000-0000-0000-0000-00000000001c'::uuid, '10000000-0000-0000-0000-000000000006'::uuid, 'America/Los_Angeles', 3::smallint),
    ('20000000-0000-0000-0000-00000000001d'::uuid, '10000000-0000-0000-0000-000000000006'::uuid, 'America/Los_Angeles', 4::smallint),
    ('20000000-0000-0000-0000-00000000001e'::uuid, '10000000-0000-0000-0000-000000000006'::uuid, 'America/Los_Angeles', 5::smallint),
    ('20000000-0000-0000-0000-00000000001f'::uuid, '10000000-0000-0000-0000-000000000007'::uuid, 'America/Los_Angeles', 1::smallint),
    ('20000000-0000-0000-0000-000000000020'::uuid, '10000000-0000-0000-0000-000000000007'::uuid, 'America/Los_Angeles', 2::smallint),
    ('20000000-0000-0000-0000-000000000021'::uuid, '10000000-0000-0000-0000-000000000007'::uuid, 'America/Los_Angeles', 3::smallint),
    ('20000000-0000-0000-0000-000000000022'::uuid, '10000000-0000-0000-0000-000000000007'::uuid, 'America/Los_Angeles', 4::smallint),
    ('20000000-0000-0000-0000-000000000023'::uuid, '10000000-0000-0000-0000-000000000007'::uuid, 'America/Los_Angeles', 5::smallint),
    ('20000000-0000-0000-0000-000000000024'::uuid, '10000000-0000-0000-0000-000000000008'::uuid, 'America/New_York', 1::smallint),
    ('20000000-0000-0000-0000-000000000025'::uuid, '10000000-0000-0000-0000-000000000008'::uuid, 'America/New_York', 2::smallint),
    ('20000000-0000-0000-0000-000000000026'::uuid, '10000000-0000-0000-0000-000000000008'::uuid, 'America/New_York', 3::smallint),
    ('20000000-0000-0000-0000-000000000027'::uuid, '10000000-0000-0000-0000-000000000008'::uuid, 'America/New_York', 4::smallint),
    ('20000000-0000-0000-0000-000000000028'::uuid, '10000000-0000-0000-0000-000000000008'::uuid, 'America/New_York', 5::smallint),
    ('20000000-0000-0000-0000-000000000029'::uuid, '10000000-0000-0000-0000-000000000009'::uuid, 'America/Chicago', 1::smallint),
    ('20000000-0000-0000-0000-00000000002a'::uuid, '10000000-0000-0000-0000-000000000009'::uuid, 'America/Chicago', 2::smallint),
    ('20000000-0000-0000-0000-00000000002b'::uuid, '10000000-0000-0000-0000-000000000009'::uuid, 'America/Chicago', 3::smallint),
    ('20000000-0000-0000-0000-00000000002c'::uuid, '10000000-0000-0000-0000-000000000009'::uuid, 'America/Chicago', 4::smallint),
    ('20000000-0000-0000-0000-00000000002d'::uuid, '10000000-0000-0000-0000-000000000009'::uuid, 'America/Chicago', 5::smallint),
    ('20000000-0000-0000-0000-00000000002e'::uuid, '10000000-0000-0000-0000-00000000000a'::uuid, 'America/New_York', 1::smallint),
    ('20000000-0000-0000-0000-00000000002f'::uuid, '10000000-0000-0000-0000-00000000000a'::uuid, 'America/New_York', 2::smallint),
    ('20000000-0000-0000-0000-000000000030'::uuid, '10000000-0000-0000-0000-00000000000a'::uuid, 'America/New_York', 3::smallint),
    ('20000000-0000-0000-0000-000000000031'::uuid, '10000000-0000-0000-0000-00000000000a'::uuid, 'America/New_York', 4::smallint),
    ('20000000-0000-0000-0000-000000000032'::uuid, '10000000-0000-0000-0000-00000000000a'::uuid, 'America/New_York', 5::smallint),
    ('20000000-0000-0000-0000-000000000033'::uuid, '10000000-0000-0000-0000-00000000000b'::uuid, 'America/Los_Angeles', 1::smallint),
    ('20000000-0000-0000-0000-000000000034'::uuid, '10000000-0000-0000-0000-00000000000b'::uuid, 'America/Los_Angeles', 2::smallint),
    ('20000000-0000-0000-0000-000000000035'::uuid, '10000000-0000-0000-0000-00000000000b'::uuid, 'America/Los_Angeles', 3::smallint),
    ('20000000-0000-0000-0000-000000000036'::uuid, '10000000-0000-0000-0000-00000000000b'::uuid, 'America/Los_Angeles', 4::smallint),
    ('20000000-0000-0000-0000-000000000037'::uuid, '10000000-0000-0000-0000-00000000000b'::uuid, 'America/Los_Angeles', 5::smallint),
    ('20000000-0000-0000-0000-000000000038'::uuid, '10000000-0000-0000-0000-00000000000c'::uuid, 'America/Los_Angeles', 1::smallint),
    ('20000000-0000-0000-0000-000000000039'::uuid, '10000000-0000-0000-0000-00000000000c'::uuid, 'America/Los_Angeles', 2::smallint),
    ('20000000-0000-0000-0000-00000000003a'::uuid, '10000000-0000-0000-0000-00000000000c'::uuid, 'America/Los_Angeles', 3::smallint),
    ('20000000-0000-0000-0000-00000000003b'::uuid, '10000000-0000-0000-0000-00000000000c'::uuid, 'America/Los_Angeles', 4::smallint),
    ('20000000-0000-0000-0000-00000000003c'::uuid, '10000000-0000-0000-0000-00000000000c'::uuid, 'America/Los_Angeles', 5::smallint),
    ('20000000-0000-0000-0000-00000000003d'::uuid, '10000000-0000-0000-0000-00000000000d'::uuid, 'America/New_York', 1::smallint),
    ('20000000-0000-0000-0000-00000000003e'::uuid, '10000000-0000-0000-0000-00000000000d'::uuid, 'America/New_York', 2::smallint),
    ('20000000-0000-0000-0000-00000000003f'::uuid, '10000000-0000-0000-0000-00000000000d'::uuid, 'America/New_York', 3::smallint),
    ('20000000-0000-0000-0000-000000000040'::uuid, '10000000-0000-0000-0000-00000000000d'::uuid, 'America/New_York', 4::smallint),
    ('20000000-0000-0000-0000-000000000041'::uuid, '10000000-0000-0000-0000-00000000000d'::uuid, 'America/New_York', 5::smallint),
    ('20000000-0000-0000-0000-000000000042'::uuid, '10000000-0000-0000-0000-00000000000e'::uuid, 'America/Chicago', 1::smallint),
    ('20000000-0000-0000-0000-000000000043'::uuid, '10000000-0000-0000-0000-00000000000e'::uuid, 'America/Chicago', 2::smallint),
    ('20000000-0000-0000-0000-000000000044'::uuid, '10000000-0000-0000-0000-00000000000e'::uuid, 'America/Chicago', 3::smallint),
    ('20000000-0000-0000-0000-000000000045'::uuid, '10000000-0000-0000-0000-00000000000e'::uuid, 'America/Chicago', 4::smallint),
    ('20000000-0000-0000-0000-000000000046'::uuid, '10000000-0000-0000-0000-00000000000e'::uuid, 'America/Chicago', 5::smallint),
    ('20000000-0000-0000-0000-000000000047'::uuid, '10000000-0000-0000-0000-00000000000f'::uuid, 'America/New_York', 1::smallint),
    ('20000000-0000-0000-0000-000000000048'::uuid, '10000000-0000-0000-0000-00000000000f'::uuid, 'America/New_York', 2::smallint),
    ('20000000-0000-0000-0000-000000000049'::uuid, '10000000-0000-0000-0000-00000000000f'::uuid, 'America/New_York', 3::smallint),
    ('20000000-0000-0000-0000-00000000004a'::uuid, '10000000-0000-0000-0000-00000000000f'::uuid, 'America/New_York', 4::smallint),
    ('20000000-0000-0000-0000-00000000004b'::uuid, '10000000-0000-0000-0000-00000000000f'::uuid, 'America/New_York', 5::smallint),
    ('20000000-0000-0000-0000-00000000004c'::uuid, '10000000-0000-0000-0000-000000000010'::uuid, 'America/Los_Angeles', 1::smallint),
    ('20000000-0000-0000-0000-00000000004d'::uuid, '10000000-0000-0000-0000-000000000010'::uuid, 'America/Los_Angeles', 2::smallint),
    ('20000000-0000-0000-0000-00000000004e'::uuid, '10000000-0000-0000-0000-000000000010'::uuid, 'America/Los_Angeles', 3::smallint),
    ('20000000-0000-0000-0000-00000000004f'::uuid, '10000000-0000-0000-0000-000000000010'::uuid, 'America/Los_Angeles', 4::smallint),
    ('20000000-0000-0000-0000-000000000050'::uuid, '10000000-0000-0000-0000-000000000010'::uuid, 'America/Los_Angeles', 5::smallint),
    ('20000000-0000-0000-0000-000000000051'::uuid, '10000000-0000-0000-0000-000000000011'::uuid, 'America/Los_Angeles', 1::smallint),
    ('20000000-0000-0000-0000-000000000052'::uuid, '10000000-0000-0000-0000-000000000011'::uuid, 'America/Los_Angeles', 2::smallint),
    ('20000000-0000-0000-0000-000000000053'::uuid, '10000000-0000-0000-0000-000000000011'::uuid, 'America/Los_Angeles', 3::smallint),
    ('20000000-0000-0000-0000-000000000054'::uuid, '10000000-0000-0000-0000-000000000011'::uuid, 'America/Los_Angeles', 4::smallint),
    ('20000000-0000-0000-0000-000000000055'::uuid, '10000000-0000-0000-0000-000000000011'::uuid, 'America/Los_Angeles', 5::smallint),
    ('20000000-0000-0000-0000-000000000056'::uuid, '10000000-0000-0000-0000-000000000012'::uuid, 'America/New_York', 1::smallint),
    ('20000000-0000-0000-0000-000000000057'::uuid, '10000000-0000-0000-0000-000000000012'::uuid, 'America/New_York', 2::smallint),
    ('20000000-0000-0000-0000-000000000058'::uuid, '10000000-0000-0000-0000-000000000012'::uuid, 'America/New_York', 3::smallint),
    ('20000000-0000-0000-0000-000000000059'::uuid, '10000000-0000-0000-0000-000000000012'::uuid, 'America/New_York', 4::smallint),
    ('20000000-0000-0000-0000-00000000005a'::uuid, '10000000-0000-0000-0000-000000000012'::uuid, 'America/New_York', 5::smallint),
    ('20000000-0000-0000-0000-00000000005b'::uuid, '10000000-0000-0000-0000-000000000013'::uuid, 'America/Chicago', 1::smallint),
    ('20000000-0000-0000-0000-00000000005c'::uuid, '10000000-0000-0000-0000-000000000013'::uuid, 'America/Chicago', 2::smallint),
    ('20000000-0000-0000-0000-00000000005d'::uuid, '10000000-0000-0000-0000-000000000013'::uuid, 'America/Chicago', 3::smallint),
    ('20000000-0000-0000-0000-00000000005e'::uuid, '10000000-0000-0000-0000-000000000013'::uuid, 'America/Chicago', 4::smallint),
    ('20000000-0000-0000-0000-00000000005f'::uuid, '10000000-0000-0000-0000-000000000013'::uuid, 'America/Chicago', 5::smallint),
    ('20000000-0000-0000-0000-000000000060'::uuid, '10000000-0000-0000-0000-000000000014'::uuid, 'America/New_York', 1::smallint),
    ('20000000-0000-0000-0000-000000000061'::uuid, '10000000-0000-0000-0000-000000000014'::uuid, 'America/New_York', 2::smallint),
    ('20000000-0000-0000-0000-000000000062'::uuid, '10000000-0000-0000-0000-000000000014'::uuid, 'America/New_York', 3::smallint),
    ('20000000-0000-0000-0000-000000000063'::uuid, '10000000-0000-0000-0000-000000000014'::uuid, 'America/New_York', 4::smallint),
    ('20000000-0000-0000-0000-000000000064'::uuid, '10000000-0000-0000-0000-000000000014'::uuid, 'America/New_York', 5::smallint)
) AS seed(rule_id, user_id, timezone, day_of_week)
JOIN guide_profiles guide_profile ON guide_profile.user_id = seed.user_id
ON CONFLICT (id) DO NOTHING;

-- Seed the derived occurrence cache for the current booking window. The availability
-- service can safely replace these rows from the persisted rules at any later time.
WITH seed_rules AS (
  SELECT id AS rule_id, guide_id, day_of_week, start_local, window_min, timezone
  FROM guide_availability_rules
  WHERE id BETWEEN
    '20000000-0000-0000-0000-000000000001'::uuid AND
    '20000000-0000-0000-0000-000000000064'::uuid
), rule_dates AS (
  SELECT rule.*, day::date AS occurrence_date
  FROM seed_rules rule
  CROSS JOIN generate_series(CURRENT_DATE, CURRENT_DATE + 30, interval '1 day') AS day
  WHERE EXTRACT(DOW FROM day)::smallint = rule.day_of_week
), occurrences AS (
  SELECT
    rule_id, guide_id,
    ((occurrence_date + start_local) AT TIME ZONE timezone) AS start_at,
    ((occurrence_date + start_local) AT TIME ZONE timezone)
      + make_interval(mins => window_min) AS end_at
  FROM rule_dates
)
INSERT INTO guide_availability_occurrences (
  guide_id, during_start_at, during_end_at, source_rule_id
)
SELECT guide_id, start_at, end_at, rule_id
FROM occurrences
WHERE NOT EXISTS (
  SELECT 1 FROM guide_availability_occurrences existing
  WHERE existing.source_rule_id = occurrences.rule_id
    AND existing.during_start_at = occurrences.start_at
    AND existing.during_end_at = occurrences.end_at
);

INSERT INTO tour_offerings (
  guide_id, university_id, title, slug, description, topic, duration_min,
  price_cents, currency, languages, status
)
SELECT
  guide_profile.id, university.id, seed.title, seed.slug, seed.description,
  seed.topic::tour_topic, seed.duration_min, seed.price_cents, 'USD',
  seed.languages::jsonb, 'ACTIVE'::tour_status
FROM (
  VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid, 'ucla', 'Campus life and must-see spots', 'general-campus-1', 'Explore UCLA landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 60, 5400, '["zh"]'),
    ('10000000-0000-0000-0000-000000000001'::uuid, 'ucla', 'International student life', 'international-student-2', 'What it is like to arrive, study, and belong as an international student at UCLA.', 'INTERNATIONAL_STUDENT', 60, 4300, '["fr", "ja", "zh"]'),
    ('10000000-0000-0000-0000-000000000001'::uuid, 'ucla', 'Transferring in: a practical tour', 'transfer-3', 'A transfer-student view of settling in and finding your footing at UCLA.', 'TRANSFER', 45, 6000, '["es"]'),
    ('10000000-0000-0000-0000-000000000001'::uuid, 'ucla', 'A parent''s guide to campus', 'parent-focused-4', 'A parent-focused overview of UCLA housing, services, safety, and support.', 'PARENT_FOCUSED', 30, 4400, '["ja"]'),
    ('10000000-0000-0000-0000-000000000001'::uuid, 'ucla', 'Transferring in: a practical tour', 'transfer-5', 'A transfer-student view of settling in and finding your footing at UCLA.', 'TRANSFER', 60, 4000, '["es", "ja"]'),
    ('10000000-0000-0000-0000-000000000001'::uuid, 'ucla', 'Dorms and first-year living', 'dorm-housing-6', 'A practical look at UCLA residence halls, dining, and first-year living.', 'DORM_HOUSING', 60, 5600, '["zh"]'),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'stanford', 'A parent''s guide to campus', 'parent-focused-1', 'A parent-focused overview of Stanford housing, services, safety, and support.', 'PARENT_FOCUSED', 60, 4300, '["ar"]'),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'stanford', 'Transferring in: a practical tour', 'transfer-2', 'A transfer-student view of settling in and finding your footing at Stanford.', 'TRANSFER', 45, 3700, '["hi"]'),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'stanford', 'Dining and student life', 'dining-student-life-3', 'Discover Stanford student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 30, 3300, '["en-US", "ja", "es"]'),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'stanford', 'Your first year, made simple', 'freshman-4', 'A first-time visitor tour of Stanford landmarks, resources, and campus navigation.', 'FRESHMAN', 30, 4000, '["ja", "fr"]'),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'stanford', 'International student life', 'international-student-5', 'What it is like to arrive, study, and belong as an international student at Stanford.', 'INTERNATIONAL_STUDENT', 45, 3200, '["en-US"]'),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'stanford', 'Dorms and first-year living', 'dorm-housing-6', 'A practical look at Stanford residence halls, dining, and first-year living.', 'DORM_HOUSING', 90, 4100, '["es", "pt"]'),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'nyu', 'A parent''s guide to campus', 'parent-focused-1', 'A parent-focused overview of NYU housing, services, safety, and support.', 'PARENT_FOCUSED', 45, 3700, '["es", "hi"]'),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'nyu', 'Dorms and first-year living', 'dorm-housing-2', 'A practical look at NYU residence halls, dining, and first-year living.', 'DORM_HOUSING', 30, 3300, '["en-US", "ja", "ar"]'),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'nyu', 'Dorms and first-year living', 'dorm-housing-3', 'A practical look at NYU residence halls, dining, and first-year living.', 'DORM_HOUSING', 45, 3900, '["de"]'),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'nyu', 'Dorms and first-year living', 'dorm-housing-4', 'A practical look at NYU residence halls, dining, and first-year living.', 'DORM_HOUSING', 45, 4900, '["zh", "en-US", "hi"]'),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'nyu', 'A parent''s guide to campus', 'parent-focused-5', 'A parent-focused overview of NYU housing, services, safety, and support.', 'PARENT_FOCUSED', 45, 4500, '["es"]'),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'nyu', 'Dorms and first-year living', 'dorm-housing-6', 'A practical look at NYU residence halls, dining, and first-year living.', 'DORM_HOUSING', 45, 5900, '["zh"]'),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'uchicago', 'Your first year, made simple', 'freshman-1', 'A first-time visitor tour of UChicago landmarks, resources, and campus navigation.', 'FRESHMAN', 60, 5300, '["hi"]'),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'uchicago', 'International student life', 'international-student-2', 'What it is like to arrive, study, and belong as an international student at UChicago.', 'INTERNATIONAL_STUDENT', 30, 3500, '["pt", "en-US"]'),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'uchicago', 'Your first year, made simple', 'freshman-3', 'A first-time visitor tour of UChicago landmarks, resources, and campus navigation.', 'FRESHMAN', 30, 3700, '["de"]'),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'uchicago', 'Dorms and first-year living', 'dorm-housing-4', 'A practical look at UChicago residence halls, dining, and first-year living.', 'DORM_HOUSING', 45, 3400, '["ar"]'),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'uchicago', 'International student life', 'international-student-5', 'What it is like to arrive, study, and belong as an international student at UChicago.', 'INTERNATIONAL_STUDENT', 90, 3200, '["en-US"]'),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'uchicago', 'Campus life and must-see spots', 'general-campus-6', 'Explore UChicago landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 60, 4500, '["pt", "hi"]'),
    ('10000000-0000-0000-0000-000000000005'::uuid, 'mit', 'Transferring in: a practical tour', 'transfer-1', 'A transfer-student view of settling in and finding your footing at MIT.', 'TRANSFER', 45, 4700, '["fr"]'),
    ('10000000-0000-0000-0000-000000000005'::uuid, 'mit', 'Transferring in: a practical tour', 'transfer-2', 'A transfer-student view of settling in and finding your footing at MIT.', 'TRANSFER', 60, 4800, '["ko"]'),
    ('10000000-0000-0000-0000-000000000005'::uuid, 'mit', 'Dining and student life', 'dining-student-life-3', 'Discover MIT student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 45, 3200, '["ar", "ja", "es"]'),
    ('10000000-0000-0000-0000-000000000005'::uuid, 'mit', 'Neuroscience pathways', 'major-specific-4', 'See where Neuroscience students learn, build, and find community at MIT.', 'MAJOR_SPECIFIC', 30, 4400, '["ja"]'),
    ('10000000-0000-0000-0000-000000000005'::uuid, 'mit', 'Dining and student life', 'dining-student-life-5', 'Discover MIT student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 60, 3700, '["es", "ja", "en-US"]'),
    ('10000000-0000-0000-0000-000000000005'::uuid, 'mit', 'A parent''s guide to campus', 'parent-focused-6', 'A parent-focused overview of MIT housing, services, safety, and support.', 'PARENT_FOCUSED', 90, 4700, '["ko"]'),
    ('10000000-0000-0000-0000-000000000006'::uuid, 'ucla', 'Dorms and first-year living', 'dorm-housing-1', 'A practical look at UCLA residence halls, dining, and first-year living.', 'DORM_HOUSING', 30, 4800, '["ar", "de"]'),
    ('10000000-0000-0000-0000-000000000006'::uuid, 'ucla', 'International student life', 'international-student-2', 'What it is like to arrive, study, and belong as an international student at UCLA.', 'INTERNATIONAL_STUDENT', 45, 4400, '["ar"]'),
    ('10000000-0000-0000-0000-000000000006'::uuid, 'ucla', 'Dorms and first-year living', 'dorm-housing-3', 'A practical look at UCLA residence halls, dining, and first-year living.', 'DORM_HOUSING', 90, 3300, '["es"]'),
    ('10000000-0000-0000-0000-000000000006'::uuid, 'ucla', 'Dorms and first-year living', 'dorm-housing-4', 'A practical look at UCLA residence halls, dining, and first-year living.', 'DORM_HOUSING', 45, 5900, '["ar"]'),
    ('10000000-0000-0000-0000-000000000006'::uuid, 'ucla', 'Neuroscience pathways', 'major-specific-5', 'See where Neuroscience students learn, build, and find community at UCLA.', 'MAJOR_SPECIFIC', 30, 5300, '["ko", "ar", "pt"]'),
    ('10000000-0000-0000-0000-000000000006'::uuid, 'ucla', 'A parent''s guide to campus', 'parent-focused-6', 'A parent-focused overview of UCLA housing, services, safety, and support.', 'PARENT_FOCUSED', 30, 6000, '["en-US", "es"]'),
    ('10000000-0000-0000-0000-000000000007'::uuid, 'stanford', 'Your first year, made simple', 'freshman-1', 'A first-time visitor tour of Stanford landmarks, resources, and campus navigation.', 'FRESHMAN', 90, 5600, '["es"]'),
    ('10000000-0000-0000-0000-000000000007'::uuid, 'stanford', 'A parent''s guide to campus', 'parent-focused-2', 'A parent-focused overview of Stanford housing, services, safety, and support.', 'PARENT_FOCUSED', 30, 3600, '["ko"]'),
    ('10000000-0000-0000-0000-000000000007'::uuid, 'stanford', 'Dining and student life', 'dining-student-life-3', 'Discover Stanford student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 45, 4600, '["de"]'),
    ('10000000-0000-0000-0000-000000000007'::uuid, 'stanford', 'International student life', 'international-student-4', 'What it is like to arrive, study, and belong as an international student at Stanford.', 'INTERNATIONAL_STUDENT', 90, 3900, '["zh"]'),
    ('10000000-0000-0000-0000-000000000007'::uuid, 'stanford', 'Dining and student life', 'dining-student-life-5', 'Discover Stanford student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 60, 3300, '["ko"]'),
    ('10000000-0000-0000-0000-000000000007'::uuid, 'stanford', 'Your first year, made simple', 'freshman-6', 'A first-time visitor tour of Stanford landmarks, resources, and campus navigation.', 'FRESHMAN', 90, 5900, '["fr"]'),
    ('10000000-0000-0000-0000-000000000008'::uuid, 'nyu', 'Your first year, made simple', 'freshman-1', 'A first-time visitor tour of NYU landmarks, resources, and campus navigation.', 'FRESHMAN', 90, 3500, '["hi"]'),
    ('10000000-0000-0000-0000-000000000008'::uuid, 'nyu', 'Dining and student life', 'dining-student-life-2', 'Discover NYU student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 45, 3800, '["es"]'),
    ('10000000-0000-0000-0000-000000000008'::uuid, 'nyu', 'Campus life and must-see spots', 'general-campus-3', 'Explore NYU landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 90, 5400, '["ar"]'),
    ('10000000-0000-0000-0000-000000000008'::uuid, 'nyu', 'Computer Science pathways', 'major-specific-4', 'See where Computer Science students learn, build, and find community at NYU.', 'MAJOR_SPECIFIC', 90, 2900, '["ar", "de", "hi"]'),
    ('10000000-0000-0000-0000-000000000008'::uuid, 'nyu', 'Campus life and must-see spots', 'general-campus-5', 'Explore NYU landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 90, 2600, '["fr"]'),
    ('10000000-0000-0000-0000-000000000008'::uuid, 'nyu', 'Transferring in: a practical tour', 'transfer-6', 'A transfer-student view of settling in and finding your footing at NYU.', 'TRANSFER', 30, 4500, '["ja", "es"]'),
    ('10000000-0000-0000-0000-000000000009'::uuid, 'uchicago', 'Architecture pathways', 'major-specific-1', 'See where Architecture students learn, build, and find community at UChicago.', 'MAJOR_SPECIFIC', 45, 5100, '["pt", "hi", "ko"]'),
    ('10000000-0000-0000-0000-000000000009'::uuid, 'uchicago', 'A parent''s guide to campus', 'parent-focused-2', 'A parent-focused overview of UChicago housing, services, safety, and support.', 'PARENT_FOCUSED', 45, 5500, '["ar", "fr"]'),
    ('10000000-0000-0000-0000-000000000009'::uuid, 'uchicago', 'Your first year, made simple', 'freshman-3', 'A first-time visitor tour of UChicago landmarks, resources, and campus navigation.', 'FRESHMAN', 30, 4300, '["de"]'),
    ('10000000-0000-0000-0000-000000000009'::uuid, 'uchicago', 'International student life', 'international-student-4', 'What it is like to arrive, study, and belong as an international student at UChicago.', 'INTERNATIONAL_STUDENT', 60, 4200, '["pt", "de"]'),
    ('10000000-0000-0000-0000-000000000009'::uuid, 'uchicago', 'International student life', 'international-student-5', 'What it is like to arrive, study, and belong as an international student at UChicago.', 'INTERNATIONAL_STUDENT', 60, 5500, '["de"]'),
    ('10000000-0000-0000-0000-000000000009'::uuid, 'uchicago', 'Architecture pathways', 'major-specific-6', 'See where Architecture students learn, build, and find community at UChicago.', 'MAJOR_SPECIFIC', 60, 5800, '["en-US", "ja", "hi"]'),
    ('10000000-0000-0000-0000-00000000000a'::uuid, 'mit', 'Campus life and must-see spots', 'general-campus-1', 'Explore MIT landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 45, 5100, '["hi"]'),
    ('10000000-0000-0000-0000-00000000000a'::uuid, 'mit', 'A parent''s guide to campus', 'parent-focused-2', 'A parent-focused overview of MIT housing, services, safety, and support.', 'PARENT_FOCUSED', 60, 3500, '["ko", "es"]'),
    ('10000000-0000-0000-0000-00000000000a'::uuid, 'mit', 'Dorms and first-year living', 'dorm-housing-3', 'A practical look at MIT residence halls, dining, and first-year living.', 'DORM_HOUSING', 30, 2800, '["ar"]'),
    ('10000000-0000-0000-0000-00000000000a'::uuid, 'mit', 'Dorms and first-year living', 'dorm-housing-4', 'A practical look at MIT residence halls, dining, and first-year living.', 'DORM_HOUSING', 45, 5100, '["de"]'),
    ('10000000-0000-0000-0000-00000000000a'::uuid, 'mit', 'Dorms and first-year living', 'dorm-housing-5', 'A practical look at MIT residence halls, dining, and first-year living.', 'DORM_HOUSING', 45, 5300, '["hi"]'),
    ('10000000-0000-0000-0000-00000000000a'::uuid, 'mit', 'A parent''s guide to campus', 'parent-focused-6', 'A parent-focused overview of MIT housing, services, safety, and support.', 'PARENT_FOCUSED', 30, 4200, '["fr", "ar"]'),
    ('10000000-0000-0000-0000-00000000000b'::uuid, 'ucla', 'Dorms and first-year living', 'dorm-housing-1', 'A practical look at UCLA residence halls, dining, and first-year living.', 'DORM_HOUSING', 90, 3300, '["ar"]'),
    ('10000000-0000-0000-0000-00000000000b'::uuid, 'ucla', 'International student life', 'international-student-2', 'What it is like to arrive, study, and belong as an international student at UCLA.', 'INTERNATIONAL_STUDENT', 30, 2800, '["zh"]'),
    ('10000000-0000-0000-0000-00000000000b'::uuid, 'ucla', 'Campus life and must-see spots', 'general-campus-3', 'Explore UCLA landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 45, 5600, '["ar", "de"]'),
    ('10000000-0000-0000-0000-00000000000b'::uuid, 'ucla', 'Environmental Science pathways', 'major-specific-4', 'See where Environmental Science students learn, build, and find community at UCLA.', 'MAJOR_SPECIFIC', 60, 2600, '["de"]'),
    ('10000000-0000-0000-0000-00000000000b'::uuid, 'ucla', 'Dorms and first-year living', 'dorm-housing-5', 'A practical look at UCLA residence halls, dining, and first-year living.', 'DORM_HOUSING', 30, 5500, '["ko"]'),
    ('10000000-0000-0000-0000-00000000000b'::uuid, 'ucla', 'Dorms and first-year living', 'dorm-housing-6', 'A practical look at UCLA residence halls, dining, and first-year living.', 'DORM_HOUSING', 90, 3100, '["zh"]'),
    ('10000000-0000-0000-0000-00000000000c'::uuid, 'stanford', 'Your first year, made simple', 'freshman-1', 'A first-time visitor tour of Stanford landmarks, resources, and campus navigation.', 'FRESHMAN', 30, 4600, '["ar", "pt", "zh"]'),
    ('10000000-0000-0000-0000-00000000000c'::uuid, 'stanford', 'International student life', 'international-student-2', 'What it is like to arrive, study, and belong as an international student at Stanford.', 'INTERNATIONAL_STUDENT', 30, 3700, '["de", "ja"]'),
    ('10000000-0000-0000-0000-00000000000c'::uuid, 'stanford', 'Transferring in: a practical tour', 'transfer-3', 'A transfer-student view of settling in and finding your footing at Stanford.', 'TRANSFER', 30, 3700, '["hi", "es"]'),
    ('10000000-0000-0000-0000-00000000000c'::uuid, 'stanford', 'Campus life and must-see spots', 'general-campus-4', 'Explore Stanford landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 30, 5700, '["fr"]'),
    ('10000000-0000-0000-0000-00000000000c'::uuid, 'stanford', 'Dorms and first-year living', 'dorm-housing-5', 'A practical look at Stanford residence halls, dining, and first-year living.', 'DORM_HOUSING', 45, 4500, '["en-US"]'),
    ('10000000-0000-0000-0000-00000000000c'::uuid, 'stanford', 'Transferring in: a practical tour', 'transfer-6', 'A transfer-student view of settling in and finding your footing at Stanford.', 'TRANSFER', 60, 4100, '["es", "fr"]'),
    ('10000000-0000-0000-0000-00000000000d'::uuid, 'nyu', 'Dining and student life', 'dining-student-life-1', 'Discover NYU student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 90, 3300, '["de"]'),
    ('10000000-0000-0000-0000-00000000000d'::uuid, 'nyu', 'English pathways', 'major-specific-2', 'See where English students learn, build, and find community at NYU.', 'MAJOR_SPECIFIC', 45, 4300, '["fr", "ja", "ar"]'),
    ('10000000-0000-0000-0000-00000000000d'::uuid, 'nyu', 'Dining and student life', 'dining-student-life-3', 'Discover NYU student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 45, 3100, '["en-US", "hi"]'),
    ('10000000-0000-0000-0000-00000000000d'::uuid, 'nyu', 'A parent''s guide to campus', 'parent-focused-4', 'A parent-focused overview of NYU housing, services, safety, and support.', 'PARENT_FOCUSED', 90, 5400, '["hi", "zh"]'),
    ('10000000-0000-0000-0000-00000000000d'::uuid, 'nyu', 'Dorms and first-year living', 'dorm-housing-5', 'A practical look at NYU residence halls, dining, and first-year living.', 'DORM_HOUSING', 45, 3700, '["pt"]'),
    ('10000000-0000-0000-0000-00000000000d'::uuid, 'nyu', 'International student life', 'international-student-6', 'What it is like to arrive, study, and belong as an international student at NYU.', 'INTERNATIONAL_STUDENT', 60, 6000, '["pt"]'),
    ('10000000-0000-0000-0000-00000000000e'::uuid, 'uchicago', 'A parent''s guide to campus', 'parent-focused-1', 'A parent-focused overview of UChicago housing, services, safety, and support.', 'PARENT_FOCUSED', 90, 5200, '["fr"]'),
    ('10000000-0000-0000-0000-00000000000e'::uuid, 'uchicago', 'International Relations pathways', 'major-specific-2', 'See where International Relations students learn, build, and find community at UChicago.', 'MAJOR_SPECIFIC', 45, 2800, '["zh"]'),
    ('10000000-0000-0000-0000-00000000000e'::uuid, 'uchicago', 'Your first year, made simple', 'freshman-3', 'A first-time visitor tour of UChicago landmarks, resources, and campus navigation.', 'FRESHMAN', 45, 2800, '["ar"]'),
    ('10000000-0000-0000-0000-00000000000e'::uuid, 'uchicago', 'Dining and student life', 'dining-student-life-4', 'Discover UChicago student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 60, 2800, '["de"]'),
    ('10000000-0000-0000-0000-00000000000e'::uuid, 'uchicago', 'Dorms and first-year living', 'dorm-housing-5', 'A practical look at UChicago residence halls, dining, and first-year living.', 'DORM_HOUSING', 45, 3600, '["de"]'),
    ('10000000-0000-0000-0000-00000000000e'::uuid, 'uchicago', 'Campus life and must-see spots', 'general-campus-6', 'Explore UChicago landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 90, 3200, '["en-US"]'),
    ('10000000-0000-0000-0000-00000000000f'::uuid, 'mit', 'Campus life and must-see spots', 'general-campus-1', 'Explore MIT landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 60, 5900, '["hi", "es", "ar"]'),
    ('10000000-0000-0000-0000-00000000000f'::uuid, 'mit', 'Campus life and must-see spots', 'general-campus-2', 'Explore MIT landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 60, 3600, '["ar", "de"]'),
    ('10000000-0000-0000-0000-00000000000f'::uuid, 'mit', 'International student life', 'international-student-3', 'What it is like to arrive, study, and belong as an international student at MIT.', 'INTERNATIONAL_STUDENT', 90, 5200, '["ar"]'),
    ('10000000-0000-0000-0000-00000000000f'::uuid, 'mit', 'Dorms and first-year living', 'dorm-housing-4', 'A practical look at MIT residence halls, dining, and first-year living.', 'DORM_HOUSING', 90, 4800, '["ko"]'),
    ('10000000-0000-0000-0000-00000000000f'::uuid, 'mit', 'A parent''s guide to campus', 'parent-focused-5', 'A parent-focused overview of MIT housing, services, safety, and support.', 'PARENT_FOCUSED', 90, 4600, '["ar", "en-US"]'),
    ('10000000-0000-0000-0000-00000000000f'::uuid, 'mit', 'Your first year, made simple', 'freshman-6', 'A first-time visitor tour of MIT landmarks, resources, and campus navigation.', 'FRESHMAN', 30, 4100, '["fr", "de", "es"]'),
    ('10000000-0000-0000-0000-000000000010'::uuid, 'ucla', 'Campus life and must-see spots', 'general-campus-1', 'Explore UCLA landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 45, 3600, '["ko"]'),
    ('10000000-0000-0000-0000-000000000010'::uuid, 'ucla', 'Your first year, made simple', 'freshman-2', 'A first-time visitor tour of UCLA landmarks, resources, and campus navigation.', 'FRESHMAN', 45, 5300, '["ja", "ko"]'),
    ('10000000-0000-0000-0000-000000000010'::uuid, 'ucla', 'Transferring in: a practical tour', 'transfer-3', 'A transfer-student view of settling in and finding your footing at UCLA.', 'TRANSFER', 90, 6000, '["de"]'),
    ('10000000-0000-0000-0000-000000000010'::uuid, 'ucla', 'Your first year, made simple', 'freshman-4', 'A first-time visitor tour of UCLA landmarks, resources, and campus navigation.', 'FRESHMAN', 90, 4800, '["pt"]'),
    ('10000000-0000-0000-0000-000000000010'::uuid, 'ucla', 'Dining and student life', 'dining-student-life-5', 'Discover UCLA student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 30, 3800, '["fr", "pt"]'),
    ('10000000-0000-0000-0000-000000000010'::uuid, 'ucla', 'Transferring in: a practical tour', 'transfer-6', 'A transfer-student view of settling in and finding your footing at UCLA.', 'TRANSFER', 60, 2900, '["ja", "es"]'),
    ('10000000-0000-0000-0000-000000000011'::uuid, 'stanford', 'International student life', 'international-student-1', 'What it is like to arrive, study, and belong as an international student at Stanford.', 'INTERNATIONAL_STUDENT', 45, 5000, '["ar", "ko"]'),
    ('10000000-0000-0000-0000-000000000011'::uuid, 'stanford', 'Your first year, made simple', 'freshman-2', 'A first-time visitor tour of Stanford landmarks, resources, and campus navigation.', 'FRESHMAN', 90, 5800, '["en-US", "pt"]'),
    ('10000000-0000-0000-0000-000000000011'::uuid, 'stanford', 'Computer Science pathways', 'major-specific-3', 'See where Computer Science students learn, build, and find community at Stanford.', 'MAJOR_SPECIFIC', 45, 3500, '["ar", "fr"]'),
    ('10000000-0000-0000-0000-000000000011'::uuid, 'stanford', 'Dining and student life', 'dining-student-life-4', 'Discover Stanford student hangouts, dining, and the neighborhoods nearby.', 'DINING_STUDENT_LIFE', 90, 3400, '["zh", "ja", "pt"]'),
    ('10000000-0000-0000-0000-000000000011'::uuid, 'stanford', 'Transferring in: a practical tour', 'transfer-5', 'A transfer-student view of settling in and finding your footing at Stanford.', 'TRANSFER', 90, 3800, '["pt", "de"]'),
    ('10000000-0000-0000-0000-000000000011'::uuid, 'stanford', 'Transferring in: a practical tour', 'transfer-6', 'A transfer-student view of settling in and finding your footing at Stanford.', 'TRANSFER', 45, 5200, '["ko", "fr"]'),
    ('10000000-0000-0000-0000-000000000012'::uuid, 'nyu', 'Dorms and first-year living', 'dorm-housing-1', 'A practical look at NYU residence halls, dining, and first-year living.', 'DORM_HOUSING', 90, 5500, '["hi", "fr", "ko"]'),
    ('10000000-0000-0000-0000-000000000012'::uuid, 'nyu', 'Dorms and first-year living', 'dorm-housing-2', 'A practical look at NYU residence halls, dining, and first-year living.', 'DORM_HOUSING', 90, 5500, '["fr", "es"]'),
    ('10000000-0000-0000-0000-000000000012'::uuid, 'nyu', 'Campus life and must-see spots', 'general-campus-3', 'Explore NYU landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 60, 4900, '["fr", "zh"]'),
    ('10000000-0000-0000-0000-000000000012'::uuid, 'nyu', 'Dorms and first-year living', 'dorm-housing-4', 'A practical look at NYU residence halls, dining, and first-year living.', 'DORM_HOUSING', 30, 3500, '["zh", "es"]'),
    ('10000000-0000-0000-0000-000000000012'::uuid, 'nyu', 'Mechanical Engineering pathways', 'major-specific-5', 'See where Mechanical Engineering students learn, build, and find community at NYU.', 'MAJOR_SPECIFIC', 30, 3900, '["zh"]'),
    ('10000000-0000-0000-0000-000000000012'::uuid, 'nyu', 'Campus life and must-see spots', 'general-campus-6', 'Explore NYU landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 30, 4500, '["hi"]'),
    ('10000000-0000-0000-0000-000000000013'::uuid, 'uchicago', 'A parent''s guide to campus', 'parent-focused-1', 'A parent-focused overview of UChicago housing, services, safety, and support.', 'PARENT_FOCUSED', 60, 4900, '["en-US"]'),
    ('10000000-0000-0000-0000-000000000013'::uuid, 'uchicago', 'International student life', 'international-student-2', 'What it is like to arrive, study, and belong as an international student at UChicago.', 'INTERNATIONAL_STUDENT', 90, 5400, '["pt"]'),
    ('10000000-0000-0000-0000-000000000013'::uuid, 'uchicago', 'Your first year, made simple', 'freshman-3', 'A first-time visitor tour of UChicago landmarks, resources, and campus navigation.', 'FRESHMAN', 60, 3500, '["pt"]'),
    ('10000000-0000-0000-0000-000000000013'::uuid, 'uchicago', 'Your first year, made simple', 'freshman-4', 'A first-time visitor tour of UChicago landmarks, resources, and campus navigation.', 'FRESHMAN', 45, 4500, '["ar"]'),
    ('10000000-0000-0000-0000-000000000013'::uuid, 'uchicago', 'A parent''s guide to campus', 'parent-focused-5', 'A parent-focused overview of UChicago housing, services, safety, and support.', 'PARENT_FOCUSED', 30, 4700, '["ar"]'),
    ('10000000-0000-0000-0000-000000000013'::uuid, 'uchicago', 'Your first year, made simple', 'freshman-6', 'A first-time visitor tour of UChicago landmarks, resources, and campus navigation.', 'FRESHMAN', 45, 5800, '["zh"]'),
    ('10000000-0000-0000-0000-000000000014'::uuid, 'mit', 'Film & Media pathways', 'major-specific-1', 'See where Film & Media students learn, build, and find community at MIT.', 'MAJOR_SPECIFIC', 60, 5600, '["es"]'),
    ('10000000-0000-0000-0000-000000000014'::uuid, 'mit', 'A parent''s guide to campus', 'parent-focused-2', 'A parent-focused overview of MIT housing, services, safety, and support.', 'PARENT_FOCUSED', 90, 3200, '["fr", "de"]'),
    ('10000000-0000-0000-0000-000000000014'::uuid, 'mit', 'Film & Media pathways', 'major-specific-3', 'See where Film & Media students learn, build, and find community at MIT.', 'MAJOR_SPECIFIC', 30, 5900, '["fr", "zh"]'),
    ('10000000-0000-0000-0000-000000000014'::uuid, 'mit', 'International student life', 'international-student-4', 'What it is like to arrive, study, and belong as an international student at MIT.', 'INTERNATIONAL_STUDENT', 45, 4800, '["zh", "en-US"]'),
    ('10000000-0000-0000-0000-000000000014'::uuid, 'mit', 'Film & Media pathways', 'major-specific-5', 'See where Film & Media students learn, build, and find community at MIT.', 'MAJOR_SPECIFIC', 60, 5400, '["hi", "ja", "fr"]'),
    ('10000000-0000-0000-0000-000000000014'::uuid, 'mit', 'Campus life and must-see spots', 'general-campus-6', 'Explore MIT landmarks, study spaces, and the daily rhythm of campus life.', 'GENERAL_CAMPUS', 30, 5100, '["es"]')
) AS seed(user_id, university_slug, title, slug, description, topic, duration_min, price_cents, languages)
JOIN guide_profiles guide_profile ON guide_profile.user_id = seed.user_id::uuid
JOIN universities university ON university.slug = seed.university_slug
ON CONFLICT (guide_id, slug) DO NOTHING;
