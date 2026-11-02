CREATE TABLE product_variants (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    sku_suffix VARCHAR(64) NOT NULL,
    color VARCHAR(80) NOT NULL,
    size VARCHAR(40) NOT NULL,
    additional_price BIGINT NOT NULL,
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX ix_product_variants_product_id ON product_variants (product_id);

CREATE TABLE product_attributes (
    product_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    value VARCHAR(255) NOT NULL,
    PRIMARY KEY (product_id, name),
    CONSTRAINT fk_product_attributes_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE reviews (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    author VARCHAR(120) NOT NULL,
    rating INTEGER NOT NULL,
    comment VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX ix_reviews_product_created_id
    ON reviews (product_id, created_at DESC, id);
