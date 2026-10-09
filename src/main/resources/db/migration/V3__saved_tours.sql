-- CTL-125: participant saved-tours (wishlist) join table.
-- One row per (user, tour offering); uniqueness enforces idempotent save.

CREATE TABLE public.saved_tours (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    tour_offering_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT saved_tours_pkey PRIMARY KEY (id),
    CONSTRAINT saved_tours_user_offering_key UNIQUE (user_id, tour_offering_id),
    CONSTRAINT saved_tours_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE CASCADE,
    CONSTRAINT saved_tours_tour_offering_id_fkey
        FOREIGN KEY (tour_offering_id) REFERENCES public.tour_offerings (id)
);

CREATE INDEX ix_saved_tours_user_created
    ON public.saved_tours (user_id, created_at DESC);
