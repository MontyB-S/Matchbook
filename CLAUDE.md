# Matchbook

A limit order matching engine. Portfolio project for 2027 graduate software engineering applications, targeting Java/Spring roles at investment banks and quant firms.

**The point of this project is that I can explain it in an interview.** See "Working with me" below — it changes how you should help.

---

## Working with me

I'm a CS student on a placement year as a Java/Spring Boot engineer. I know Spring Boot, JPA and Gradle reasonably well from work. Kafka, matching engines and low-latency work are new to me.

### Do not write implementation code unless I ask for it

The value of this project is being able to answer questions like "why is your matching engine single-threaded" and "what happens if the process dies between the database write and the Kafka publish". If you write the code, I can't answer those.

**Default behaviour:**
- Give me acceptance criteria and a skeleton with TODOs, not a finished implementation
- Explain the concept and let me write it
- If I'm stuck, give a hint first, then a bigger hint, then the answer if I ask again

**Exceptions — just write these, they're boilerplate:**
- Config files, build files, Docker, SQL migrations
- Test fixtures and builders
- Getters, mapping code between DTOs/entities/records
- Anything I explicitly say "just write this"

**Always fine:** reviewing code I've written, explaining errors, suggesting tests, pointing out bugs.

### Explain at a beginner level for anything new

Assume I don't know Kafka internals, exchange terminology, or systems programming concepts. Short sentences, concrete examples. Don't assume the jargon.

I do not need Spring Boot, JPA or Gradle basics explained.

### Be direct about mistakes

If my approach is wrong, say so and say why. Don't hedge.

---

## Current status

**Done:**
- Gradle build, Spring Boot 4.1, Java 21
- `docker-compose.yml` — Postgres + Kafka only
- `application.yml`
- Spotless with palantir-java-format
- Git repo, branch + squash-merge PR workflow

**Not done yet:**
- `V1__initial_schema.sql` — designed but not written to disk
- Domain model — `Side`, `OrderType`, `OrderStatus`, `Order`, `Trade`, `MatchResult`
- Everything else

**Next:** domain model records, then `OrderBook`.

> Keep this section current. It's the first thing to check before suggesting what to do next.

---

## Stack

| | Version | Notes |
|---|---|---|
| Java | **21** | Not 25. Toolchain pinned in `build.gradle.kts`. |
| Spring Boot | 4.1.0 | Major changes from 3.x — see gotchas |
| Gradle | Kotlin DSL | Single module |
| Postgres | 17 | Docker |
| Kafka | 4.x KRaft | Docker, no Zookeeper |
| Flyway | via `spring-boot-starter-flyway` | |
| Testcontainers | 2.x | Artifact names changed |
| Spotless | 8.7.0 | palantir-java-format |

Base package: `com.monty.matchbook`

### Spring Boot 4 gotchas

Most tutorials and training data are Boot 3. These will be wrong:

- `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**
- Flyway needs **`spring-boot-starter-flyway`**; `flyway-core` alone silently does nothing
- Testcontainers artifacts are **`testcontainers-postgresql`**, **`testcontainers-kafka`** (with the prefix)
- Jackson 3 is the default — packages are `tools.jackson.*`, not `com.fasterxml.jackson.*`
- JUnit 6, not 5
- Tech starters have paired `spring-boot-starter-<tech>-test` variants

**jqwik does not work here.** It runs on JUnit Platform 1.x; Boot 4 ships JUnit 6. Property tests are hand-rolled — see Testing.

---

## Domain

### Order book basics

An order book holds unmatched orders for one symbol. Two sides:

- **Bids** (buyers), sorted highest price first — highest bid is best
- **Asks** (sellers), sorted lowest price first — lowest ask is best

**Invariant: the book is never crossed.** `bestBid < bestAsk` always. If a buyer will pay 152 and a seller accepts 152, they should have traded.

### Price-time priority

1. Best price first
2. Within a price level, earliest arrival first (FIFO)

### Price improvement — easy to get wrong

**Trades execute at the RESTING order's price, not the incoming order's.**

Book: asks at 152 (300 units), 152 (100 units), 153 (500 units).
Incoming: limit buy 600 @ 153.

Result: three trades at **152, 152, 153**. The buyer was willing to pay 153 but got two fills at 152.

This is the canonical test case. If a change breaks it, the change is wrong.

### Market vs limit remainder

- **Limit** order remainder → rests on the book
- **Market** order remainder → **cancelled**, never rests

Same status and quantities, completely different meaning. Only the book knows which happened.

### Order lifecycle

```
NEW → PARTIALLY_FILLED → FILLED
 └────────┴────────────→ CANCELLED
