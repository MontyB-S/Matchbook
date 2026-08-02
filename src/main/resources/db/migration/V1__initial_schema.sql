-- Orders as submitted by clients.
-- order book is derived from this via Kafka.
CREATE TABLE orders (
                        id              UUID PRIMARY KEY,
                        idempotency_key VARCHAR(64) NOT NULL UNIQUE,
                        client_id       VARCHAR(64) NOT NULL,
                        symbol          VARCHAR(16) NOT NULL,
                        side            VARCHAR(4)  NOT NULL CHECK ( side IN ('BUY', 'SELL') ),
                        order_type      VARCHAR(8)  NOT NULL,
                        price_ticks     BIGINT,
                        quantity        BIGINT NOT NULL CHECK (quantity > 0),
                        filled_quantity BIGINT NOT NULL DEFAULT 0 CHECK ( filled_quantity >= 0 AND filled_quantity <= quantity ),
                        status          VARCHAR(20) NOT NULL CHECK ( status IN ('NEW', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED') ),
                        version         BIGINT NOT NULL DEFAULT 0,
                        created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                        CONSTRAINT orders_price_matches_type CHECK (
                            (order_type = 'LIMIT' AND price_ticks IS NOT NULL AND price_ticks > 0)
                                OR (order_type = 'MARKET' AND price_ticks IS NULL))
);

CREATE INDEX idx_orders_client_symbol ON orders (client_id, symbol);


-- Kafka messages waiting to be published. Written in the same
-- transaction as the order
CREATE TABLE outbox (
                        id           BIGSERIAL PRIMARY KEY,
                        aggregate_id UUID        NOT NULL,
                        topic        VARCHAR(64) NOT NULL,
                        msg_key      VARCHAR(64) NOT NULL,
                        payload      JSONB       NOT NULL,
                        created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                        published_at TIMESTAMPTZ
);

-- Partial index: only unpublished rows are indexed. The publisher
-- only queries for those
CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;


-- Executions produced by the matching engine.
CREATE TABLE trades (
                        id              UUID PRIMARY KEY,
                        symbol          VARCHAR(16) NOT NULL,
                        sequence_number BIGINT NOT NULL,
                        buy_order_id    UUID NOT NULL,
                        sell_order_id   UUID NOT NULL,
                        buy_client_id   VARCHAR(64) NOT NULL,
                        sell_client_id  VARCHAR(64) NOT NULL,
                        price_ticks     BIGINT NOT NULL CHECK ( price_ticks > 0 ),
                        quantity        BIGINT NOT NULL CHECK (quantity > 0),
                        executed_at     TIMESTAMPTZ NOT NULL,
                        UNIQUE (symbol, sequence_number)
);

CREATE INDEX idx_trades_symbol_time ON trades (symbol, executed_at);


-- Who owns what. Built by consuming the trades topic.
CREATE TABLE positions (
                           client_id  VARCHAR(64) NOT NULL,
                           symbol     VARCHAR(16) NOT NULL,
                           quantity   BIGINT NOT NULL DEFAULT 0,
                           cash_delta BIGINT NOT NULL DEFAULT 0,
                           version    BIGINT NOT NULL DEFAULT 0,
                           PRIMARY KEY (client_id, symbol)
);


-- Trade events already applied to positions. Written in the same
-- transaction as the position update
CREATE TABLE processed_events (
                                  event_id     UUID PRIMARY KEY,
                                  processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_time ON processed_events (processed_at);
