-- Store availability wall-clock times as HH:mm:ss strings so Hibernate's
-- hibernate.jdbc.time_zone=UTC does not shift LocalTime on JDBC TIME bindings.
-- Renamed from V3 (CTL-49 occupies V3 on main).

ALTER TABLE guide_availability_rules
  DROP CONSTRAINT IF EXISTS guide_availability_rules_check;

ALTER TABLE guide_availability_rules
  ALTER COLUMN start_local TYPE VARCHAR(8) USING to_char(start_local, 'HH24:MI:SS'),
  ALTER COLUMN end_local TYPE VARCHAR(8) USING to_char(end_local, 'HH24:MI:SS');

ALTER TABLE availability_exceptions
  ALTER COLUMN start_local TYPE VARCHAR(8)
    USING CASE
      WHEN start_local IS NULL THEN NULL
      ELSE to_char(start_local, 'HH24:MI:SS')
    END,
  ALTER COLUMN end_local TYPE VARCHAR(8)
    USING CASE
      WHEN end_local IS NULL THEN NULL
      ELSE to_char(end_local, 'HH24:MI:SS')
    END;

-- Explicit constraint names (V1 used an implicit CHECK name on rules only).
ALTER TABLE guide_availability_rules
  ADD CONSTRAINT guide_availability_rules_start_local_format_check
    CHECK (start_local ~ '^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$'),
  ADD CONSTRAINT guide_availability_rules_end_local_format_check
    CHECK (end_local ~ '^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$'),
  ADD CONSTRAINT guide_availability_rules_time_range_check
    CHECK (start_local::time < end_local::time);

ALTER TABLE availability_exceptions
  ADD CONSTRAINT availability_exceptions_start_local_format_check
    CHECK (
      start_local IS NULL
      OR start_local ~ '^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$'
    ),
  ADD CONSTRAINT availability_exceptions_end_local_format_check
    CHECK (
      end_local IS NULL
      OR end_local ~ '^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$'
    ),
  ADD CONSTRAINT availability_exceptions_time_range_check
    CHECK (
      (start_local IS NULL AND end_local IS NULL)
      OR (
        start_local IS NOT NULL
        AND end_local IS NOT NULL
        AND start_local::time < end_local::time
      )
    );