NEW → REJECTED
```

---

## Architecture

Three components in one module. **They communicate only via Kafka**, never direct method calls.

```
HTTP → gateway ──[orders + outbox, one TX]──→ Postgres
                                                  │
                              outbox poller ──────┘
                                    ↓
                    ═══ Kafka: orders.accepted (key = symbol) ═══
                                    ↓
                              matching (in-memory book)
                                    ↓
                    ═══ Kafka: trades.executed ═══
                                    ↓
                    position ──[dedup + update, one TX]──→ Postgres
```

Packages by component, not by layer. `gateway/` must not import `position/`.

---

## Design decisions

Each of these is an interview question. Don't change them without discussion.

### 1. Single-threaded matching, partitioned by symbol

The order book is mutable shared state. Rather than lock it, give each symbol's book to exactly one thread.

Kafka provides this free: messages keyed by symbol go to the same partition, and a partition is consumed by one thread. 12 partitions = up to 12 matching threads.

Faster than locking because locks cost even when uncontended, and cache lines don't bounce between cores.

### 2. Transactional outbox

Saving to Postgres and publishing to Kafka can't share a transaction. Crash between them and you lose the order (or match one you have no record of).

Fix: write the event to an `outbox` table in the same transaction as the order. A scheduled poller publishes and marks rows sent.

Gives **at-least-once**, not exactly-once. Poller must stay single-threaded to preserve ordering. Debezium CDC is the production answer.

### 3. Idempotency in two places

- **HTTP:** client-supplied `Idempotency-Key` header, unique constraint. Duplicate → return the original order, don't create a second.
- **Kafka:** `processed_events` table, event ID inserted in the same transaction as the position update. Duplicate key → skip.

This is **effectively-once**, not exactly-once. Use the correct phrase.

### 4. Optimistic locking on positions

`@Version` column + `@Retryable`. Conflicts are rare (most trades are different clients), so optimistic beats `SELECT FOR UPDATE`, which costs a round trip every time.

### 5. Prices as `long` ticks

Never `double` (inexact), never `BigDecimal` (allocates, slow in the hot path). £1.52 with 0.01 tick = `152`. Convert to decimal only at the API boundary.

### 6. No foreign keys

Tables owned by different components have no FK constraints between them. FKs would couple components that must be independently deployable, and can't span databases.

Cost: no database-level referential integrity. Enforced by tests instead.

---

## File layout

```
src/main/java/com/monty/matchbook/
├── MatchbookApplication.java
├── engine/                    PURE JAVA — no Spring, no JPA, no Kafka
│   ├── model/                 Order, Trade, MatchResult, Side, OrderType, OrderStatus
│   ├── book/                  OrderBook (public), PriceLevel + RestingOrder (package-private)
│   └── MatchingEngine.java    routes symbol → OrderBook
├── gateway/                   HTTP in, Postgres + outbox out
│   ├── api/                   controllers, dto/, exception handler
│   ├── domain/                JPA entities
│   ├── repo/                  Spring Data repositories
│   ├── OrderService.java      the transactional boundary
│   └── OutboxPublisher.java   scheduled poller
├── matching/                  Kafka → engine → Kafka (thin adapter, no logic)
├── position/                  Kafka → dedup → positions
├── event/                     Kafka message records
└── config/                    topic definitions

src/main/resources/
├── application.yml
└── db/migration/V1__initial_schema.sql

src/test/java/com/monty/matchbook/
├── engine/book/               unit + property tests
├── support/Orders.java        test fixtures
├── AbstractIntegrationTest.java
└── ArchitectureTest.java      ArchUnit rules

