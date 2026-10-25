CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL
);

ALTER TABLE orders
    ADD COLUMN warehouse_id UUID REFERENCES warehouses (id);
