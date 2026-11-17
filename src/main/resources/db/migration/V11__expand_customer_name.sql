DO $$
DECLARE
    invalid_count BIGINT;
BEGIN
    SELECT count(*) INTO invalid_count FROM customers WHERE btrim(name, ' ') = '';
    IF invalid_count > 0 THEN
        RAISE EXCEPTION 'Customer names requiring human correction: %', invalid_count;
    END IF;
END $$;

ALTER TABLE customers ADD COLUMN first_name VARCHAR(120);
ALTER TABLE customers ADD COLUMN last_name VARCHAR(120);

UPDATE customers
SET first_name = split_part(regexp_replace(btrim(name, ' '), ' +', ' ', 'g'), ' ', 1),
    last_name = NULLIF(
        substring(regexp_replace(btrim(name, ' '), ' +', ' ', 'g') FROM '^[^ ]+ (.*)$'),
        ''
    );