src/jmh/java/com/monty/matchbook/bench/
```

### `engine/` must stay framework-free

No Spring annotations, no JPA, no Kafka imports. Enforced by ArchUnit. This keeps it fast to test, clean to benchmark, and portable to C++ later.

### Three "order" types is deliberate

- `SubmitOrderRequest` (DTO) — public API contract
- `OrderEntity` (JPA) — database shape, has `@Version`
- `Order` (record) — immutable value for the hot path

They change for different reasons. Don't collapse them.

`Order` has **no status field**. It's an immutable command. State lives in `RestingOrder` (mutable, in the book), `OrderEntity` (mutable, in the DB), and `MatchResult` (immutable snapshot).

---

## Database

Five tables, grouped by owner:

- **gateway:** `orders`, `outbox`
- **matching:** `trades`
- **position:** `positions`, `processed_events`

Notable:
- `outbox` has a **partial index** — `WHERE published_at IS NULL`. Keeps it tiny.
- `outbox.payload` is `JSONB`
- `trades` has `UNIQUE (symbol, sequence_number)` — catches sequencing bugs
- `positions` PK is composite `(client_id, symbol)`
- All timestamps are `TIMESTAMPTZ`, never `TIMESTAMP`

**Ledger invariant:** `SUM(quantity)` and `SUM(cash_delta)` across all positions must both be **zero**. Every buy has a matching sell. This is the day-10 concurrency test.

### Flyway rules

- `ddl-auto: validate` — Flyway owns the schema, never Hibernate
- Never edit an applied migration; checksum mismatch fails startup
- In dev, to change the schema: `docker compose down -v && docker compose up -d`
- Once public, add `V2__...` instead

---

## Testing

**Unit tests on `engine/`** — no Spring, milliseconds, where most tests live.

**Property tests, hand-rolled** (jqwik doesn't work). Generate random order sequences, check invariants after every order. **Print the seed on failure** so a failing sequence can be replayed.

Three invariants:
1. Book is never crossed
2. Quantity conserved — `traded × 2 + resting == submitted`
3. No order filled beyond its quantity

**Integration tests** — Testcontainers with real Postgres and Kafka. Use `@ServiceConnection`, not `@DynamicPropertySource`. Static containers shared across classes. Never H2 — no partial indexes, no JSONB, different concurrency semantics.

**Concurrency test** — 16 threads, 8000 orders, assert the ledger invariant.

**ArchUnit** — `engine/` has no Spring; `gateway/` doesn't import `position/`.

---

## Conventions

### Git

- Branch per chunk of work: `feat/order-book-matching`, `fix/...`, `perf/...`
- Conventional Commits: `feat:`, `fix:`, `test:`, `perf:`, `docs:`, `chore:`
- **Squash-merge** PRs. Exception: keep benchmark before/after as separate commits.
- PR description = problem / approach / tradeoff accepted. These become interview answers.
- CI must stay green.

### Code

- `./gradlew spotlessApply` before committing, or CI fails
- Records for immutable values; validate in the compact constructor so invalid objects can't exist
- Defensive copy collections in records — `List.copyOf()`
- Enums over strings and booleans for domain concepts
- Package-private for internals (`PriceLevel`, `RestingOrder`)

### Notes

`NOTES.md` holds design decisions as they're made. The README gets written from it on day 14.

---

## Commands

```bash
docker compose up -d              # Postgres + Kafka
docker compose down -v            # wipe (needed after migration changes)
./gradlew bootRun
./gradlew test
./gradlew spotlessApply
./gradlew jmh                     # day 12+

docker exec -it matchbook-postgres psql -U matchbook -d matchbook
docker exec -it matchbook-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

---

## Build order

14 days, ~5 hours each. Currently around day 1.

| Day | Goal |
|---|---|
| 1 | Setup, schema, domain model, first failing test |
| 2 | `OrderBook` add/cancel/bestBid/bestAsk — no matching |
| 3 | Matching algorithm |
| 4 | Edge cases + property tests |
| 5 | REST API |
| 6 | JPA + idempotency + first Testcontainers test |
| 7 | Kafka wiring |
| 8 | Transactional outbox + crash recovery test |
| 9 | Position service |
| 10 | Concurrency test |
| 11 | Metrics, Prometheus, Grafana |
| 12 | JMH baseline |
| 13 | One optimisation pass, measure |
| 14 | README, diagram, polish |

**Cut in this order if behind:** optimisation → Grafana → position service → market orders.

**Never cut:** outbox, idempotency, property tests, README.

**Do not build:** auth, frontend, Kubernetes, FIX protocol, multiple asset classes, distributed consensus.

---

## Edge cases needing named tests

| Case | Expected |
|---|---|
| Client matches own resting order | Policy decision — pick one, document it |
| Market order, empty book | Reject, don't hang |
| Market order partly filled | Remainder cancelled, not rested |
| Cancel after fill | "Too late", not an error |
| Zero/negative quantity | Reject at API with 400 |
| Cancel twice | Idempotent, return success |

---

## Known corrections

Things that were wrong earlier and are easy to reintroduce:

- **Kafka env vars are all-or-nothing.** Set none and the `apache/kafka` image uses bundled defaults. Set any and you must specify the full KRaft config. Don't "trim redundant" settings.
- **`max.in.flight.requests.per.connection: 1` is unnecessary.** Idempotence already guarantees ordering up to 5 in-flight.
- **Java 21, not 25.**
- **Prometheus/Grafana removed** from compose until day 11.
