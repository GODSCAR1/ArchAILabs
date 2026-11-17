SELECT id FROM customers
WHERE (last_name IS NULL) IS DISTINCT FROM
    (position(' ' IN regexp_replace(btrim(name, ' '), ' +', ' ', 'g')) = 0);
