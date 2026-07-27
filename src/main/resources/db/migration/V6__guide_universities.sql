CREATE TABLE public.guide_universities (
  id                   uuid PRIMARY KEY,
  guide_profile_id     uuid NOT NULL REFERENCES public.guide_profiles(id) ON DELETE CASCADE,
  university_id        uuid NOT NULL REFERENCES public.universities(id),
  major                text,
  degree               text,
  class_year           text,
  school_email         public.citext,                          -- PII, never serialized
  verification_status  public.guide_verification_status NOT NULL DEFAULT 'NOT_SUBMITTED',
  verification_sent_at timestamptz,
  verified_at          timestamptz,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  UNIQUE (guide_profile_id, university_id)
);
CREATE INDEX ix_guide_universities_profile ON public.guide_universities (guide_profile_id);
