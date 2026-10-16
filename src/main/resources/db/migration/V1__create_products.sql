CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    price BIGINT NOT NULL,
    CONSTRAINT uq_products_sku UNIQUE (sku)
);
