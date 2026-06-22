-- =====================================================================
-- CampusToursLive.ai — MVP PostgreSQL Schema (V1)
-- Target: PostgreSQL 15+
-- Money: integer minor units (cents), BIGINT. Currency: ISO 4217 char(3).
-- Time : TIMESTAMPTZ (UTC). IANA time zones stored as text where needed.
-- Scope: Local-MVP. Tables/columns marked [DEFERRABLE] may be skipped for
--        a first local build (see docs/01-data-model.md).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS btree_gist;   -- exclusion constraints on (uuid, tstzrange)
CREATE EXTENSION IF NOT EXISTS citext;       -- case-insensitive email

-- ---------------------------------------------------------------------
-- ENUM TYPES (kept in sync with api/openapi.yaml)
-- ---------------------------------------------------------------------
CREATE TYPE user_role               AS ENUM ('PARTICIPANT','GUIDE','ADMIN','SUPPORT');
CREATE TYPE account_status          AS ENUM ('PENDING_VERIFICATION','ACTIVE','SUSPENDED','DELETED');
CREATE TYPE age_band                AS ENUM ('ADULT','MINOR_16_17','UNDER_16');
CREATE TYPE participant_type        AS ENUM ('HIGH_SCHOOL','PROSPECTIVE','TRANSFER','INTERNATIONAL','PARENT','OTHER');

CREATE TYPE guide_application_status AS ENUM ('DRAFT','PENDING_REVIEW','APPROVED','REJECTED','SUSPENDED');
CREATE TYPE guide_verification_status AS ENUM ('NOT_SUBMITTED','PENDING','VERIFIED','REJECTED','EXPIRED');
CREATE TYPE acceptance_mode         AS ENUM ('AUTO','MANUAL');

CREATE TYPE guardian_consent_status AS ENUM ('PENDING','PHONE_VERIFIED','ACCEPTED','REVOKED','EXPIRED');

CREATE TYPE university_status       AS ENUM ('ACTIVE','PAUSED','ARCHIVED');
CREATE TYPE tour_topic              AS ENUM ('GENERAL_CAMPUS','DORM_HOUSING','DINING_STUDENT_LIFE','MAJOR_SPECIFIC','INTERNATIONAL_STUDENT','PARENT_FOCUSED','FRESHMAN','TRANSFER');
CREATE TYPE tour_status             AS ENUM ('DRAFT','ACTIVE','PAUSED','ARCHIVED');

CREATE TYPE availability_exception_type AS ENUM ('UNAVAILABLE_ALL_DAY','UNAVAILABLE_RANGE','ADDITIONAL');

CREATE TYPE booking_status          AS ENUM (
  'DRAFT','PENDING_PAYMENT_AUTH','PENDING_GUIDE_ACCEPTANCE','PAYMENT_ACTION_REQUIRED',
  'CONFIRMED','DECLINED_BY_GUIDE','EXPIRED',
  'CANCELLED_BY_PARTICIPANT','CANCELLED_BY_GUIDE',
  'IN_PROGRESS','COMPLETED','PARTICIPANT_NO_SHOW','GUIDE_NO_SHOW',
  'TECHNICAL_FAILURE','REFUND_PENDING','REFUNDED');
CREATE TYPE booking_actor           AS ENUM ('PARTICIPANT','GUIDE','SYSTEM','ADMIN','STRIPE_WEBHOOK','DAILY_WEBHOOK');

CREATE TYPE payment_status          AS ENUM ('REQUIRES_PAYMENT_METHOD','AUTHORIZED','CAPTURE_PENDING','CAPTURED','CANCELLED','FAILED','PARTIALLY_REFUNDED','REFUNDED','DISPUTED');
CREATE TYPE refund_status           AS ENUM ('REQUESTED','PROCESSING','SUCCEEDED','FAILED');
CREATE TYPE refund_reason           AS ENUM ('PARTICIPANT_CANCELLATION','GUIDE_CANCELLATION','GUIDE_NO_SHOW','TECHNICAL_FAILURE','GUARDIAN_FAILURE','SUPPORT_ADJUSTMENT');

