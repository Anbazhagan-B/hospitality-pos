# check-service on DynamoDB

check-service can persist checks to either Postgres/JPA or DynamoDB, selected at
runtime:

```yaml
pos:
  check:
    store: jpa        # default, unchanged behaviour
    # store: dynamodb
```

Two implementations behind one `CheckStore` port, so the migration is a strangler
rather than a cutover — and rolling back is a config change, not a redeploy.

## Table design

One item per check, holding its lines, modifiers and payments.

```
pk = CHECK#<id>                              partition key
gsi1-org-status : ORG#<org>#STATUS#<status> / openedAt
gsi2-org-opened : ORG#<org>                / openedAt
```

| Access pattern | Operation |
| -------------- | --------- |
| by id | `GetItem` |
| by check number | `GetItem` — the number encodes the id |
| open checks for an org | `Query` gsi1 |
| all checks for an org | `Query` gsi2, newest first |

**No Scan anywhere.** That is the property that keeps latency and cost flat as
the table grows, and it is why the port is narrow: the old JPA repository
exposed finders by employee, by terminal, by global status and by arbitrary date
range that nothing called. In Postgres an unused finder is free; in DynamoDB each
one is an index with its own storage and per-write cost.

### Why one item instead of an item collection

The textbook single-table design would give each check a partition and store
`META`, `ITEM#n` and `PAYMENT#n` as separate rows. That is right when children
are updated independently at high frequency, or when the aggregate could exceed
the 400 KB item limit. Neither applies: a check with fifty lines is roughly
25 KB, and `recalculateTotals()` needs every line and payment anyway, so the
check is always read whole.

One item buys real properties — a read is one `GetItem` rather than a `Query`
plus assembly, and a write is one conditional `PutItem`, atomic by construction.
No `TransactWriteItems`, and no possibility of a half-written check whose total
disagrees with its lines. That matters when the document is someone's bill.

The cost is that every write rewrites the whole item, so write capacity scales
with check size rather than change size. At a few kilobytes that is one or two
WCUs — cheaper than the multiple writes plus transaction the split design needs.

## What this fixes

**Check number collisions.** `"CHK-" + System.currentTimeMillis()` produced
duplicates whenever two of the 2–10 replicas opened a check in the same
millisecond — and since the check number is also the Kafka partition key, a
collision merged two customers' orders onto one kitchen ticket. Replaced with a
server-side atomic counter (`UpdateItem` with `ADD`), which DynamoDB applies
under a per-item lock so every caller gets a distinct, monotonic value.

Trade-off: one counter item means one partition, capped near 1,000 writes/sec.
Far beyond restaurant volume; a chain that outgrew it would shard the counter.

**Lost order events.** The table has Streams enabled with
`NEW_AND_OLD_IMAGES`. That is the clean fix for the outbox problem — the current
code commits the check and *then* publishes to Kafka, so a broker failure loses
the event while the check is already saved. With Streams the change log **is**
the commit, so the event cannot be lost; a consumer forwards it at least once.
Not yet wired up, but the table is ready.

**Concurrent edits.** `@DynamoDbVersionAttribute` makes every write conditional
on the version read. Two servers adding lines to the same table's check would
otherwise last-write-wins, and one line would silently vanish from the bill.

## Local development

```bash
docker compose up -d dynamodb-local

aws dynamodb create-table --endpoint-url http://localhost:8000 \
  --table-name pos-checks \
  --attribute-definitions \
      AttributeName=pk,AttributeType=S \
      AttributeName=gsi1pk,AttributeType=S AttributeName=gsi1sk,AttributeType=S \
      AttributeName=gsi2pk,AttributeType=S AttributeName=gsi2sk,AttributeType=S \
  --key-schema AttributeName=pk,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --global-secondary-indexes '[
    {"IndexName":"gsi1-org-status","KeySchema":[{"AttributeName":"gsi1pk","KeyType":"HASH"},{"AttributeName":"gsi1sk","KeyType":"RANGE"}],"Projection":{"ProjectionType":"ALL"}},
    {"IndexName":"gsi2-org-opened","KeySchema":[{"AttributeName":"gsi2pk","KeyType":"HASH"},{"AttributeName":"gsi2sk","KeyType":"RANGE"}],"Projection":{"ProjectionType":"ALL"}}]'

POS_CHECK_STORE=dynamodb \
POS_DYNAMODB_ENDPOINT=http://localhost:8000 \
  java -jar target/check-service-1.0.0-SNAPSHOT.jar
```

In AWS, leave `POS_DYNAMODB_ENDPOINT` empty — the SDK resolves the regional
endpoint and credentials come from IRSA, so no static key exists in the pod. The
table and its scoped IAM role are in
`infrastructure/terraform/dynamodb.tf`.

## Known gaps

- **`@Transactional` no longer means anything on this path.** The annotations
  remain on `CheckService` and still open a JPA transaction, but that transaction
  does not cover DynamoDB writes. Atomicity comes from the single-item write
  instead. The annotations should be removed once JPA is dropped — leaving them
  is misleading.
- **Pagination is offset-emulated.** `Pageable` is offset-based and DynamoDB
  paginates by cursor, so `findByOrganizationId` reads up to the end of the
  requested page. Fine for the first few pages of an admin screen, wasteful
  beyond that. Deep paging should switch to passing `lastEvaluatedKey` back.
- **`totalElements` is approximate.** DynamoDB has no cheap `COUNT(*)` over a
  partition; a true total means reading every matching item.
- **Streams are enabled but nothing consumes them.** The outbox fix above is
  available, not built.
- **No migration of existing rows.** Switching an environment to `dynamodb`
  starts with an empty table. Backfilling means a one-off job reading Postgres
  and writing items.
