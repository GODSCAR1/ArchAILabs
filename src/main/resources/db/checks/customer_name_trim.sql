SELECT id FROM customers
WHERE first_name IS DISTINCT FROM btrim(first_name, ' ')
   OR last_name IS DISTINCT FROM btrim(last_name, ' ');
