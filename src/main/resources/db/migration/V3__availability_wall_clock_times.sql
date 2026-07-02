-- Store availability wall-clock times as HH:mm:ss strings so Hibernate's
-- hibernate.jdbc.time_zone=UTC does not shift LocalTime on JDBC TIME bindings.

ALTER TABLE guide_availability_rules
  DROP CONSTRAINT guide_availability_rules_check;

ALTER TABLE guide_availability_rules
  ALTER COLUMN start_local TYPE VARCHAR(8) USING to_char(start_local, 'HH24:MI:SS'),
  ALTER COLUMN end_local TYPE VARCHAR(8) USING to_char(end_local, 'HH24:MI:SS');

ALTER TABLE guide_availability_rules
  ADD CONSTRAINT guide_availability_rules_check
    CHECK (start_local::time < end_local::time);

ALTER TABLE availability_exceptions
  ALTER COLUMN start_local TYPE VARCHAR(8) USING to_char(start_local, 'HH24:MI:SS'),
  ALTER COLUMN end_local TYPE VARCHAR(8) USING to_char(end_local, 'HH24:MI:SS');
