# MatchBook

MatchBook is a limit order matching engine using price-time priority,
it is built as three components that communicate only using Kafka.

Orders arrive over HTTP, they are persisted through Postgres, then reach
an in-memory order book through Kafka, and then the resulting trades are
published back out.

---

## Running It

```bash
docker compose up -d
./gradlew bootRun
```

Submitting a resting sell:
```bash
curl -i -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 11111' \
  -d '{"clientId":"jim","symbol":"AAPL","side":"SELL","type":"LIMIT","price":"1.52","quantity":100}'
```
Then submit a crossing buy:
```bash
curl -i -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 222222' \
  -d '{"clientId":"john","symbol":"AAPL","side":"BUY","type":"LIMIT","price":"1.52","quantity":100}'
```

The application log will show which partition and thread handled each order, and the trade
appearing on `trades.executed`.

To run the tests:
```bash
./gradlew test
```

Test suite needs Docker but not a running `docker compose`, the Testcontainers start their
own Postgres and Kafka.

---

## How Matching Works
An order book holds the unmatched orders for one symbol. Bids sort highest first, asks sort lowest first, and the book is never crossed. If a buyer will pay 150 and a seller
accepts 100, they must have already traded.

Orders match on price-time priority, this means best price first, and within a price level, earliest arrival first.

Trades execute at the resting order's price, not the incoming orders. Example: given asks of 300 @ 110, 100 @ 110 and 500 @ 112, an incoming buy limit of 600 @ 112 produces three trades. They are at 110, 110 and 112. The buyer was willing to pay 112, and for 400 units they get them cheaper.

A limit order's unfilled remainder rests on the book. A market order's remainder is cancelled and never rests.

---

## Design Decisions

### 1. Single threaded matching, partitioned by symbol
Rather than lock the order book, each symbol's book is owned by exactly one thread.
This integrates well with Kafka, messages keyed by symbol land on the same partition, and a partition is consumed by one thread.
Nothing in `engine/` synchronises anything and it does not need to.

This is visible in logs, both orders for a symbol have the same partition and the same thread.

```
symbol=AAPL partition=9 offset=40 thread=org.springframework.kafka.KafkaListenerEndpointContainer#0-6-C-1
symbol=AAPL partition=9 offset=41 thread=org.springframework.kafka.KafkaListenerEndpointContainer#0-6-C-1
```
This approach is faster than locking, locking cost cycles even when uncontended, and cache lines don't transfer between cores.
The trade-off is that matching parallelism is capped at one thread per partition, and the count is frozen at the time of topic
creation. Partition assignment is hashed using partition size, so resizing moves existing symbols to new partitions and breaks
the ordering guarantee.

### 2. Transactional Outbox
Postgres and Kafka cannot be written atomically, a transaction only covers one of the systems. Therefore, if we just save
and then publish there is a crash window: crash between the save and publish and the order will be committed and acknowledged, but
it becomes invisible to the matching engine. Even if a client retries they will get returned their stored order and it won't be published.

A transactional outbox solves this - it turns the message into a database row. The order and its outgoing event are inserted in one
Postgres transaction, so neither can exist without the other one existing. A scheduled poller then delivers the pending rows
to Kafka in `id` order and marks them sent.

The poller always publishes before marking sent, during these two actions there is the possibility of a crash but the ordering
means that a failure only results in a duplicated message. Duplicates are handled downstream of this, the delivery mechanism
is therefore at-least-once, not-exactly-once.

### 3. Idempotency via unique constraint

Clients have to send an Idempotency key in the header. The service then checks for the key first, but this alone is not enough.
Two simultaneous requests both see `not found` and both insert. The unique constraint on `orders.idempotency_key` prevents
the duplicate; catches the resulting violation and re-reading.

Tested using 8 threads racing on one idempotency key: one row created and all 8 callers get the same order id.

### 4. No foreign keys between components

Tables which are owned by different components have no foreign key constraints between them. Foreign keys would couple components
that should be independently deployable. The cost is that there is no database-level referential integrity between components.
Tests enforce this instead.

---

## API

| Method  | Path | Response                                                |
|---------|------|---------------------------------------------------------|
| `POST` | `/orders` | `202` with order ID                                     |
| `DELETE` | `/orders/{id}` | `202` requested `200` if already resolved `404` unknown |
| `GET` | `/orders/{id}` | `200`/`404`                                             |

---
## Known Limitations

* **At-least-once, not-exactly-once** - the outbox can republish messages after a crash.
* **No self-trade prevention** - A client could match their own resting order.
* **`clientId` comes from request body, not authentication** - A client can claim to be another as there is no authentication.
* **Nothing writes back to the `orders` table** - `GET /orders/{id}` always reports `NEW`. The `TOO_LATE` cancel outcome is therefore currently unreachable. 
---

## Stack

Java 21 | Spring Boot 4.1 | Postgres 17 | Kafka 4 | Flyway | Testcontainers | ArchUnit | Gradle
