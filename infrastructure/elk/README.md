# Centralised logging (ELK)

Structured JSON logging for all seven POS services, aggregated into
Elasticsearch and searchable in Kibana.

```
 service (stdout, JSON)  ->  Filebeat  ->  Logstash  ->  Elasticsearch  ->  Kibana
      Logback +                 per-node    parse,          index +           search &
   LogstashEncoder             collector    enrich, tag      ILM              dashboards
```

## Why the services write to stdout instead of talking to Logstash

`logstash-logback-encoder` can open a TCP appender straight to Logstash. That
was deliberately **not** used. It couples every request thread to the
availability of the log pipeline: if Logstash is slow or down, the appender
queues and eventually blocks or drops, and the failure surfaces inside the
application. Writing JSON to stdout keeps the app 12-factor — the collector is
an infrastructure concern that can be restarted, reconfigured or removed
without redeploying a service. If ELK is down, the POS keeps taking payments.

## Running it

```bash
docker compose up -d elasticsearch logstash kibana filebeat

# Register the index template and ILM policy. Run once.
./infrastructure/elk/elasticsearch/bootstrap-elasticsearch.sh

docker compose up -d          # start the services themselves
```

| Component     | URL                     |
| ------------- | ----------------------- |
| Kibana        | http://localhost:5601   |
| Elasticsearch | http://localhost:9200   |
| Logstash API  | http://localhost:9600   |

In Kibana: **Stack Management → Data Views → Create data view**, index pattern
`pos-logs-*`, time field `@timestamp`.

## What each log event contains

Every line is one JSON object. The fields that make it useful:

| Field                        | Type    | Notes                                             |
| ---------------------------- | ------- | ------------------------------------------------- |
| `@timestamp`                 | date    | UTC, set by the application, not by the collector  |
| `service`, `environment`     | keyword | From `spring.application.name` and `POS_ENVIRONMENT` |
| `level`, `logger`, `thread`  | keyword |                                                   |
| `message`                    | text    | Full-text searchable                               |
| `stack_trace`                | text    | Root cause first, bounded to 8 KB                  |
| `correlationId`              | keyword | **The one that matters** — see below               |
| `userId`, `username`         | keyword | From the JWT, once authenticated                   |
| `httpMethod`, `path`, `httpStatus` | mixed | Request context                              |
| `durationMs`                 | integer | Numeric, so it can be averaged and charted         |
| `operation`, `errorType`     | keyword | e.g. `CheckService.addItem`, `BAD_REQUEST`         |
| `kafkaTopic`                 | keyword | Set on consumer threads                            |

### Correlation IDs

`CorrelationIdFilter` assigns an id to every inbound request — reusing an
`X-Correlation-Id` header if the caller supplies a valid one, otherwise minting
a UUID — and echoes it back on the response. It survives two hops:

- **HTTP** — pass `X-Correlation-Id` between services.
- **Kafka** — `MdcProducerInterceptor` writes it as a record header;
  `KafkaMdcConfig` reads it back on the consumer side. This is what links a
  check opened on a terminal to the order that appears on the kitchen display.

One Kibana query returns the whole causal chain:

```
correlationId: "3f2a...e91"
```

Useful starting queries:

```
level: "ERROR" and service: "payment-gateway-service"
tags: "slow" and durationMs > 3000
tags: "security"                       # failed logins, access denied
errorType: "AUTH_FAILED" and username: "j.smith"
```

## Sensitive data

Two independent controls, because a PAN written into an index is not something
you can quietly delete — it means reindexing:

1. **`SensitiveDataMasker`** (application) — redacts card numbers, CVVs,
   passwords, tokens and keys before they reach an appender. Card numbers are
   matched both by field name and as bare digit runs validated with a Luhn
   check, so real PANs are caught while order totals and check numbers are left
   alone.
2. **A `gsub` filter in the Logstash pipeline** — a backstop for anything
   logged by a third-party library that never passes through the aspect.

Related hardening applied at the same time:

- `logging.level.com.pos` now defaults to **INFO**, not DEBUG. At DEBUG the
  logging aspect serialises every request and response body into the index.
- `org.springframework.security` in employee-service dropped from DEBUG to WARN.
- Client errors (404/400/401/403) log at **WARN**, not ERROR, so an "error rate"
  panel measures real failures rather than typos.

## Retention

`pos-logs-policy` (ILM): rollover at 10 GB or 1 day → force-merge at 3 days →
delete at 30 days. Tune in `bootstrap-elasticsearch.sh`.

Note for the payment path: PCI-DSS requires audit trails to be retained for a
year, with three months immediately available. The pipeline already tags those
events `pci-scope`; routing them to a separate index with its own longer policy
and tighter access control is the natural next step.

## Kubernetes

```bash
kubectl apply -f infrastructure/kubernetes/elk/filebeat-daemonset.yml
```

Filebeat runs as a DaemonSet — one collector per node, reading `/var/log/pods`,
enriching with pod metadata from the API server, and filtered to the
`pos-system` namespace.

Elasticsearch and Kibana are intentionally not included as manifests. Running a
stateful search cluster from hand-written StatefulSets is a standing
operational commitment (storage, upgrades, snapshots, capacity). Use a managed
service or the ECK operator and point `ELASTICSEARCH_HOSTS` at it.

## Local development

The `local` profile keeps the human-readable console format — nobody wants to
read JSON while debugging. The correlation id is included in the pattern:

```
14:22:07.431 INFO  [http-nio-8083-exec-1] 3f2a1c9e-... c.p.c.service.CheckService - Creating new check
```

JSON output activates on the `docker`, `kubernetes`, `staging` and `prod`
profiles.

## Security note

The Compose stack runs Elasticsearch with `xpack.security.enabled=false` — no
TLS, no authentication. That is acceptable for a laptop and **not** for anything
shared. Kibana is a full read interface over your logs; in a real environment
enable security, put Kibana behind SSO, and give Logstash a dedicated write-only
role.

## Pre-existing defects found while verifying this

Both are unrelated to logging, and were surfaced by starting the services to
confirm JSON output:

1. **employee-service failed to start** — `data.sql` executed before Hibernate
   created the schema (`Table "PERMISSIONS" not found`). Fixed here by adding
   `spring.jpa.defer-datasource-initialization: true`. The real fix is Flyway or
   Liquibase instead of `ddl-auto: update`.
2. **employee-service still fails** with `AuthService` requiring a bean of type
   `EmployeeMapper`. The MapStruct implementation *is* generated, annotated
   `@Component`, and present in the jar under a scanned package — so the cause
   is something else and it needs its own investigation. **Not fixed.**

Neither blocks the logging pipeline; the other six services are unaffected.
