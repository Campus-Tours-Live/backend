-- =====================================================================
-- V4 — Backfill image_url (and reconcile name) for universities seeded before
-- V2 gained/settled these values. FORWARD-ONLY: V2 is frozen (an already-applied
-- migration is never edited); this new migration rolls the data forward instead.
--
-- Why this is needed: V2 upserts with ON CONFLICT (slug) DO NOTHING, so any row
-- already present from an EARLIER V2 byte-set never received the image_url values
-- (nor the later name corrections). This UPDATE reconciles image_url and name,
-- keyed by the stable slug. It NEVER touches slug (the ON CONFLICT unique key,
-- referenced by tour URLs / potential FKs — changing it would cascade).
--
-- Idempotent: the WHERE clause only matches rows that actually differ, so this is
-- a no-op on a fresh DB that already got the correct values straight from V2, and
-- re-running it changes nothing. Values below mirror V2 exactly.
-- =====================================================================
UPDATE universities u
SET image_url = v.image_url,
    name      = v.name
FROM (VALUES
  ('berkeley', 'University of California-Berkeley', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20California-Berkeley.png'),
  ('michigan', 'University of Michigan-Ann Arbor', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Michigan-Ann%20Arbor.png'),
  ('gatech', 'Georgia Institute of Technology-Main Campus', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Georgia%20Institute%20of%20Technology-Main%20Campus.png'),
  ('utaustin', 'The University of Texas at Austin', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/The%20University%20of%20Texas%20at%20Austin.png'),
  ('uw', 'University of Washington-Seattle Campus', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Washington-Seattle%20Campus.png'),
  ('illinois', 'University of Illinois Urbana-Champaign', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Illinois%20Urbana-Champaign.png'),
  ('purdue', 'Purdue University-Main Campus', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Purdue%20University-Main%20Campus.png'),
  ('wisconsin', 'University of Wisconsin-Madison', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Wisconsin-Madison.png'),
  ('unc', 'University of North Carolina at Chapel Hill', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20North%20Carolina%20at%20Chapel%20Hill.png'),
  ('uva', 'University of Virginia-Main Campus', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Virginia-Main%20Campus.png'),
  ('usc', 'University of Southern California', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Southern%20California.png'),
  ('cmu', 'Carnegie Mellon University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Carnegie%20Mellon%20University.png'),
  ('caltech', 'California Institute of Technology', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/California%20Institute%20of%20Technology.png'),
  ('cornell', 'Cornell University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Cornell%20University.png'),
  ('columbia', 'Columbia University in the City of New York', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Columbia%20University%20in%20the%20City%20of%20New%20York.png'),
  ('penn', 'University of Pennsylvania', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Pennsylvania.png'),
  ('duke', 'Duke University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Duke%20University.png'),
  ('jhu', 'Johns Hopkins University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Johns%20Hopkins%20University.png'),
  ('northwestern', 'Northwestern University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Northwestern%20University.png'),
  ('rice', 'Rice University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Rice%20University.png'),
  ('vanderbilt', 'Vanderbilt University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Vanderbilt%20University.png'),
  ('emory', 'Emory University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Emory%20University.png'),
  ('notredame', 'University of Notre Dame', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Notre%20Dame.png'),
  ('bu', 'Boston University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Boston%20University.png'),
  ('bc', 'Boston College', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Boston%20College.png'),
  ('tufts', 'Tufts University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Tufts%20University.png'),
  ('northeastern', 'Northeastern University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Northeastern%20University.png'),
  ('georgetown', 'Georgetown University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Georgetown%20University.png'),
  ('osu', 'Ohio State University-Main Campus', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Ohio%20State%20University-Main%20Campus.png'),
  ('pennstate', 'Pennsylvania State University-Main Campus', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Pennsylvania%20State%20University-Main%20Campus.png'),
  ('maryland', 'University of Maryland-College Park', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Maryland-College%20Park.png'),
  ('florida', 'University of Florida', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Florida.png'),
  ('georgia', 'University of Georgia', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Georgia.png'),
  ('tamu', 'Texas A&M University-College Station', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Texas%20A%26M%20University-College%20Station.png'),
  ('asu', 'Arizona State University Campus Immersion', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Arizona%20State%20University%20Campus%20Immersion.png'),
  ('indiana', 'Indiana University-Bloomington', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Indiana%20University-Bloomington.png'),
  ('iowa', 'University of Iowa', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Iowa.png'),
  ('minnesota', 'University of Minnesota-Twin Cities', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Minnesota-Twin%20Cities.png'),
  ('colorado', 'University of Colorado Boulder', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Colorado%20Boulder.png'),
  ('oregon', 'University of Oregon', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Oregon.png'),
  ('pitt', 'University of Pittsburgh-Pittsburgh Campus', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Pittsburgh-Pittsburgh%20Campus.png'),
  ('rutgers', 'Rutgers University-New Brunswick', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Rutgers%20University-New%20Brunswick.png'),
  ('ucsd', 'University of California-San Diego', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20California-San%20Diego.png'),
  ('ucdavis', 'University of California-Davis', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20California-Davis.png'),
  ('ucirvine', 'University of California-Irvine', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20California-Irvine.png'),
  ('ucsb', 'University of California-Santa Barbara', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20California-Santa%20Barbara.png'),
  ('rochester', 'University of Rochester', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Rochester.png'),
  ('case', 'Case Western Reserve University', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Case%20Western%20Reserve%20University.png'),
  ('tulane', 'Tulane University of Louisiana', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/Tulane%20University%20of%20Louisiana.png'),
  ('miami', 'University of Miami', 'https://pub-3225b84a9a0b4728b11f261ee52251ba.r2.dev/University%20of%20Miami.png')
) AS v(slug, name, image_url)
WHERE u.slug = v.slug
  AND (u.image_url IS DISTINCT FROM v.image_url OR u.name IS DISTINCT FROM v.name);
