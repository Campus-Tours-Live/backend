#!/usr/bin/env node
// Reproducible generator for the demo seed (V2 universities + V3 guides/offerings). Emits STATIC
// SQL so the Flyway migrations stay deterministic. Marketplace-focused: users + GUIDE roles +
// APPROVED guide_profiles (with major / degree / entry_year) + ACTIVE tour_offerings (topic +
// features). Availability/booking rows are intentionally omitted (not needed by GET /tours).
//
//   node scripts/generate-seed.mjs
// Writes: src/main/resources/db/migration/V2__seed_universities.sql
//         src/main/resources/db/migration/V3__seed_demo_data.sql

import { writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const MIG = resolve(HERE, "../src/main/resources/db/migration");

const GUIDES = 500;
const OFFERINGS_PER_GUIDE = 6; // 500 * 6 = 3000 → 60 tours per university (round-robin over 50), so
// even a single-university filter spans multiple pages of 24.

// Deterministic PRNG (mulberry32, fixed seed) — no Math.random, so output is stable across runs.
let _s = 80 >>> 0;
function rnd() {
  _s = (_s + 0x6d2b79f5) | 0;
  let t = Math.imul(_s ^ (_s >>> 15), 1 | _s);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const pick = (a) => a[Math.floor(rnd() * a.length)];
const esc = (s) => String(s).replace(/'/g, "''");
const uuid = (n) => `10000000-0000-0000-0000-${n.toString(16).padStart(12, "0")}`;

// Cloudflare R2 campus image bucket. Encoding must match the Java CampusImageUrls builder
// (URLEncoder.encode(...).replace("+", "%20")): spaces -> %20, & -> %26, etc.
const CAMPUS_IMAGE_BASE = "https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/";
const campusImageUrl = (name) => `${CAMPUS_IMAGE_BASE}${encodeURIComponent(name)}.png`;

// ---- Universities: 50 well-known US schools, deliberately EXCLUDING the previous demo set
// (MIT, NYU, Stanford, UChicago, UCLA). [slug, name, short, city, region, tz]
const PT = "America/Los_Angeles",
  ET = "America/New_York",
  CT = "America/Chicago",
  MT = "America/Denver",
  AZ = "America/Phoenix";
const UNIS = [
  ["berkeley", "University of California-Berkeley", "Berkeley", "Berkeley", "CA", PT],
  ["michigan", "University of Michigan-Ann Arbor", "Michigan", "Ann Arbor", "MI", ET],
  ["gatech", "Georgia Institute of Technology-Main Campus", "Georgia Tech", "Atlanta", "GA", ET],
  ["utaustin", "The University of Texas at Austin", "UT Austin", "Austin", "TX", CT],
  ["uw", "University of Washington-Seattle Campus", "UW", "Seattle", "WA", PT],
  ["illinois", "University of Illinois Urbana-Champaign", "Illinois", "Urbana", "IL", CT],
  ["purdue", "Purdue University-Main Campus", "Purdue", "West Lafayette", "IN", ET],
  ["wisconsin", "University of Wisconsin-Madison", "Wisconsin", "Madison", "WI", CT],
  ["unc", "University of North Carolina at Chapel Hill", "UNC", "Chapel Hill", "NC", ET],
  ["uva", "University of Virginia-Main Campus", "UVA", "Charlottesville", "VA", ET],
  ["usc", "University of Southern California", "USC", "Los Angeles", "CA", PT],
  ["cmu", "Carnegie Mellon University", "CMU", "Pittsburgh", "PA", ET],
  ["caltech", "California Institute of Technology", "Caltech", "Pasadena", "CA", PT],
  ["cornell", "Cornell University", "Cornell", "Ithaca", "NY", ET],
  ["columbia", "Columbia University in the City of New York", "Columbia", "New York", "NY", ET],
  ["penn", "University of Pennsylvania", "Penn", "Philadelphia", "PA", ET],
  ["duke", "Duke University", "Duke", "Durham", "NC", ET],
  ["jhu", "Johns Hopkins University", "Johns Hopkins", "Baltimore", "MD", ET],
  ["northwestern", "Northwestern University", "Northwestern", "Evanston", "IL", CT],
  ["rice", "Rice University", "Rice", "Houston", "TX", CT],
  ["vanderbilt", "Vanderbilt University", "Vanderbilt", "Nashville", "TN", CT],
  ["emory", "Emory University", "Emory", "Atlanta", "GA", ET],
  ["notredame", "University of Notre Dame", "Notre Dame", "Notre Dame", "IN", ET],
  ["bu", "Boston University", "BU", "Boston", "MA", ET],
  ["bc", "Boston College", "BC", "Chestnut Hill", "MA", ET],
  ["tufts", "Tufts University", "Tufts", "Medford", "MA", ET],
  ["northeastern", "Northeastern University", "Northeastern", "Boston", "MA", ET],
  ["georgetown", "Georgetown University", "Georgetown", "Washington", "DC", ET],
  ["osu", "Ohio State University-Main Campus", "Ohio State", "Columbus", "OH", ET],
  ["pennstate", "Pennsylvania State University-Main Campus", "Penn State", "University Park", "PA", ET],
  ["maryland", "University of Maryland-College Park", "Maryland", "College Park", "MD", ET],
  ["florida", "University of Florida", "Florida", "Gainesville", "FL", ET],
  ["georgia", "University of Georgia", "Georgia", "Athens", "GA", ET],
  ["tamu", "Texas A&M University-College Station", "Texas A&M", "College Station", "TX", CT],
  ["asu", "Arizona State University Campus Immersion", "ASU", "Tempe", "AZ", AZ],
  ["indiana", "Indiana University-Bloomington", "Indiana", "Bloomington", "IN", ET],
  ["iowa", "University of Iowa", "Iowa", "Iowa City", "IA", CT],
  ["minnesota", "University of Minnesota-Twin Cities", "Minnesota", "Minneapolis", "MN", CT],
  ["colorado", "University of Colorado Boulder", "CU Boulder", "Boulder", "CO", MT],
  ["oregon", "University of Oregon", "Oregon", "Eugene", "OR", PT],
  ["pitt", "University of Pittsburgh-Pittsburgh Campus", "Pitt", "Pittsburgh", "PA", ET],
  ["rutgers", "Rutgers University-New Brunswick", "Rutgers", "New Brunswick", "NJ", ET],
  ["ucsd", "University of California-San Diego", "UC San Diego", "La Jolla", "CA", PT],
  ["ucdavis", "University of California-Davis", "UC Davis", "Davis", "CA", PT],
  ["ucirvine", "University of California-Irvine", "UC Irvine", "Irvine", "CA", PT],
  ["ucsb", "University of California-Santa Barbara", "UC Santa Barbara", "Santa Barbara", "CA", PT],
  ["rochester", "University of Rochester", "Rochester", "Rochester", "NY", ET],
  ["case", "Case Western Reserve University", "Case Western", "Cleveland", "OH", ET],
  ["tulane", "Tulane University of Louisiana", "Tulane", "New Orleans", "LA", CT],
  ["miami", "University of Miami", "Miami", "Coral Gables", "FL", ET],
];

// ---- Majors: cover every icon family in tourCard.visuals so cards show varied major glyphs.
const MAJORS = [
  "Computer Science", "Data Science", "Software Engineering", "Cybersecurity",
  "Electrical Engineering", "Mechanical Engineering", "Civil Engineering", "Chemical Engineering",
  "Aerospace Engineering", "Biomedical Engineering", "Architecture", "Urban Planning",
  "Chemistry", "Physics", "Astronomy", "Mathematics", "Statistics", "Accounting", "Finance",
  "Economics", "Business Administration", "Marketing", "Nursing", "Public Health", "Kinesiology",
  "Biology", "Neuroscience", "Genetics", "Psychology", "Environmental Science", "Geology",
  "Agriculture", "Animal Science", "Pre-Law", "Criminal Justice", "Political Science",
  "International Relations", "History", "Geography", "Linguistics", "Spanish", "Journalism",
  "Communications", "Sociology", "Anthropology", "Social Work", "Education", "Music",
  "Film Studies", "Photography", "Theatre Arts", "Dance", "Graphic Design", "Fine Arts",
  "Culinary Arts", "Nutrition", "Philosophy", "Religious Studies", "Sport Management",
  "English", "Comparative Literature", "Liberal Arts",
];

const DEGREES = ["BS", "BA", "BS", "BA", "BS", "BFA", "MS", "MA", "MEng", "PhD"];
const TOPICS = [
  "GENERAL_CAMPUS", "DORM_HOUSING", "DINING_STUDENT_LIFE", "INTERNATIONAL_STUDENT",
  "MAJOR_SPECIFIC", "PARENT_FOCUSED", "FRESHMAN", "TRANSFER",
];
const DURATIONS = [30, 45, 60, 90];
const LANGS = ["en-US", "es", "zh", "fr", "de", "ja", "ko", "ar", "hi", "pt", "it"];

// Feature catalog per topic (mirrors backend TourFeatureCatalog) — pick 3 per offering.
const FEATURES = {
  GENERAL_CAMPUS: ["Q_AND_A","SMALL_GROUP","HIDDEN_SPOTS","PHOTOS_OK","STUDENT_HANGOUTS","LANDMARKS","WHEELCHAIR","MEET_AT_GATE","RAIN_OR_SHINE","INSIDER_TIPS"],
  DORM_HOUSING: ["DORM_INTERIOR","ROOM_TOUR","SHARED_KITCHENS","LAUNDRY_AMENITIES","MOVE_IN_TIPS","ROOMMATE_ADVICE","PHOTOS_OK","COST_BREAKDOWN","WHEELCHAIR","Q_AND_A"],
  DINING_STUDENT_LIFE: ["DINING_HALL","FREE_SAMPLES","COFFEE_SPOTS","LATE_NIGHT_EATS","MEAL_PLAN_TIPS","DIETARY_OPTIONS","STUDENT_CLUBS","PHOTOS_OK","Q_AND_A","BUDGET_TIPS"],
  INTERNATIONAL_STUDENT: ["VISA_TIPS","MULTILINGUAL","TRANSIT_TIPS","HOUSING_HELP","BANKING_SIM","INTL_COMMUNITY","DIETARY_OPTIONS","Q_AND_A","CULTURAL_CLUBS","ORIENTATION_TIPS"],
  MAJOR_SPECIFIC: ["LAB_ACCESS","LECTURE_SIT_IN","MEET_FACULTY","RESEARCH_SPOTLIGHT","LIBRARY_RESOURCES","COURSE_ADVICE","CAREER_TIPS","PROJECT_SHOWCASE","Q_AND_A","STUDY_SPACES"],
  PARENT_FOCUSED: ["SAFETY_OVERVIEW","COST_AID_INFO","HOUSING_WALKTHROUGH","DINING_OVERVIEW","HEALTH_SERVICES","TRANSIT_PARKING","SLOWER_PACE","Q_AND_A","FAMILY_FRIENDLY","PHOTOS_OK"],
  FRESHMAN: ["FIRST_YEAR_DORMS","ORIENTATION_TIPS","WHERE_TO_EAT","STUDY_SPOTS","CLUBS_ACTIVITIES","GETTING_AROUND","MEET_UPPERCLASSMEN","SURVIVAL_TIPS","Q_AND_A","PHOTOS_OK"],
  TRANSFER: ["CREDIT_TRANSFER_TIPS","TRANSFER_HOUSING","ADVISING_RESOURCES","TRANSFER_COMMUNITY","FAST_TRACK_ORIENTATION","COURSE_PLANNING","CAREER_SERVICES","Q_AND_A","GET_CONNECTED","INSIDER_TIPS"],
};
const TITLES = {
  GENERAL_CAMPUS: "Campus life and must-see spots",
  DORM_HOUSING: "Dorms and first-year living",
  DINING_STUDENT_LIFE: "Dining and student life",
  INTERNATIONAL_STUDENT: "International student life",
  MAJOR_SPECIFIC: "Academics and majors up close",
  PARENT_FOCUSED: "A parent's guide to campus",
  FRESHMAN: "First-year survival tour",
  TRANSFER: "Transferring in: a practical tour",
};

const FIRST = ["Lucas","Priya","Wei","Noah","Jordan","Aisha","Mia","Ethan","Leah","Nina","Ava","Zoe","Sofia","Ravi","Liam","Diego","Omar","Kofi","Hana","Emma","Maya","Jack","Sara","Ken","Lily","Sam","Amir","Grace","Tom","Yuki","Ines","Pablo","Nadia","Owen","Chloe","Marco","Fatima","Leo","Nora","Hugo","Elif","Theo","Ivy","Sami","Rosa","Dev","Anya","Cole","Rina","Ben"];
const LAST = ["Santos","Shah","Zhang","Kim","Lee","Khan","Chen","Brooks","Cohen","Novak","Rivera","Nguyen","Patel","Iyer","Walsh","Morales","Haddad","Mensah","Sato","Wilson","Park","Silva","Costa","Ali","Reed","Ortiz","Ross","Tan","Vega","Bauer","Roy","Diaz","Fox","Mori","Lopez","Adams","Kaur","Bell","Hahn","Yang","Cruz","Meyer","Ng","Okafor","Rao","Weiss","Blanc","Ito","Kelly","Dubois"];

function langs(n) {
  const set = new Set(["en-US"]);
  while (set.size < n) set.add(pick(LANGS));
  return JSON.stringify([...set]);
}
function feats(topic) {
  const pool = FEATURES[topic];
  const set = new Set();
  while (set.size < 3) set.add(pick(pool));
  return JSON.stringify([...set]);
}

// ---------------------------------------------------------------- build SQL
const userRows = [], roleRows = [], guideRows = [], offeringRows = [];

for (let i = 0; i < GUIDES; i++) {
  const id = uuid(i + 1);
  const uni = UNIS[i % UNIS.length];
  const major = MAJORS[i % MAJORS.length];
  const degree = pick(DEGREES);
  const entryYear = 2018 + (i % 8);
  const classYear = entryYear + 4;
  const first = pick(FIRST), last = pick(LAST);
  const display = `${first} ${last}`;
  const basePrice = 2500 + (i % 15) * 500;
  const specialties = JSON.stringify([TOPICS[i % 8], TOPICS[(i + 3) % 8]]);
  const bio = `${esc(uni[2])} ${esc(major)} student (class of ${classYear}) who loves showing visitors around campus.`;

  userRows.push(
    `  ('${id}', 'seed-guide-${i + 1}', 'guide${i + 1}@seed.campustours.local', true, 'GUIDE', 'ACTIVE', 'ADULT', '${esc(first)}', '${esc(last)}', '${esc(display)}', 'en-US', '${uni[5]}')`,
  );
  roleRows.push(`  ('${id}', 'GUIDE')`);
  guideRows.push(
    `    ('${id}'::uuid, '${uni[0]}', '${esc(major)}', '${classYear}', '${degree}', ${entryYear}, '${bio}', '${langs(1 + (i % 3))}', '${specialties}', ${basePrice}::bigint)`,
  );

  for (let j = 0; j < OFFERINGS_PER_GUIDE; j++) {
    const topic = TOPICS[(i * OFFERINGS_PER_GUIDE + j) % 8];
    const duration = DURATIONS[(i + j) % 4];
    const price = 3000 + ((i * OFFERINGS_PER_GUIDE + j) % 14) * 1000;
    const title =
      topic === "MAJOR_SPECIFIC" ? `${esc(major)} pathways` : TITLES[topic];
    const desc = `${esc(TITLES[topic])} — a ${esc(topic.toLowerCase().replace(/_/g, " "))} tour at ${esc(uni[1])}.`;
    offeringRows.push(
      `    ('${id}'::uuid, '${uni[0]}', '${esc(title)}', 'tour-${i + 1}-${j + 1}', '${desc}', '${topic}', ${duration}, ${price}, '${langs(1 + (j % 2))}', '${feats(topic)}')`,
    );
  }
}

const uniRows = UNIS.map(
  (u) =>
    `  ('${u[0]}', '${esc(u[1])}', '${esc(u[2])}', '${esc(u[3])}', '${u[4]}', 'US', '${u[5]}', '${esc(campusImageUrl(u[1]))}', 'ACTIVE')`,
);

const V2 = `-- =====================================================================
-- V2 — Seed the demo universities the marketplace fixtures (V3) reference.
-- MACHINE-GENERATED by scripts/generate-seed.mjs (deterministic). Real guide
-- onboarding discovers all U.S. schools live via the College Scorecard API and
-- upserts the chosen school; this seed only backs the demo tours.
-- Idempotent: ON CONFLICT (slug) DO NOTHING.
-- =====================================================================
INSERT INTO universities (slug, name, short_name, city, region, country_code, timezone, image_url, status) VALUES
${uniRows.join(",\n")}
ON CONFLICT (slug) DO NOTHING;
`;

const V3 = `-- =====================================================================
-- V3 — Local MVP demo data. MACHINE-GENERATED by scripts/generate-seed.mjs
-- (deterministic, seed=80) as STATIC SQL.
--
-- ${GUIDES} APPROVED guides across ${UNIS.length} schools, each with ${OFFERINGS_PER_GUIDE} ACTIVE tour offerings
-- (${GUIDES * OFFERINGS_PER_GUIDE} total) with varied major / degree / entry_year / topic / features.
-- Marketplace-only: no participant/admin rows and no availability/booking rows
-- (GET /tours reads only offerings + approved guides + universities).
-- =====================================================================

INSERT INTO users (
  id, oidc_subject, email, email_verified, last_active_role,
  account_status, age_band, first_name, last_name, display_name,
  preferred_language, timezone
) VALUES
${userRows.join(",\n")}
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_roles (user_id, role) VALUES
${roleRows.join(",\n")}
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO guide_profiles (
  user_id, university_id, major, class_year, degree, entry_year, bio, languages,
  specialties, application_status, verification_status, base_price_cents, currency, approved_at
)
SELECT
  seed.user_id, university.id, seed.major, seed.class_year, seed.degree, seed.entry_year, seed.bio,
  seed.languages::jsonb, seed.specialties::jsonb,
  'APPROVED'::guide_application_status, 'VERIFIED'::guide_verification_status,
  seed.base_price_cents, 'USD', now()
FROM (
  VALUES
${guideRows.join(",\n")}
) AS seed(user_id, university_slug, major, class_year, degree, entry_year, bio, languages, specialties, base_price_cents)
JOIN universities university ON university.slug = seed.university_slug
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO tour_offerings (
  guide_id, university_id, title, slug, description, topic, duration_min,
  price_cents, currency, languages, features, status
)
SELECT
  guide_profile.id, university.id, seed.title, seed.slug, seed.description,
  seed.topic::tour_topic, seed.duration_min, seed.price_cents, 'USD',
  seed.languages::jsonb, seed.features::jsonb, 'ACTIVE'::tour_status
FROM (
  VALUES
${offeringRows.join(",\n")}
) AS seed(user_id, university_slug, title, slug, description, topic, duration_min, price_cents, languages, features)
JOIN guide_profiles guide_profile ON guide_profile.user_id = seed.user_id::uuid
JOIN universities university ON university.slug = seed.university_slug
ON CONFLICT (guide_id, slug) DO NOTHING;
`;

writeFileSync(resolve(MIG, "V2__seed_universities.sql"), V2);
writeFileSync(resolve(MIG, "V3__seed_demo_data.sql"), V3);
console.error(
  `Wrote V2 (${UNIS.length} universities) + V3 (${GUIDES} guides, ${offeringRows.length} offerings).`,
);
