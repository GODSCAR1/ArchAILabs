CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL
);

CREATE TABLE order_lines (
    order_id UUID NOT NULL REFERENCES orders (id),
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT uq_order_lines_product UNIQUE (order_id, product_id),
    CONSTRAINT chk_order_lines_quantity_positive CHECK (quantity > 0)
);
