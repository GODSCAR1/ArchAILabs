CREATE TABLE stock (
    product_id UUID PRIMARY KEY,
    available INTEGER NOT NULL,
    CONSTRAINT chk_stock_available_non_negative CHECK (available >= 0)
);
