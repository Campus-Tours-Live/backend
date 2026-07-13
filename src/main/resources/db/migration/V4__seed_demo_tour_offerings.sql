-- =====================================================================
-- V4 — Local MVP demo tour offerings
--
-- Depends on V2's university catalog and V3's approved fixture guides.
-- Every offering is ACTIVE, belongs to an APPROVED guide, and uses an ACTIVE
-- university so it is returned by GET /tours for frontend catalog development.
-- =====================================================================

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
    ('seed-guide-ethan-brooks', 'uchicago', 'Parent guide to UChicago campus life', 'parent-guide-campus-life', 'A parent-focused overview of housing, student services, safety, and campus support systems.', 'PARENT_FOCUSED', 45, 3200, '["en-US"]')
) AS seed(guide_subject, university_slug, title, slug, description, topic, duration_min, price_cents, languages)
JOIN users guide_user ON guide_user.oidc_subject = seed.guide_subject
JOIN guide_profiles guide_profile ON guide_profile.user_id = guide_user.id
JOIN universities university ON university.slug = seed.university_slug
ON CONFLICT (guide_id, slug) DO NOTHING;
