SELECT id FROM customers
WHERE concat_ws(' ', first_name, last_name)
    IS DISTINCT FROM regexp_replace(btrim(name, ' '), ' +', ' ', 'g');
