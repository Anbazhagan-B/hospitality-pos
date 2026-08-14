# hospitality-pos

A Point of Sale system for hospitality built as Spring Boot microservices —
check management, payments, kitchen display, employee auth and enterprise
configuration — with event-driven communication over Kafka, centralised
structured logging, and Kubernetes deployment manifests.

**Stack:** Java 17 · Spring Boot 3.2 · Spring Security (JWT) · Spring Data JPA ·
Spring Kafka · Spring Batch · PostgreSQL · Resilience4j · Prometheus ·
Elasticsearch / Logstash / Kibana · Docker · Kubernetes · Jenkins

---

## Services

| Service                         | Port | Responsibility                                              |
| ------------------------------- | ---- | ----------------------------------------------------------- |
| `employee-service`              | 8081 | Employees, roles, permissions, JWT issuance                  |
| `enterprise-management-service` | 8082 | Organisations, profit centres, menu items, tenders, terminals |
| `check-service`                 | 8083 | Check lifecycle — open, add items, void, close; publishes order events |
| `payment-gateway-service`       | 8084 | Credit, debit, gift card, loyalty and casino tenders; refunds and voids |
| `kitchen-display-service`       | 8085 | Consumes order events, drives kitchen screens over WebSocket |
| `admin-panel-service`           | 8086 | Form definitions for the admin UI                            |
| `cook-service`                  | 8087 | Spring Batch job producing terminal/cook data as JSON        |
| `common`                        | —    | Shared security, logging, exceptions and DTOs                |

## Architecture

```
                        ┌──────────────────┐
   Terminal UI ────────▶│  nginx Ingress   │◀──────── Admin Panel UI
   Admin UI             │  routing + rate  │
                        │     limiting     │
                        └────────┬─────────┘
                                 │
     ┌────────────┬──────────────┼───────────────┬──────────────┐
     ▼            ▼              ▼               ▼              ▼
  employee   enterprise-mgmt   check          payment        admin-panel
  (8081)       (8082)          (8083)         (8084)           (8086)
                                 │
                                 │ OrderEvent
                                 ▼
                          ┌─────────────┐
                          │    Kafka    │
                          │ order-events│
                          └──────┬──────┘
                                 ▼
                        kitchen-display (8085)
                                 │ STOMP / WebSocket
                                 ▼
                          Kitchen screens
```

**Cross-cutting concerns** are implemented once in `common` and applied to every
service: JWT authentication, an AOP logging aspect, a global exception handler,
and correlation-ID propagation across both HTTP and Kafka.

## Running locally

```bash
# Build all modules
./mvnw clean install -DskipTests

# Start infrastructure and services
docker compose up -d

# Register the log index template and retention policy (once)
./infrastructure/elk/elasticsearch/bootstrap-elasticsearch.sh
```

| Interface     | URL                                    |
| ------------- | -------------------------------------- |
| Kibana        | http://localhost:5601                  |
| Grafana       | http://localhost:3000                  |
| Prometheus    | http://localhost:9090                  |
| Swagger UI    | http://localhost:{port}/swagger-ui.html |

## Observability

- **Metrics** — Spring Boot Actuator exposes Micrometer metrics at
  `/actuator/prometheus`, scraped by Prometheus and visualised in Grafana.
- **Logs** — every service emits single-line JSON to stdout, collected by
  Filebeat, enriched by Logstash and indexed into Elasticsearch. A correlation
  ID is assigned per request and propagated across service boundaries *and*
  across the Kafka hop, so one Kibana query returns the full causal chain for a
  check — from the terminal that opened it to the kitchen screen that displayed
  it. See [`infrastructure/elk/README.md`](infrastructure/elk/README.md).
- **Resilience** — Resilience4j circuit breakers with fallbacks on the payment
  path.

## Kubernetes

```bash
kubectl apply -f infrastructure/kubernetes/namespace.yml
kubectl apply -f infrastructure/kubernetes/configmap.yml
kubectl apply -f infrastructure/kubernetes/
kubectl apply -f infrastructure/kubernetes/elk/filebeat-daemonset.yml
```

Manifests include rolling updates with zero unavailability, liveness/readiness/
startup probes, resource requests and limits, HPA, PodDisruptionBudget, pod
anti-affinity and zone topology spread.

## Project status

This is a working reference implementation of the architecture, not a
production system. Known gaps, kept visible rather than hidden:

- Credentials in `docker-compose.yml` and `infrastructure/kubernetes/configmap.yml`
  are **development placeholders**. Real deployments should use External Secrets
  Operator or a cloud secret store.
- `spring.jpa.hibernate.ddl-auto: update` stands in for proper migrations;
  Flyway or Liquibase is the correct tool.
- Deployment manifests exist for a subset of services.
- Test coverage is not yet in place.

## Licence

Released for portfolio and educational use.