CREATE TYPE session_status          AS ENUM ('SCHEDULED','WAITING','IN_PROGRESS','PAUSED_GUARDIAN','ENDED','CANCELLED','FAILED');
CREATE TYPE session_role            AS ENUM ('PARTICIPANT','GUIDE');
CREATE TYPE connection_status       AS ENUM ('CONNECTING','ACTIVE','DISCONNECTED','REPLACED');
CREATE TYPE message_type            AS ENUM ('TEXT','QA','SYSTEM');
CREATE TYPE moderation_status       AS ENUM ('ALLOWED','BLOCKED','FLAGGED','REMOVED');

CREATE TYPE recording_status        AS ENUM ('PENDING','RECORDING','PROCESSING','AVAILABLE','FAILED','DELETED');

CREATE TYPE reschedule_status       AS ENUM ('PENDING_COUNTERPARTY','ACCEPTED','DECLINED','EXPIRED','CANCELLED');

CREATE TYPE review_status           AS ENUM ('PENDING_MODERATION','PUBLISHED','REMOVED');

CREATE TYPE notification_channel    AS ENUM ('EMAIL','SMS','IN_APP');
CREATE TYPE notification_status     AS ENUM ('SCHEDULED','SENT','FAILED','CANCELLED');

-- ---------------------------------------------------------------------
-- updated_at trigger helper
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- IDENTITY
-- =====================================================================
-- One row per person/account — the root of the data model. Created just-in-time on the
-- first authenticated request as a "bare" account (no role, no profile); roles and
-- role-specific profiles (participant_profiles / guide_profiles) are acquired later when
-- the corresponding onboarding completes.
--
-- Authentication: every request carries a Google OIDC id_token; `oidc_subject` (its "sub"
-- claim) is the stable join key to this row. Authorization is NOT decided here — it reads
-- the `user_roles` table (below).
CREATE TABLE users (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  oidc_subject       TEXT UNIQUE,                       -- Google OIDC subject ("sub"): the stable identity join key. Nullable at the DB level; the app always sets it on provisioning.
  email              CITEXT,                            -- case-insensitive (citext extension); may be NULL until provided
  email_verified     BOOLEAN NOT NULL DEFAULT FALSE,    -- mirrors the id_token "email_verified" claim
  -- The role whose view the user is currently in — UX context ONLY, never consulted for
  -- authorization (that is exclusively user_roles). NULL = no active role yet (bare account).
  -- Only switchable roles (PARTICIPANT/GUIDE) are stored here; staff roles aren't active-able.
  last_active_role   user_role,

  -- Account-level lifecycle, NOT role lifecycle (guide approval etc. lives on the profile):
  -- PENDING_VERIFICATION → ACTIVE on first use; SUSPENDED / DELETED gate all access.
  account_status     account_status NOT NULL DEFAULT 'PENDING_VERIFICATION',

  date_of_birth      DATE,                              -- nullable; only collected when age-gating applies
  age_band           age_band,                          -- coarse age bucket (ADULT / MINOR_16_17 / UNDER_16) for gating
  -- Name model: first/last are the structured source of truth supplied at onboarding;
  -- display_name is the canonical rendered string the app keeps synced from them (and is
  -- what the UI shows). display_name is NOT NULL because every account must render somehow.
  first_name         TEXT,                              -- structured given name (source of truth)
  last_name          TEXT,                              -- structured family name (source of truth)
  display_name       TEXT NOT NULL,                     -- rendered name shown in the UI; kept synced from first/last
  preferred_language TEXT NOT NULL DEFAULT 'en-US',     -- BCP-47 language tag
  timezone           TEXT NOT NULL DEFAULT 'America/Los_Angeles', -- IANA time-zone id
  stripe_customer_id TEXT UNIQUE,                       -- Stripe Customer id for saved payment methods (NULL until needed)
  last_login_at      TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),-- maintained by trg_users_updated below
  deleted_at         TIMESTAMPTZ                        -- soft-delete tombstone; NULL = live row
);
-- One active account per email, case-insensitively, ignoring soft-deleted rows.
CREATE UNIQUE INDEX uq_users_email_active ON users (lower(email)) WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- The authoritative set of roles a user holds, and the ONLY source consulted for
-- authorization (a row here = the user has that role). A user may hold several at once
-- (e.g. PARTICIPANT + GUIDE); a row is written when that role's onboarding completes.
-- Contrast users.last_active_role, which is mere UX context.
CREATE TABLE user_roles (
  user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE, -- grants are removed with the user
  role       user_role   NOT NULL,
  granted_at timestamptz NOT NULL DEFAULT now(),         -- when the role was acquired (audit trail)
  PRIMARY KEY (user_id, role)                            -- ≤ 1 row per (user, role) → grants are idempotent
);

