-- CTL-50: persist optional propose reason for counterparty display (CTL-51).
ALTER TABLE public.reschedule_proposals
    ADD COLUMN reason text;
