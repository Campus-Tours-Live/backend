-- CTL-49: booking display timezone removed -- the absolute UTC instant is the only time on the
-- contract; clients render in the viewer's local timezone.
ALTER TABLE bookings DROP COLUMN display_timezone;
