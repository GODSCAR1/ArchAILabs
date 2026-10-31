CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO categories (id, name, version)
VALUES ('00000000-0000-0000-0000-000000000001', 'Sin categoría', 0);

ALTER TABLE products
    ADD COLUMN category_id UUID,
    ADD COLUMN status VARCHAR(20),
    ADD COLUMN created_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE products
SET category_id = '00000000-0000-0000-0000-000000000001',
    status = 'ACTIVE',
    created_at = CURRENT_TIMESTAMP;

ALTER TABLE products
    ALTER COLUMN category_id SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ADD CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    ADD CONSTRAINT chk_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'DISCONTINUED'));

CREATE INDEX ix_products_category_id ON products (category_id);
CREATE INDEX ix_products_status ON products (status);
CREATE INDEX ix_products_price ON products (price);
CREATE INDEX ix_products_created_at ON products (created_at);
