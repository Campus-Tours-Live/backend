-- =====================================================================
-- CampusToursLive.ai — consolidated schema baseline (V1)
-- Squash of the original V1(schema)+V3(drop bookings.display_timezone)+
-- V4(availability engine v2)+V5(dst notices) into one baseline. V2 (seed)
-- stays a separate migration. Generated via pg_dump of the net schema; the
-- original hand-written migrations are in git history before this commit.
-- =====================================================================

--
-- PostgreSQL database dump
--


-- Dumped from database version 15.18 (Debian 15.18-1.pgdg13+1)
-- Dumped by pg_dump version 15.18 (Debian 15.18-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: citext; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS citext WITH SCHEMA public;


--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: acceptance_mode; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.acceptance_mode AS ENUM (
    'AUTO',
    'MANUAL'
);


--
-- Name: account_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.account_status AS ENUM (
    'PENDING_VERIFICATION',
    'ACTIVE',
    'SUSPENDED',
    'DELETED'
);


--
-- Name: age_band; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.age_band AS ENUM (
    'ADULT',
    'MINOR_16_17',
    'UNDER_16'
);


--
-- Name: availability_exception_kind; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.availability_exception_kind AS ENUM (
    'UNAVAILABLE',
    'ADDITIONAL'
);


--
-- Name: booking_actor; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.booking_actor AS ENUM (
    'PARTICIPANT',
    'GUIDE',
    'SYSTEM',
    'ADMIN',
    'STRIPE_WEBHOOK',
    'DAILY_WEBHOOK'
);


--
-- Name: booking_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.booking_status AS ENUM (
    'DRAFT',
    'PENDING_PAYMENT_AUTH',
    'PENDING_GUIDE_ACCEPTANCE',
    'PAYMENT_ACTION_REQUIRED',
    'CONFIRMED',
    'DECLINED_BY_GUIDE',
    'EXPIRED',
    'CANCELLED_BY_PARTICIPANT',
    'CANCELLED_BY_GUIDE',
    'IN_PROGRESS',
    'COMPLETED',
    'PARTICIPANT_NO_SHOW',
    'GUIDE_NO_SHOW',
    'TECHNICAL_FAILURE',
    'REFUND_PENDING',
    'REFUNDED'
);


--
-- Name: connection_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.connection_status AS ENUM (
    'CONNECTING',
    'ACTIVE',
    'DISCONNECTED',
    'REPLACED'
);


--
-- Name: guardian_consent_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.guardian_consent_status AS ENUM (
    'PENDING',
    'PHONE_VERIFIED',
    'ACCEPTED',
    'REVOKED',
    'EXPIRED'
);


--
-- Name: guide_application_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.guide_application_status AS ENUM (
    'PENDING',
    'VERIFIED',
    'REJECTED'
);


--
-- Name: guide_verification_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.guide_verification_status AS ENUM (
    'NOT_SUBMITTED',
    'PENDING',
    'VERIFIED',
    'REJECTED',
    'EXPIRED'
);


--
-- Name: message_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.message_type AS ENUM (
    'TEXT',
    'QA',
    'SYSTEM'
);


--
-- Name: moderation_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.moderation_status AS ENUM (
    'ALLOWED',
    'BLOCKED',
    'FLAGGED',
    'REMOVED'
);


--
-- Name: notification_channel; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.notification_channel AS ENUM (
    'EMAIL',
    'SMS',
    'IN_APP'
);


--
-- Name: notification_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.notification_status AS ENUM (
    'SCHEDULED',
    'SENT',
    'FAILED',
    'CANCELLED'
);


--
-- Name: participant_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.participant_type AS ENUM (
    'HIGH_SCHOOL',
    'PROSPECTIVE',
    'TRANSFER',
    'INTERNATIONAL',
    'PARENT',
    'OTHER'
);


--
-- Name: payment_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.payment_status AS ENUM (
    'REQUIRES_PAYMENT_METHOD',
    'AUTHORIZED',
    'CAPTURE_PENDING',
    'CAPTURED',
    'CANCELLED',
    'FAILED',
    'PARTIALLY_REFUNDED',
    'REFUNDED',
    'DISPUTED'
);


--
-- Name: recording_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.recording_status AS ENUM (
    'PENDING',
    'RECORDING',
    'PROCESSING',
    'AVAILABLE',
    'FAILED',
    'DELETED'
);


--
-- Name: refund_reason; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.refund_reason AS ENUM (
    'PARTICIPANT_CANCELLATION',
    'GUIDE_CANCELLATION',
    'GUIDE_NO_SHOW',
    'TECHNICAL_FAILURE',
    'GUARDIAN_FAILURE',
    'SUPPORT_ADJUSTMENT'
);


--
-- Name: refund_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.refund_status AS ENUM (
    'REQUESTED',
    'PROCESSING',
    'SUCCEEDED',
    'FAILED'
);


--
-- Name: reschedule_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.reschedule_status AS ENUM (
    'PENDING_COUNTERPARTY',
    'ACCEPTED',
    'DECLINED',
    'EXPIRED',
    'CANCELLED'
);


--
-- Name: review_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.review_status AS ENUM (
    'PENDING_MODERATION',
    'PUBLISHED',
    'REMOVED'
);


--
-- Name: session_role; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.session_role AS ENUM (
    'PARTICIPANT',
    'GUIDE'
);


--
-- Name: session_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.session_status AS ENUM (
    'SCHEDULED',
    'WAITING',
    'IN_PROGRESS',
    'PAUSED_GUARDIAN',
    'ENDED',
    'CANCELLED',
    'FAILED'
);


--
-- Name: tour_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.tour_status AS ENUM (
    'DRAFT',
    'ACTIVE',
    'PAUSED',
    'ARCHIVED'
);


--
-- Name: tour_topic; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.tour_topic AS ENUM (
    'GENERAL_CAMPUS',
    'DORM_HOUSING',
    'DINING_STUDENT_LIFE',
    'MAJOR_SPECIFIC',
    'INTERNATIONAL_STUDENT',
    'PARENT_FOCUSED',
    'FRESHMAN',
    'TRANSFER'
);


--
-- Name: university_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.university_status AS ENUM (
    'ACTIVE',
    'PAUSED',
    'ARCHIVED'
);


--
-- Name: user_role; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.user_role AS ENUM (
    'PARTICIPANT',
    'GUIDE',
    'ADMIN',
    'SUPPORT'
);


--
-- Name: set_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_log (
    id bigint NOT NULL,
    actor_user_id uuid,
    action text NOT NULL,
    target_type text NOT NULL,
    target_id text,
    reason_code text,
    metadata jsonb,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.audit_log ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: availability_exceptions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.availability_exceptions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    guide_id uuid NOT NULL,
    exception_date date NOT NULL,
    kind public.availability_exception_kind NOT NULL,
    start_local time without time zone NOT NULL,
    window_min integer NOT NULL,
    reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT availability_exceptions_window_min_check CHECK ((window_min > 0))
);


--
-- Name: booking_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.booking_status_history (
    id bigint NOT NULL,
    booking_id uuid NOT NULL,
    previous_status public.booking_status,
    new_status public.booking_status NOT NULL,
    actor_type public.booking_actor NOT NULL,
    actor_user_id uuid,
    reason_code text,
    correlation_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: booking_status_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.booking_status_history ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.booking_status_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: bookings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bookings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    booking_number text NOT NULL,
    participant_user_id uuid NOT NULL,
    guide_id uuid NOT NULL,
    tour_offering_id uuid NOT NULL,
    university_id uuid NOT NULL,
    status public.booking_status DEFAULT 'DRAFT'::public.booking_status NOT NULL,
    acceptance_mode_snap public.acceptance_mode NOT NULL,
    scheduled_start_at timestamp with time zone NOT NULL,
    scheduled_end_at timestamp with time zone NOT NULL,
    reserved_start_at timestamp with time zone NOT NULL,
    reserved_end_at timestamp with time zone NOT NULL,
    base_price_cents bigint NOT NULL,
    service_fee_cents bigint DEFAULT 0 NOT NULL,
    tax_cents bigint DEFAULT 0 NOT NULL,
    total_cents bigint NOT NULL,
    platform_fee_cents bigint NOT NULL,
    guide_amount_cents bigint NOT NULL,
    currency character(3) DEFAULT 'USD'::bpchar NOT NULL,
    participant_notes text,
    topics jsonb DEFAULT '[]'::jsonb NOT NULL,
    guide_response_deadline_at timestamp with time zone,
    cancellation_reason text,
    cancellation_actor public.booking_actor,
    confirmed_at timestamp with time zone,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT bookings_check CHECK ((scheduled_start_at < scheduled_end_at)),
    CONSTRAINT bookings_check1 CHECK (((reserved_start_at <= scheduled_start_at) AND (reserved_end_at >= scheduled_end_at)))
);


--
-- Name: guardian_consents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.guardian_consents (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    guardian_id uuid NOT NULL,
    participant_user_id uuid NOT NULL,
    status public.guardian_consent_status DEFAULT 'PENDING'::public.guardian_consent_status NOT NULL,
    link_token_hash text NOT NULL,
    terms_version text,
    privacy_version text,
    recording_policy_version text,
    accepted_at timestamp with time zone,
    revoked_at timestamp with time zone,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: guardians; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.guardians (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    participant_user_id uuid NOT NULL,
    full_name text NOT NULL,
    first_name text,
    last_name text,
    relationship text NOT NULL,
    phone_hash text NOT NULL,
    phone_verified_at timestamp with time zone,
    email_enc text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: guide_availability_dst_notices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.guide_availability_dst_notices (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    guide_id uuid NOT NULL,
    adjusted_date date NOT NULL,
    generated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: guide_availability_occurrences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.guide_availability_occurrences (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    guide_id uuid NOT NULL,
    during_start_at timestamp with time zone NOT NULL,
    during_end_at timestamp with time zone NOT NULL,
    source_rule_id uuid,
    source_exception_id uuid,
    generated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT guide_availability_occurrences_check CHECK ((during_start_at < during_end_at))
);


--
-- Name: guide_availability_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.guide_availability_rules (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    guide_id uuid NOT NULL,
    day_of_week smallint NOT NULL,
    start_local time without time zone NOT NULL,
    window_min integer NOT NULL,
    timezone text DEFAULT 'America/Los_Angeles'::text NOT NULL,
    effective_from date DEFAULT CURRENT_DATE NOT NULL,
    effective_to date,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT guide_availability_rules_day_of_week_check CHECK (((day_of_week >= 0) AND (day_of_week <= 6))),
    CONSTRAINT guide_availability_rules_window_min_check CHECK ((window_min > 0))
);


--
-- Name: guide_booking_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.guide_booking_settings (
    guide_id uuid NOT NULL,
    acceptance_mode public.acceptance_mode DEFAULT 'MANUAL'::public.acceptance_mode NOT NULL,
    response_deadline_min integer DEFAULT 90 NOT NULL,
    min_notice_min integer DEFAULT 1440 NOT NULL,
    max_advance_days integer DEFAULT 30 NOT NULL,
    buffer_before_min integer DEFAULT 0 NOT NULL,
    buffer_after_min integer DEFAULT 15 NOT NULL,
    durations_offered jsonb DEFAULT '[30, 45, 60, 90]'::jsonb NOT NULL,
    timezone text DEFAULT 'America/Los_Angeles'::text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: guide_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.guide_profiles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    bio text,
    spoken_languages jsonb DEFAULT '["en-US"]'::jsonb NOT NULL,
    tour_topics jsonb DEFAULT '[]'::jsonb NOT NULL,
    favorite_spots jsonb DEFAULT '[]'::jsonb NOT NULL,
    guide_status public.guide_application_status DEFAULT 'PENDING'::public.guide_application_status NOT NULL,
    avg_rating numeric(3,2) DEFAULT 0 NOT NULL,
    review_count integer DEFAULT 0 NOT NULL,
    completed_tours integer DEFAULT 0 NOT NULL,
    stripe_account_id text,
    payouts_enabled boolean DEFAULT false NOT NULL,
    approved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: idempotency_keys; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.idempotency_keys (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    actor_user_id uuid,
    idempotency_key text NOT NULL,
    operation text NOT NULL,
    request_hash text NOT NULL,
    response_status integer,
    response_body jsonb,
    resource_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL
);


--
-- Name: live_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.live_sessions (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    booking_id uuid NOT NULL,
    provider text DEFAULT 'DAILY'::text NOT NULL,
    provider_room_name text,
    status public.session_status DEFAULT 'SCHEDULED'::public.session_status NOT NULL,
    recording_required boolean DEFAULT true NOT NULL,
    guardian_required boolean DEFAULT false NOT NULL,
    scheduled_start_at timestamp with time zone NOT NULL,
    scheduled_end_at timestamp with time zone NOT NULL,
    started_at timestamp with time zone,
    ended_at timestamp with time zone,
    ended_by public.session_role,
    end_reason text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    type text NOT NULL,
    channel public.notification_channel NOT NULL,
    title text NOT NULL,
    body text NOT NULL,
    booking_id uuid,
    status public.notification_status DEFAULT 'SCHEDULED'::public.notification_status NOT NULL,
    scheduled_at timestamp with time zone DEFAULT now() NOT NULL,
    sent_at timestamp with time zone,
    read_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: outbox_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbox_events (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    aggregate_type text NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type text NOT NULL,
    schema_version integer DEFAULT 1 NOT NULL,
    payload jsonb NOT NULL,
    status text DEFAULT 'PENDING'::text NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    available_at timestamp with time zone DEFAULT now() NOT NULL,
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: participant_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.participant_profiles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    participant_type public.participant_type DEFAULT 'PROSPECTIVE'::public.participant_type NOT NULL,
    grade_level text,
    intended_major text,
    interests jsonb DEFAULT '[]'::jsonb NOT NULL,
    guardian_required boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: payment_methods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment_methods (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    stripe_payment_method_id text NOT NULL,
    brand text,
    last4 character(4),
    exp_month smallint,
    exp_year smallint,
    is_default boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payments (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    booking_id uuid NOT NULL,
    provider text DEFAULT 'STRIPE'::text NOT NULL,
    provider_intent_id text,
    provider_charge_id text,
    status public.payment_status DEFAULT 'REQUIRES_PAYMENT_METHOD'::public.payment_status NOT NULL,
    authorized_cents bigint,
    captured_cents bigint,
    currency character(3) DEFAULT 'USD'::bpchar NOT NULL,
    capture_before timestamp with time zone,
    failure_code text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: recordings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recordings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    live_session_id uuid NOT NULL,
    provider_recording_id text,
    status public.recording_status DEFAULT 'PENDING'::public.recording_status NOT NULL,
    storage_key text,
    duration_seconds bigint,
    started_at timestamp with time zone,
    ended_at timestamp with time zone,
    expires_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: refunds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refunds (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    booking_id uuid NOT NULL,
    payment_id uuid NOT NULL,
    provider_refund_id text,
    reason public.refund_reason NOT NULL,
    status public.refund_status DEFAULT 'REQUESTED'::public.refund_status NOT NULL,
    tour_refund_cents bigint NOT NULL,
    tax_refund_cents bigint DEFAULT 0 NOT NULL,
    total_refund_cents bigint NOT NULL,
    initiated_by public.booking_actor NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: reschedule_proposals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reschedule_proposals (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    booking_id uuid NOT NULL,
    requested_by public.booking_actor NOT NULL,
    requested_by_user_id uuid,
    proposed_start_at timestamp with time zone NOT NULL,
    proposed_end_at timestamp with time zone NOT NULL,
    status public.reschedule_status DEFAULT 'PENDING_COUNTERPARTY'::public.reschedule_status NOT NULL,
    fee_cents bigint DEFAULT 0 NOT NULL,
    price_diff_cents bigint DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT reschedule_proposals_check CHECK ((proposed_start_at < proposed_end_at))
);


--
-- Name: reviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reviews (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    booking_id uuid NOT NULL,
    participant_user_id uuid NOT NULL,
    guide_id uuid NOT NULL,
    tour_offering_id uuid NOT NULL,
    overall_rating smallint NOT NULL,
    knowledge_rating smallint,
    communication_rating smallint,
    friendliness_rating smallint,
    helpfulness_rating smallint,
    comment text,
    private_feedback text,
    status public.review_status DEFAULT 'PENDING_MODERATION'::public.review_status NOT NULL,
    guide_response text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    published_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT reviews_communication_rating_check CHECK (((communication_rating >= 1) AND (communication_rating <= 5))),
    CONSTRAINT reviews_friendliness_rating_check CHECK (((friendliness_rating >= 1) AND (friendliness_rating <= 5))),
    CONSTRAINT reviews_helpfulness_rating_check CHECK (((helpfulness_rating >= 1) AND (helpfulness_rating <= 5))),
    CONSTRAINT reviews_knowledge_rating_check CHECK (((knowledge_rating >= 1) AND (knowledge_rating <= 5))),
    CONSTRAINT reviews_overall_rating_check CHECK (((overall_rating >= 1) AND (overall_rating <= 5)))
);


--
-- Name: session_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session_messages (
    id bigint NOT NULL,
    live_session_id uuid NOT NULL,
    client_message_id uuid NOT NULL,
    sender_user_id uuid NOT NULL,
    sender_role public.session_role NOT NULL,
    message_type public.message_type DEFAULT 'TEXT'::public.message_type NOT NULL,
    content text NOT NULL,
    moderation_status public.moderation_status DEFAULT 'ALLOWED'::public.moderation_status NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: session_messages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.session_messages ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.session_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: session_participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.session_participants (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    live_session_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role public.session_role NOT NULL,
    active_device_id text,
    connection_status public.connection_status DEFAULT 'CONNECTING'::public.connection_status NOT NULL,
    first_joined_at timestamp with time zone,
    last_seen_at timestamp with time zone,
    total_seconds bigint DEFAULT 0 NOT NULL,
    recording_ack_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: tour_offerings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tour_offerings (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    guide_id uuid NOT NULL,
    university_id uuid NOT NULL,
    title text NOT NULL,
    slug text NOT NULL,
    description text DEFAULT ''::text NOT NULL,
    topic public.tour_topic NOT NULL,
    duration_min integer NOT NULL,
    price_cents bigint NOT NULL,
    currency character(3) DEFAULT 'USD'::bpchar NOT NULL,
    languages jsonb DEFAULT '["en-US"]'::jsonb NOT NULL,
    features jsonb DEFAULT '[]'::jsonb NOT NULL,
    status public.tour_status DEFAULT 'DRAFT'::public.tour_status NOT NULL,
    avg_rating numeric(3,2) DEFAULT 0 NOT NULL,
    review_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT tour_offerings_duration_min_check CHECK ((duration_min = ANY (ARRAY[30, 45, 60, 90]))),
    CONSTRAINT tour_offerings_price_cents_check CHECK (((price_cents >= 2000) AND (price_cents <= 20000)))
);


--
-- Name: universities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.universities (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    slug text NOT NULL,
    name text NOT NULL,
    short_name text,
    city text NOT NULL,
    region text,
    country_code character(2) DEFAULT 'US'::bpchar NOT NULL,
    timezone text DEFAULT 'America/Los_Angeles'::text NOT NULL,
    image_url text,
    status public.university_status DEFAULT 'ACTIVE'::public.university_status NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_roles (
    user_id uuid NOT NULL,
    role public.user_role NOT NULL,
    granted_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    oidc_subject text,
    email public.citext,
    email_verified boolean DEFAULT false NOT NULL,
    account_status public.account_status DEFAULT 'PENDING_VERIFICATION'::public.account_status NOT NULL,
    date_of_birth date,
    age_band public.age_band,
    first_name text,
    last_name text,
    display_name text NOT NULL,
    preferred_language text DEFAULT 'en-US'::text NOT NULL,
    timezone text DEFAULT 'America/Los_Angeles'::text NOT NULL,
    stripe_customer_id text,
    last_login_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: availability_exceptions availability_exceptions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.availability_exceptions
    ADD CONSTRAINT availability_exceptions_pkey PRIMARY KEY (id);


--
-- Name: booking_status_history booking_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking_status_history
    ADD CONSTRAINT booking_status_history_pkey PRIMARY KEY (id);


--
-- Name: bookings bookings_booking_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT bookings_booking_number_key UNIQUE (booking_number);


--
-- Name: bookings bookings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT bookings_pkey PRIMARY KEY (id);


--
-- Name: bookings excl_guide_no_overlap; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT excl_guide_no_overlap EXCLUDE USING gist (guide_id WITH =, tstzrange(reserved_start_at, reserved_end_at) WITH &&) WHERE ((status = ANY (ARRAY['PENDING_PAYMENT_AUTH'::public.booking_status, 'PENDING_GUIDE_ACCEPTANCE'::public.booking_status, 'PAYMENT_ACTION_REQUIRED'::public.booking_status, 'CONFIRMED'::public.booking_status, 'IN_PROGRESS'::public.booking_status])));


--
-- Name: guide_availability_occurrences excl_guide_occurrence_no_overlap; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_availability_occurrences
    ADD CONSTRAINT excl_guide_occurrence_no_overlap EXCLUDE USING gist (guide_id WITH =, tstzrange(during_start_at, during_end_at) WITH &&);


--
-- Name: bookings excl_participant_no_overlap; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT excl_participant_no_overlap EXCLUDE USING gist (participant_user_id WITH =, tstzrange(scheduled_start_at, scheduled_end_at) WITH &&) WHERE ((status = ANY (ARRAY['PENDING_PAYMENT_AUTH'::public.booking_status, 'PENDING_GUIDE_ACCEPTANCE'::public.booking_status, 'PAYMENT_ACTION_REQUIRED'::public.booking_status, 'CONFIRMED'::public.booking_status, 'IN_PROGRESS'::public.booking_status])));


--
-- Name: guardian_consents guardian_consents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guardian_consents
    ADD CONSTRAINT guardian_consents_pkey PRIMARY KEY (id);


--
-- Name: guardians guardians_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guardians
    ADD CONSTRAINT guardians_pkey PRIMARY KEY (id);


--
-- Name: guide_availability_dst_notices guide_availability_dst_notices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_availability_dst_notices
    ADD CONSTRAINT guide_availability_dst_notices_pkey PRIMARY KEY (id);


--
-- Name: guide_availability_occurrences guide_availability_occurrences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_availability_occurrences
    ADD CONSTRAINT guide_availability_occurrences_pkey PRIMARY KEY (id);


--
-- Name: guide_availability_rules guide_availability_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_availability_rules
    ADD CONSTRAINT guide_availability_rules_pkey PRIMARY KEY (id);


--
-- Name: guide_booking_settings guide_booking_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_booking_settings
    ADD CONSTRAINT guide_booking_settings_pkey PRIMARY KEY (guide_id);


--
-- Name: guide_profiles guide_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_profiles
    ADD CONSTRAINT guide_profiles_pkey PRIMARY KEY (id);


--
-- Name: guide_profiles guide_profiles_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_profiles
    ADD CONSTRAINT guide_profiles_user_id_key UNIQUE (user_id);


--
-- Name: idempotency_keys idempotency_keys_operation_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_keys
    ADD CONSTRAINT idempotency_keys_operation_idempotency_key_key UNIQUE (operation, idempotency_key);


--
-- Name: idempotency_keys idempotency_keys_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_keys
    ADD CONSTRAINT idempotency_keys_pkey PRIMARY KEY (id);


--
-- Name: live_sessions live_sessions_booking_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.live_sessions
    ADD CONSTRAINT live_sessions_booking_id_key UNIQUE (booking_id);


--
-- Name: live_sessions live_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.live_sessions
    ADD CONSTRAINT live_sessions_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: outbox_events outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (id);


--
-- Name: participant_profiles participant_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.participant_profiles
    ADD CONSTRAINT participant_profiles_pkey PRIMARY KEY (id);


--
-- Name: participant_profiles participant_profiles_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.participant_profiles
    ADD CONSTRAINT participant_profiles_user_id_key UNIQUE (user_id);


--
-- Name: payment_methods payment_methods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_methods
    ADD CONSTRAINT payment_methods_pkey PRIMARY KEY (id);


--
-- Name: payment_methods payment_methods_user_id_stripe_payment_method_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_methods
    ADD CONSTRAINT payment_methods_user_id_stripe_payment_method_id_key UNIQUE (user_id, stripe_payment_method_id);


--
-- Name: payments payments_booking_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_booking_id_key UNIQUE (booking_id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: recordings recordings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recordings
    ADD CONSTRAINT recordings_pkey PRIMARY KEY (id);


--
-- Name: refunds refunds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_pkey PRIMARY KEY (id);


--
-- Name: reschedule_proposals reschedule_proposals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reschedule_proposals
    ADD CONSTRAINT reschedule_proposals_pkey PRIMARY KEY (id);


--
-- Name: reviews reviews_booking_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_booking_id_key UNIQUE (booking_id);


--
-- Name: reviews reviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_pkey PRIMARY KEY (id);


--
-- Name: session_messages session_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_messages
    ADD CONSTRAINT session_messages_pkey PRIMARY KEY (id);


--
-- Name: session_messages session_messages_sender_user_id_client_message_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_messages
    ADD CONSTRAINT session_messages_sender_user_id_client_message_id_key UNIQUE (sender_user_id, client_message_id);


--
-- Name: session_participants session_participants_live_session_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT session_participants_live_session_id_user_id_key UNIQUE (live_session_id, user_id);


--
-- Name: session_participants session_participants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT session_participants_pkey PRIMARY KEY (id);


--
-- Name: tour_offerings tour_offerings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tour_offerings
    ADD CONSTRAINT tour_offerings_pkey PRIMARY KEY (id);


--
-- Name: universities universities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.universities
    ADD CONSTRAINT universities_pkey PRIMARY KEY (id);


--
-- Name: universities universities_slug_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.universities
    ADD CONSTRAINT universities_slug_key UNIQUE (slug);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role);


--
-- Name: users users_oidc_subject_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_oidc_subject_key UNIQUE (oidc_subject);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_stripe_customer_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_stripe_customer_id_key UNIQUE (stripe_customer_id);


--
-- Name: ix_audit_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_target ON public.audit_log USING btree (target_type, target_id);


--
-- Name: ix_avail_dst_guide; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_avail_dst_guide ON public.guide_availability_dst_notices USING btree (guide_id);


--
-- Name: ix_avail_exc_guide; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_avail_exc_guide ON public.availability_exceptions USING btree (guide_id, exception_date);


--
-- Name: ix_avail_guide; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_avail_guide ON public.guide_availability_rules USING btree (guide_id, day_of_week);


--
-- Name: ix_avail_occ_guide; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_avail_occ_guide ON public.guide_availability_occurrences USING btree (guide_id, during_start_at);


--
-- Name: ix_bookings_guide; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_bookings_guide ON public.bookings USING btree (guide_id, status);


--
-- Name: ix_bookings_participant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_bookings_participant ON public.bookings USING btree (participant_user_id, status);


--
-- Name: ix_bookings_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_bookings_start ON public.bookings USING btree (scheduled_start_at);


--
-- Name: ix_bsh_booking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_bsh_booking ON public.booking_status_history USING btree (booking_id, created_at);


--
-- Name: ix_consents_participant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_consents_participant ON public.guardian_consents USING btree (participant_user_id);


--
-- Name: ix_guardians_participant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_guardians_participant ON public.guardians USING btree (participant_user_id);


--
-- Name: ix_guide_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_guide_status ON public.guide_profiles USING btree (guide_status);


--
-- Name: ix_idem_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_idem_expires ON public.idempotency_keys USING btree (expires_at);


--
-- Name: ix_msg_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_msg_session ON public.session_messages USING btree (live_session_id, created_at);


--
-- Name: ix_notif_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_notif_user ON public.notifications USING btree (user_id, created_at);


--
-- Name: ix_outbox_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_outbox_pending ON public.outbox_events USING btree (status, available_at) WHERE (status = 'PENDING'::text);


--
-- Name: ix_recordings_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_recordings_session ON public.recordings USING btree (live_session_id);


--
-- Name: ix_refunds_booking; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_refunds_booking ON public.refunds USING btree (booking_id);


--
-- Name: ix_reviews_guide; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_reviews_guide ON public.reviews USING btree (guide_id, status);


--
-- Name: ix_sp_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_sp_session ON public.session_participants USING btree (live_session_id);


--
-- Name: ix_tour_topic; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_tour_topic ON public.tour_offerings USING btree (topic);


--
-- Name: ix_tour_univ_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_tour_univ_status ON public.tour_offerings USING btree (university_id, status);


--
-- Name: uq_default_payment_method; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_default_payment_method ON public.payment_methods USING btree (user_id) WHERE is_default;


--
-- Name: uq_reschedule_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_reschedule_active ON public.reschedule_proposals USING btree (booking_id) WHERE (status = 'PENDING_COUNTERPARTY'::public.reschedule_status);


--
-- Name: uq_tour_guide_slug; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_tour_guide_slug ON public.tour_offerings USING btree (guide_id, slug);


--
-- Name: uq_users_email_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_users_email_active ON public.users USING btree (lower((email)::text)) WHERE ((deleted_at IS NULL) AND (email IS NOT NULL));


--
-- Name: bookings trg_bookings_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_bookings_updated BEFORE UPDATE ON public.bookings FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: guardian_consents trg_consents_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_consents_updated BEFORE UPDATE ON public.guardian_consents FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: guide_booking_settings trg_gset_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_gset_updated BEFORE UPDATE ON public.guide_booking_settings FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: guardians trg_guardians_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_guardians_updated BEFORE UPDATE ON public.guardians FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: guide_profiles trg_guide_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_guide_updated BEFORE UPDATE ON public.guide_profiles FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: payments trg_payments_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_payments_updated BEFORE UPDATE ON public.payments FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: participant_profiles trg_pprofiles_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_pprofiles_updated BEFORE UPDATE ON public.participant_profiles FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: recordings trg_recordings_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_recordings_updated BEFORE UPDATE ON public.recordings FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: refunds trg_refunds_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_refunds_updated BEFORE UPDATE ON public.refunds FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: reschedule_proposals trg_resched_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_resched_updated BEFORE UPDATE ON public.reschedule_proposals FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: reviews trg_reviews_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_reviews_updated BEFORE UPDATE ON public.reviews FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: live_sessions trg_session_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_session_updated BEFORE UPDATE ON public.live_sessions FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: tour_offerings trg_tour_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_tour_updated BEFORE UPDATE ON public.tour_offerings FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: universities trg_univ_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_univ_updated BEFORE UPDATE ON public.universities FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: users trg_users_updated; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_users_updated BEFORE UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();


--
-- Name: audit_log audit_log_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_log
    ADD CONSTRAINT audit_log_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(id);


--
-- Name: availability_exceptions availability_exceptions_guide_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.availability_exceptions
    ADD CONSTRAINT availability_exceptions_guide_id_fkey FOREIGN KEY (guide_id) REFERENCES public.guide_profiles(id) ON DELETE CASCADE;


--
-- Name: booking_status_history booking_status_history_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking_status_history
    ADD CONSTRAINT booking_status_history_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(id);


--
-- Name: booking_status_history booking_status_history_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.booking_status_history
    ADD CONSTRAINT booking_status_history_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE CASCADE;


--
-- Name: bookings bookings_guide_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT bookings_guide_id_fkey FOREIGN KEY (guide_id) REFERENCES public.guide_profiles(id);


--
-- Name: bookings bookings_participant_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT bookings_participant_user_id_fkey FOREIGN KEY (participant_user_id) REFERENCES public.users(id);


--
-- Name: bookings bookings_tour_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT bookings_tour_offering_id_fkey FOREIGN KEY (tour_offering_id) REFERENCES public.tour_offerings(id);


--
-- Name: bookings bookings_university_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bookings
    ADD CONSTRAINT bookings_university_id_fkey FOREIGN KEY (university_id) REFERENCES public.universities(id);


--
-- Name: guardian_consents guardian_consents_guardian_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guardian_consents
    ADD CONSTRAINT guardian_consents_guardian_id_fkey FOREIGN KEY (guardian_id) REFERENCES public.guardians(id) ON DELETE CASCADE;


--
-- Name: guardian_consents guardian_consents_participant_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guardian_consents
    ADD CONSTRAINT guardian_consents_participant_user_id_fkey FOREIGN KEY (participant_user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: guardians guardians_participant_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guardians
    ADD CONSTRAINT guardians_participant_user_id_fkey FOREIGN KEY (participant_user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: guide_availability_dst_notices guide_availability_dst_notices_guide_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_availability_dst_notices
    ADD CONSTRAINT guide_availability_dst_notices_guide_id_fkey FOREIGN KEY (guide_id) REFERENCES public.guide_profiles(id) ON DELETE CASCADE;


--
-- Name: guide_availability_occurrences guide_availability_occurrences_guide_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_availability_occurrences
    ADD CONSTRAINT guide_availability_occurrences_guide_id_fkey FOREIGN KEY (guide_id) REFERENCES public.guide_profiles(id) ON DELETE CASCADE;


--
-- Name: guide_availability_occurrences guide_availability_occurrences_source_exception_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_availability_occurrences
    ADD CONSTRAINT guide_availability_occurrences_source_exception_id_fkey FOREIGN KEY (source_exception_id) REFERENCES public.availability_exceptions(id) ON DELETE SET NULL;


--
-- Name: guide_availability_occurrences guide_availability_occurrences_source_rule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_availability_occurrences
    ADD CONSTRAINT guide_availability_occurrences_source_rule_id_fkey FOREIGN KEY (source_rule_id) REFERENCES public.guide_availability_rules(id) ON DELETE SET NULL;


--
-- Name: guide_availability_rules guide_availability_rules_guide_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_availability_rules
    ADD CONSTRAINT guide_availability_rules_guide_id_fkey FOREIGN KEY (guide_id) REFERENCES public.guide_profiles(id) ON DELETE CASCADE;


--
-- Name: guide_booking_settings guide_booking_settings_guide_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_booking_settings
    ADD CONSTRAINT guide_booking_settings_guide_id_fkey FOREIGN KEY (guide_id) REFERENCES public.guide_profiles(id) ON DELETE CASCADE;


--
-- Name: guide_profiles guide_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.guide_profiles
    ADD CONSTRAINT guide_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: idempotency_keys idempotency_keys_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_keys
    ADD CONSTRAINT idempotency_keys_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(id);


--
-- Name: live_sessions live_sessions_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.live_sessions
    ADD CONSTRAINT live_sessions_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE CASCADE;


--
-- Name: notifications notifications_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE SET NULL;


--
-- Name: notifications notifications_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: participant_profiles participant_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.participant_profiles
    ADD CONSTRAINT participant_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: payment_methods payment_methods_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment_methods
    ADD CONSTRAINT payment_methods_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: payments payments_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE CASCADE;


--
-- Name: recordings recordings_live_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recordings
    ADD CONSTRAINT recordings_live_session_id_fkey FOREIGN KEY (live_session_id) REFERENCES public.live_sessions(id) ON DELETE CASCADE;


--
-- Name: refunds refunds_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE CASCADE;


--
-- Name: refunds refunds_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refunds
    ADD CONSTRAINT refunds_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payments(id) ON DELETE CASCADE;


--
-- Name: reschedule_proposals reschedule_proposals_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reschedule_proposals
    ADD CONSTRAINT reschedule_proposals_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE CASCADE;


--
-- Name: reschedule_proposals reschedule_proposals_requested_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reschedule_proposals
    ADD CONSTRAINT reschedule_proposals_requested_by_user_id_fkey FOREIGN KEY (requested_by_user_id) REFERENCES public.users(id);


--
-- Name: reviews reviews_booking_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_booking_id_fkey FOREIGN KEY (booking_id) REFERENCES public.bookings(id) ON DELETE CASCADE;


--
-- Name: reviews reviews_guide_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_guide_id_fkey FOREIGN KEY (guide_id) REFERENCES public.guide_profiles(id);


--
-- Name: reviews reviews_participant_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_participant_user_id_fkey FOREIGN KEY (participant_user_id) REFERENCES public.users(id);


--
-- Name: reviews reviews_tour_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reviews
    ADD CONSTRAINT reviews_tour_offering_id_fkey FOREIGN KEY (tour_offering_id) REFERENCES public.tour_offerings(id);


--
-- Name: session_messages session_messages_live_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_messages
    ADD CONSTRAINT session_messages_live_session_id_fkey FOREIGN KEY (live_session_id) REFERENCES public.live_sessions(id) ON DELETE CASCADE;


--
-- Name: session_messages session_messages_sender_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_messages
    ADD CONSTRAINT session_messages_sender_user_id_fkey FOREIGN KEY (sender_user_id) REFERENCES public.users(id);


--
-- Name: session_participants session_participants_live_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT session_participants_live_session_id_fkey FOREIGN KEY (live_session_id) REFERENCES public.live_sessions(id) ON DELETE CASCADE;


--
-- Name: session_participants session_participants_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.session_participants
    ADD CONSTRAINT session_participants_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: tour_offerings tour_offerings_guide_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tour_offerings
    ADD CONSTRAINT tour_offerings_guide_id_fkey FOREIGN KEY (guide_id) REFERENCES public.guide_profiles(id) ON DELETE CASCADE;


--
-- Name: tour_offerings tour_offerings_university_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tour_offerings
    ADD CONSTRAINT tour_offerings_university_id_fkey FOREIGN KEY (university_id) REFERENCES public.universities(id);


--
-- Name: user_roles user_roles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: guide_universities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.guide_universities (
  id                   uuid PRIMARY KEY,
  guide_profile_id     uuid NOT NULL REFERENCES public.guide_profiles(id) ON DELETE CASCADE,
  university_id        uuid NOT NULL REFERENCES public.universities(id),
  major                text,
  degree               text,
  class_year           text,
  entry_year           integer,
  school_email         public.citext,                          -- PII, never serialized
  verification_status  public.guide_verification_status NOT NULL DEFAULT 'NOT_SUBMITTED',
  verification_sent_at timestamptz,
  verified_at          timestamptz,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  UNIQUE (guide_profile_id, university_id)
);
CREATE INDEX ix_guide_universities_profile ON public.guide_universities (guide_profile_id);


--
-- PostgreSQL database dump complete
--


