ALTER TABLE orders
    ADD COLUMN payment_amount NUMERIC(19, 2),
    ADD COLUMN payment_attempt INTEGER NOT NULL DEFAULT 0;

CREATE TABLE order_confirmation_outbox (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id),
    product_ids UUID[] NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_order_confirmation_outbox_pending
    ON order_confirmation_outbox (created_at) WHERE processed_at IS NULL;