-- The PARTICIPANT role's profile (1:1 with users; created when participant onboarding
-- completes). Holds tour-discovery preferences. `interests` is a JSONB blob of topic tags /
-- saved universities; `guardian_required` flags a minor who needs guardian consent first.
CREATE TABLE participant_profiles (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id            UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  participant_type   participant_type NOT NULL DEFAULT 'PROSPECTIVE',
  grade_level        TEXT,
  intended_major     TEXT,
  interests          JSONB NOT NULL DEFAULT '[]',       -- topic tags, saved universities, etc.
  guardian_required  BOOLEAN NOT NULL DEFAULT FALSE,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_pprofiles_updated BEFORE UPDATE ON participant_profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- GUARDIAN (minors 16-17)
-- =====================================================================
-- A parent/legal guardian attached to a minor participant (ages 16-17). Stores contact
-- details (phone hashed, email app-encrypted); the consent itself lives in guardian_consents.
CREATE TABLE guardians (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  participant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- Same name model as users: first/last are the structured source of truth; full_name is
  -- the canonical rendered string (NOT NULL — a guardian must always have a display name).
  full_name          TEXT NOT NULL,                     -- rendered name shown in the UI
  first_name         TEXT,                              -- structured given name (source of truth)
  last_name          TEXT,                              -- structured family name (source of truth)
  relationship       TEXT NOT NULL,
  phone_hash         TEXT NOT NULL,                     -- hashed; raw never stored in plaintext
  phone_verified_at  TIMESTAMPTZ,
  email_enc          TEXT,                              -- encrypted (app-level) [DEFERRABLE: plain in local dev]
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_guardians_participant ON guardians(participant_user_id);
CREATE TRIGGER trg_guardians_updated BEFORE UPDATE ON guardians FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- A consent request/grant from a guardian for a specific minor participant. Drives the
-- consent lifecycle (PENDING → PHONE_VERIFIED → ACCEPTED / REVOKED / EXPIRED). The single-use
-- link is stored hashed; the *_version columns pin exactly which policy text was agreed to.
CREATE TABLE guardian_consents (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  guardian_id        UUID NOT NULL REFERENCES guardians(id) ON DELETE CASCADE,
  participant_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status             guardian_consent_status NOT NULL DEFAULT 'PENDING',
  link_token_hash    TEXT NOT NULL,                     -- single-use, hashed
  terms_version      TEXT,
  privacy_version    TEXT,
  recording_policy_version TEXT,
  accepted_at        TIMESTAMPTZ,
  revoked_at         TIMESTAMPTZ,
  expires_at         TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_consents_participant ON guardian_consents(participant_user_id);
CREATE TRIGGER trg_consents_updated BEFORE UPDATE ON guardian_consents FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- UNIVERSITY / CATALOG
-- =====================================================================
-- University catalog (reference data): what participants browse and guides belong to.
-- `slug` is the stable public identifier; `status` hides non-ACTIVE rows from search.
CREATE TABLE universities (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug         TEXT NOT NULL UNIQUE,
  name         TEXT NOT NULL,
  short_name   TEXT,
  city         TEXT NOT NULL,
  region       TEXT,
  country_code CHAR(2) NOT NULL DEFAULT 'US',
  timezone     TEXT NOT NULL DEFAULT 'America/Los_Angeles',
  image_url    TEXT,
  status       university_status NOT NULL DEFAULT 'ACTIVE',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_univ_updated BEFORE UPDATE ON universities FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- GUIDE
-- =====================================================================
-- The GUIDE role's profile and marketplace listing (1:1 with users). `application_status`
-- and `verification_status` drive the approval gate: a guide may only publish offerings and
-- host tours once APPROVED. The rating/tour counters are denormalized aggregates kept here
-- for fast listing (source of truth is reviews / bookings).
CREATE TABLE guide_profiles (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id             UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  university_id       UUID NOT NULL REFERENCES universities(id),
  major               TEXT NOT NULL,
  class_year          TEXT,
  bio                 TEXT,
  languages           JSONB NOT NULL DEFAULT '["en-US"]',
  specialties         JSONB NOT NULL DEFAULT '[]',       -- array of tour_topic values
  favorite_spots      JSONB NOT NULL DEFAULT '[]',
  application_status  guide_application_status NOT NULL DEFAULT 'DRAFT',
  verification_status guide_verification_status NOT NULL DEFAULT 'NOT_SUBMITTED',
  base_price_cents    BIGINT NOT NULL DEFAULT 2800 CHECK (base_price_cents BETWEEN 2000 AND 20000),
  currency            CHAR(3) NOT NULL DEFAULT 'USD',
  avg_rating          NUMERIC(3,2) NOT NULL DEFAULT 0,
  review_count        INTEGER NOT NULL DEFAULT 0,
  completed_tours     INTEGER NOT NULL DEFAULT 0,
  stripe_account_id   TEXT,                              -- [DEFERRABLE for local MVP]
  payouts_enabled     BOOLEAN NOT NULL DEFAULT FALSE,    -- [DEFERRABLE for local MVP]
  approved_at         TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_guide_univ ON guide_profiles(university_id);
CREATE INDEX ix_guide_app_status ON guide_profiles(application_status);
CREATE TRIGGER trg_guide_updated BEFORE UPDATE ON guide_profiles FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- A student/identity verification attempt for a guide (university email, student ID, doc, …).
-- Reviewed by staff (`reviewed_by`); the outcome feeds guide_profiles.verification_status.
CREATE TABLE guide_verifications (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  guide_id           UUID NOT NULL REFERENCES guide_profiles(id) ON DELETE CASCADE,
  method             TEXT NOT NULL,                      -- UNIVERSITY_EMAIL | STUDENT_ID | ENROLLMENT_DOC | MANUAL
  university_email   CITEXT,
  document_key       TEXT,                               -- S3 object key [DEFERRABLE: local file path]
  status             guide_verification_status NOT NULL DEFAULT 'PENDING',
  reviewed_by        UUID REFERENCES users(id),
  reviewed_at        TIMESTAMPTZ,
  rejection_reason   TEXT,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_gverif_guide ON guide_verifications(guide_id);
CREATE TRIGGER trg_gverif_updated BEFORE UPDATE ON guide_verifications FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Per-guide booking policy (1:1 with guide_profiles, keyed by guide_id). Controls auto vs
-- manual acceptance, notice/lead-time windows, buffers between tours, and offered durations.
CREATE TABLE guide_booking_settings (
  guide_id                UUID PRIMARY KEY REFERENCES guide_profiles(id) ON DELETE CASCADE,
  acceptance_mode         acceptance_mode NOT NULL DEFAULT 'MANUAL',
  response_deadline_min   INTEGER NOT NULL DEFAULT 90,
  min_notice_min          INTEGER NOT NULL DEFAULT 1440,
  max_advance_days        INTEGER NOT NULL DEFAULT 30,
  buffer_before_min       INTEGER NOT NULL DEFAULT 0,
  buffer_after_min        INTEGER NOT NULL DEFAULT 15,
  durations_offered       JSONB NOT NULL DEFAULT '[30,45,60,90]',
  timezone                TEXT NOT NULL DEFAULT 'America/Los_Angeles',
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_gset_updated BEFORE UPDATE ON guide_booking_settings FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- A guide's sellable tour product (the supply side). Unique per (guide_id, slug). Going live
-- requires the guide to be APPROVED; `status` (DRAFT → ACTIVE → PAUSED/ARCHIVED) gates
-- visibility. duration_min and price_cents are constrained to the allowed marketplace ranges.
CREATE TABLE tour_offerings (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  guide_id        UUID NOT NULL REFERENCES guide_profiles(id) ON DELETE CASCADE,
  university_id   UUID NOT NULL REFERENCES universities(id),
  title           TEXT NOT NULL,
  slug            TEXT NOT NULL,
  description     TEXT NOT NULL DEFAULT '',
  topic           tour_topic NOT NULL,
  duration_min    INTEGER NOT NULL CHECK (duration_min IN (30,45,60,90)),
  price_cents     BIGINT NOT NULL CHECK (price_cents BETWEEN 2000 AND 20000),
  currency        CHAR(3) NOT NULL DEFAULT 'USD',
  languages       JSONB NOT NULL DEFAULT '["en-US"]',
  status          tour_status NOT NULL DEFAULT 'DRAFT',
  avg_rating      NUMERIC(3,2) NOT NULL DEFAULT 0,
  review_count    INTEGER NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_tour_guide_slug ON tour_offerings(guide_id, slug);
CREATE INDEX ix_tour_univ_status ON tour_offerings(university_id, status);
CREATE INDEX ix_tour_topic ON tour_offerings(topic);
CREATE TRIGGER trg_tour_updated BEFORE UPDATE ON tour_offerings FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- AVAILABILITY
-- =====================================================================
-- Recurring weekly availability for a guide (e.g. "Mondays 18:00-21:00"). day_of_week is
-- 0=Sunday; start/end are local to `timezone`. effective_from/to bound when the rule applies.
CREATE TABLE guide_availability_rules (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  guide_id        UUID NOT NULL REFERENCES guide_profiles(id) ON DELETE CASCADE,
  day_of_week     SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),  -- 0=Sunday
  start_local     TIME NOT NULL,
  end_local       TIME NOT NULL,
  timezone        TEXT NOT NULL DEFAULT 'America/Los_Angeles',
  effective_from  DATE NOT NULL DEFAULT CURRENT_DATE,
  effective_to    DATE,
  active          BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (start_local < end_local)
);
CREATE INDEX ix_avail_guide ON guide_availability_rules(guide_id, day_of_week);

-- One-off overrides to the weekly rules for a specific date: block the whole day, block a
-- time range, or add extra availability (`type`).
CREATE TABLE availability_exceptions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  guide_id        UUID NOT NULL REFERENCES guide_profiles(id) ON DELETE CASCADE,
  exception_date  DATE NOT NULL,
  type            availability_exception_type NOT NULL,
  start_local     TIME,
  end_local       TIME,
  reason          TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_avail_exc_guide ON availability_exceptions(guide_id, exception_date);

-- =====================================================================
-- BOOKING (aggregate root)
-- =====================================================================
-- The booking aggregate root: one tour reservation between a participant and a guide. Carries
-- an IMMUTABLE price snapshot (so later guide/offering price changes never alter a placed
-- booking) and a guide-reserved interval (scheduled time + buffers) used for overlap checks.
-- `version` is the optimistic-lock counter; the EXCLUDE constraints below prevent double-
-- booking the same guide or participant while a booking actively holds the slot.
CREATE TABLE bookings (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_number        TEXT NOT NULL UNIQUE,
  participant_user_id   UUID NOT NULL REFERENCES users(id),
  guide_id              UUID NOT NULL REFERENCES guide_profiles(id),
  tour_offering_id      UUID NOT NULL REFERENCES tour_offerings(id),
  university_id         UUID NOT NULL REFERENCES universities(id),
  status                booking_status NOT NULL DEFAULT 'DRAFT',
  acceptance_mode_snap  acceptance_mode NOT NULL,
  scheduled_start_at    TIMESTAMPTZ NOT NULL,
  scheduled_end_at      TIMESTAMPTZ NOT NULL,
  display_timezone      TEXT NOT NULL,
  -- guide-reserved interval includes buffers (for overlap checks)
  reserved_start_at     TIMESTAMPTZ NOT NULL,
  reserved_end_at       TIMESTAMPTZ NOT NULL,
  -- immutable price snapshot (cents)
  base_price_cents      BIGINT NOT NULL,
  service_fee_cents     BIGINT NOT NULL DEFAULT 0,
  tax_cents             BIGINT NOT NULL DEFAULT 0,
  total_cents           BIGINT NOT NULL,
  platform_fee_cents    BIGINT NOT NULL,
  guide_amount_cents    BIGINT NOT NULL,
  currency              CHAR(3) NOT NULL DEFAULT 'USD',
  participant_notes     TEXT,
  topics                JSONB NOT NULL DEFAULT '[]',
  guide_response_deadline_at TIMESTAMPTZ,
  cancellation_reason   TEXT,
  cancellation_actor    booking_actor,
  confirmed_at          TIMESTAMPTZ,
  started_at            TIMESTAMPTZ,
  completed_at          TIMESTAMPTZ,
  cancelled_at          TIMESTAMPTZ,
  version               BIGINT NOT NULL DEFAULT 0,   -- optimistic locking
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (scheduled_start_at < scheduled_end_at),
  CHECK (reserved_start_at <= scheduled_start_at AND reserved_end_at >= scheduled_end_at)
);
CREATE INDEX ix_bookings_participant ON bookings(participant_user_id, status);
CREATE INDEX ix_bookings_guide ON bookings(guide_id, status);
CREATE INDEX ix_bookings_start ON bookings(scheduled_start_at);
CREATE TRIGGER trg_bookings_updated BEFORE UPDATE ON bookings FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Prevent overlapping reserved intervals for the SAME guide while a booking holds the slot.
ALTER TABLE bookings ADD CONSTRAINT excl_guide_no_overlap
  EXCLUDE USING gist (
    guide_id WITH =,
    tstzrange(reserved_start_at, reserved_end_at) WITH &&
  )
  WHERE (status IN ('PENDING_PAYMENT_AUTH','PENDING_GUIDE_ACCEPTANCE','PAYMENT_ACTION_REQUIRED','CONFIRMED','IN_PROGRESS'));

-- Prevent a participant from holding overlapping bookings.
ALTER TABLE bookings ADD CONSTRAINT excl_participant_no_overlap
  EXCLUDE USING gist (
    participant_user_id WITH =,
    tstzrange(scheduled_start_at, scheduled_end_at) WITH &&
  )
  WHERE (status IN ('PENDING_PAYMENT_AUTH','PENDING_GUIDE_ACCEPTANCE','PAYMENT_ACTION_REQUIRED','CONFIRMED','IN_PROGRESS'));

-- Append-only audit trail of every booking status transition: previous/new status, who or
-- what changed it (`actor_type`/`actor_user_id`), and a correlation id for tracing.
CREATE TABLE booking_status_history (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  booking_id      UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  previous_status booking_status,
  new_status      booking_status NOT NULL,
  actor_type      booking_actor NOT NULL,
  actor_user_id   UUID REFERENCES users(id),
  reason_code     TEXT,
  correlation_id  TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_bsh_booking ON booking_status_history(booking_id, created_at);

-- =====================================================================
-- PAYMENT  (simplified MVP: one payment row per booking)
-- =====================================================================
-- One payment per booking (MVP, 1:1). Mirrors the Stripe PaymentIntent lifecycle
-- (`status`); amounts are in minor units (cents). `capture_before` bounds the
-- authorize-then-capture window.
CREATE TABLE payments (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id             UUID NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
  provider               TEXT NOT NULL DEFAULT 'STRIPE',
  provider_intent_id     TEXT,                          -- Stripe PaymentIntent id
  provider_charge_id     TEXT,
  status                 payment_status NOT NULL DEFAULT 'REQUIRES_PAYMENT_METHOD',
  authorized_cents       BIGINT,
  captured_cents         BIGINT,
  currency               CHAR(3) NOT NULL DEFAULT 'USD',
  capture_before         TIMESTAMPTZ,
  failure_code           TEXT,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_payments_updated BEFORE UPDATE ON payments FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- A refund against a booking's payment (full or partial). Amounts in cents;
-- `initiated_by` records which party triggered it.
CREATE TABLE refunds (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id         UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  payment_id         UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
  provider_refund_id TEXT,
  reason             refund_reason NOT NULL,
  status             refund_status NOT NULL DEFAULT 'REQUESTED',
  tour_refund_cents  BIGINT NOT NULL,
  tax_refund_cents   BIGINT NOT NULL DEFAULT 0,
  total_refund_cents BIGINT NOT NULL,
  initiated_by       booking_actor NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at       TIMESTAMPTZ,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refunds_booking ON refunds(booking_id);
CREATE TRIGGER trg_refunds_updated BEFORE UPDATE ON refunds FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Saved payment methods (Stripe is the source of truth; this is a local mirror for listing/default).
CREATE TABLE payment_methods (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  stripe_payment_method_id TEXT NOT NULL,
  brand                    TEXT,
  last4                    CHAR(4),
  exp_month                SMALLINT,
  exp_year                 SMALLINT,
  is_default               BOOLEAN NOT NULL DEFAULT FALSE,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, stripe_payment_method_id)
);
CREATE UNIQUE INDEX uq_default_payment_method ON payment_methods(user_id) WHERE is_default;

-- =====================================================================
-- LIVE SESSION
-- =====================================================================
-- The live video session for a booking (1:1). Wraps the video-provider room and tracks the
-- session lifecycle (`status`). recording_required / guardian_required snapshot the policy
-- that applied at session time (so later policy changes don't rewrite history).
CREATE TABLE live_sessions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id          UUID NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
  provider            TEXT NOT NULL DEFAULT 'DAILY',
  provider_room_name  TEXT,
  status              session_status NOT NULL DEFAULT 'SCHEDULED',
  recording_required  BOOLEAN NOT NULL DEFAULT TRUE,
  guardian_required   BOOLEAN NOT NULL DEFAULT FALSE,
  scheduled_start_at  TIMESTAMPTZ NOT NULL,
  scheduled_end_at    TIMESTAMPTZ NOT NULL,
  started_at          TIMESTAMPTZ,
  ended_at            TIMESTAMPTZ,
  ended_by            session_role,
  end_reason          TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_session_updated BEFORE UPDATE ON live_sessions FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- A user's membership in a live session (their `role`: guide / participant / guardian).
-- Tracks presence, the single active device, total connected seconds, and recording
-- acknowledgement. Unique per (session, user).
CREATE TABLE session_participants (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  live_session_id     UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
  user_id             UUID NOT NULL REFERENCES users(id),
  role                session_role NOT NULL,
  active_device_id    TEXT,                              -- enforces single active device
  connection_status   connection_status NOT NULL DEFAULT 'CONNECTING',
  first_joined_at     TIMESTAMPTZ,
  last_seen_at        TIMESTAMPTZ,
  total_seconds       BIGINT NOT NULL DEFAULT 0,
  recording_ack_at    TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (live_session_id, user_id)
);
CREATE INDEX ix_sp_session ON session_participants(live_session_id);

-- In-session chat messages. `client_message_id` makes a send idempotent (unique per sender,
-- so retries don't duplicate); `moderation_status` gates whether a message is shown.
CREATE TABLE session_messages (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  live_session_id     UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
  client_message_id   UUID NOT NULL,
  sender_user_id      UUID NOT NULL REFERENCES users(id),
  sender_role         session_role NOT NULL,
  message_type        message_type NOT NULL DEFAULT 'TEXT',
  content             TEXT NOT NULL,
  moderation_status   moderation_status NOT NULL DEFAULT 'ALLOWED',
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at          TIMESTAMPTZ,
  UNIQUE (sender_user_id, client_message_id)
);
CREATE INDEX ix_msg_session ON session_messages(live_session_id, created_at);

-- [DEFERRABLE for local MVP] A recording of a live session: provider id, storage key, and the
-- retention lifecycle (`status`, expires_at, deleted_at).
CREATE TABLE recordings (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  live_session_id     UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
  provider_recording_id TEXT,
  status              recording_status NOT NULL DEFAULT 'PENDING',
  storage_key         TEXT,
  duration_seconds    BIGINT,
  started_at          TIMESTAMPTZ,
  ended_at            TIMESTAMPTZ,
  expires_at          TIMESTAMPTZ,
  deleted_at          TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_recordings_session ON recordings(live_session_id);
CREATE TRIGGER trg_recordings_updated BEFORE UPDATE ON recordings FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- RESCHEDULE
-- =====================================================================
-- A proposed new time for a booking, awaiting the counterparty's response. At most one active
-- (PENDING_COUNTERPARTY) proposal per booking (partial unique index below). May carry a
-- reschedule fee and a price difference vs the original booking.
CREATE TABLE reschedule_proposals (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id          UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  requested_by        booking_actor NOT NULL,
  requested_by_user_id UUID REFERENCES users(id),
  proposed_start_at   TIMESTAMPTZ NOT NULL,
  proposed_end_at     TIMESTAMPTZ NOT NULL,
  status              reschedule_status NOT NULL DEFAULT 'PENDING_COUNTERPARTY',
  fee_cents           BIGINT NOT NULL DEFAULT 0,
  price_diff_cents    BIGINT NOT NULL DEFAULT 0,
  expires_at          TIMESTAMPTZ NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (proposed_start_at < proposed_end_at)
);
CREATE UNIQUE INDEX uq_reschedule_active ON reschedule_proposals(booking_id)
  WHERE status = 'PENDING_COUNTERPARTY';
CREATE TRIGGER trg_resched_updated BEFORE UPDATE ON reschedule_proposals FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- REVIEW
-- =====================================================================
-- A participant's post-tour review of a guide/offering (1:1 with booking). overall_rating is
-- required 1-5; the sub-ratings are optional. `private_feedback` is never shown publicly;
-- `status` gates moderation/publishing. Feeds the denormalized counters on guide_profiles.
CREATE TABLE reviews (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id          UUID NOT NULL UNIQUE REFERENCES bookings(id) ON DELETE CASCADE,
  participant_user_id UUID NOT NULL REFERENCES users(id),
  guide_id            UUID NOT NULL REFERENCES guide_profiles(id),
  tour_offering_id    UUID NOT NULL REFERENCES tour_offerings(id),
  overall_rating      SMALLINT NOT NULL CHECK (overall_rating BETWEEN 1 AND 5),
  knowledge_rating    SMALLINT CHECK (knowledge_rating BETWEEN 1 AND 5),
  communication_rating SMALLINT CHECK (communication_rating BETWEEN 1 AND 5),
  friendliness_rating SMALLINT CHECK (friendliness_rating BETWEEN 1 AND 5),
  helpfulness_rating  SMALLINT CHECK (helpfulness_rating BETWEEN 1 AND 5),
  comment             TEXT,
  private_feedback    TEXT,
  status              review_status NOT NULL DEFAULT 'PENDING_MODERATION',
  guide_response      TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at        TIMESTAMPTZ,
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reviews_guide ON reviews(guide_id, status);
CREATE TRIGGER trg_reviews_updated BEFORE UPDATE ON reviews FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =====================================================================
-- INFRA / RELIABILITY
-- =====================================================================
-- Idempotency ledger: dedupes retried mutations. Unique per (operation, idempotency_key);
-- the stored response lets a replay return the same result without re-running the work.
-- `request_hash` detects key reuse with a different body; `expires_at` bounds the TTL.
CREATE TABLE idempotency_keys (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id   UUID REFERENCES users(id),
  idempotency_key TEXT NOT NULL,
  operation       TEXT NOT NULL,
  request_hash    TEXT NOT NULL,
  response_status INTEGER,
  response_body   JSONB,
  resource_id     UUID,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at      TIMESTAMPTZ NOT NULL,
  UNIQUE (operation, idempotency_key)
);
CREATE INDEX ix_idem_expires ON idempotency_keys(expires_at);

-- [DEFERRABLE for local MVP] Transactional outbox: domain events written in the SAME
-- transaction as the state change, then relayed to the message bus by a publisher
-- (`status`: PENDING → PUBLISHED / FAILED, with retry via attempt_count / available_at).
CREATE TABLE outbox_events (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_type  TEXT NOT NULL,
  aggregate_id    UUID NOT NULL,
  event_type      TEXT NOT NULL,
  schema_version  INTEGER NOT NULL DEFAULT 1,
  payload         JSONB NOT NULL,
  status          TEXT NOT NULL DEFAULT 'PENDING',   -- PENDING|PUBLISHED|FAILED
  attempt_count   INTEGER NOT NULL DEFAULT 0,
  available_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_pending ON outbox_events(status, available_at) WHERE status = 'PENDING';

-- [DEFERRABLE for local MVP] A notification queued/sent to a user over a channel (email / SMS /
-- push). `status` tracks SCHEDULED → sent/read; in local dev these are just console-logged.
CREATE TABLE notifications (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type            TEXT NOT NULL,
  channel         notification_channel NOT NULL,
  title           TEXT NOT NULL,
  body            TEXT NOT NULL,
  booking_id      UUID REFERENCES bookings(id) ON DELETE SET NULL,
  status          notification_status NOT NULL DEFAULT 'SCHEDULED',
  scheduled_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at         TIMESTAMPTZ,
  read_at         TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notif_user ON notifications(user_id, created_at);

-- [DEFERRABLE for local MVP] Append-only audit log of privileged/staff actions: who did what
-- (`action`) to which target (`target_type`/`target_id`) and why. `metadata` holds action-specific detail.
CREATE TABLE audit_log (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor_user_id   UUID REFERENCES users(id),
  action          TEXT NOT NULL,
  target_type     TEXT NOT NULL,
  target_id       TEXT,
  reason_code     TEXT,
  metadata        JSONB,
  occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_target ON audit_log(target_type, target_id);

-- END V1
