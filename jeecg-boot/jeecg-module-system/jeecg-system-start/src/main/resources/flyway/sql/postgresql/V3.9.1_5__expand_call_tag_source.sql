ALTER TABLE IF EXISTS call_tag
    ALTER COLUMN source TYPE varchar(32),
    ALTER COLUMN tag_name TYPE varchar(100);
