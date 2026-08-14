# Progressive delivery with Argo Rollouts

check-service is deployed as an Argo Rollouts `Rollout` rather than a
`Deployment`, so a release is validated against live traffic before it reaches
everyone.

## The canary steps

| Step | Action        | Traffic | Duration |
| ---- | ------------- | ------- | -------- |
| 1    | `setWeight`   | 5%      | —        |
| 2    | `pause`       | 5%      | 5m       |
| 3    | `analysis`    | 5%      | ~5m      |
| 4    | `setWeight`   | 25%     | —        |
| 5    | `pause`       | 25%     | 10m      |
| 6    | `setWeight`   | 50%     | —        |
| 7    | `pause`       | 50%     | 10m      |
| —    | auto-promote  | 100%    | —        |

**A full promotion takes roughly 35 minutes.** That is why the Jenkins pipeline
timeout was raised from 30 to 90 minutes — the old budget would have killed a
healthy rollout partway through and left traffic split between two versions.

## Why each piece exists

Adding the `strategy` block alone does not produce a working canary. These are
the dependencies it pulls in:

| Piece | Why the canary fails without it |
| ----- | ------------------------------- |
| `check-service-stable` + `check-service-canary` Services | nginx needs two distinct backends to weight between |
| `check-service-ingress.yml` (dedicated Ingress) | Argo Rollouts clones the referenced Ingress to express weight. Pointed at the shared `pos-ingress` it would split traffic for all seven services |
| `AnalysisTemplate error-rate-and-p99` | Step 3 references it by name; a missing template fails the step |
| `percentiles-histogram` in `application.yml` | Micrometer publishes no `_bucket` series by default, so the p99 query returns empty and the rollout stalls as inconclusive |
| `probes.enabled: true` | The liveness/readiness group endpoints return 404 without it, and 404 is a failed probe |
| `prometheus-kubernetes.yml` | The analysis selects canary pods by `pod=~"check-service-<hash>-.*"`. `static_configs` produces no `pod` label, so the query would average canary and stable together and pass a broken release |
| HPA `scaleTargetRef` → `Rollout` | Pointed at a Deployment that no longer exists, the HPA silently does nothing |
| Jenkinsfile `rollout/` instead of `deployment/` | `kubectl set image deployment/check-service` no longer resolves |

**`setWeight: 5` requires real traffic routing.** A replica-count canary cannot
express 5% at two replicas — the finest split it can do is 50%. That is why the
nginx `trafficRouting` block is not optional here.

## Prerequisites

```bash
# Controller
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts -f \
  https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml

# kubectl plugin
kubectl krew install argo-rollouts
```

Also required in-cluster: an nginx ingress controller, Prometheus scraping with
the config in `infrastructure/prometheus/prometheus-kubernetes.yml`, and
kube-state-metrics (or drop the `canary-pods-available` metric).

## Applying

```bash
kubectl apply -f infrastructure/kubernetes/namespace.yml
kubectl apply -f infrastructure/kubernetes/configmap.yml
kubectl apply -f infrastructure/kubernetes/analysis-templates.yml
kubectl apply -f infrastructure/kubernetes/check-service-rollout.yml
kubectl apply -f infrastructure/kubernetes/check-service-ingress.yml
kubectl apply -f infrastructure/kubernetes/ingress.yml
```

> The previous `check-service-deployment.yml` was removed. Do not reintroduce
> it — both controllers select `app=check-service` and would fight over the
> same pods.

## Operating a rollout

```bash
kubectl argo rollouts get rollout check-service -n pos-system --watch
kubectl argo rollouts promote check-service -n pos-system   # skip current pause
kubectl argo rollouts abort   check-service -n pos-system   # 100% back to stable
kubectl argo rollouts undo    check-service -n pos-system   # previous revision
```

An aborted rollout stays aborted until the image is changed or it is explicitly
promoted — it does not silently retry.

## Known gaps

- **The analysis runs once, at 5%.** Steps 4–7 raise traffic to 25% and 50%
  with no further checking, so a regression that only appears under higher load
  is not caught. The stronger form is a background analysis at the `canary`
  level, which runs continuously for the whole rollout and can abort at any
  step:

  ```yaml
  strategy:
    canary:
      analysis:
        templates:
          - templateName: error-rate-and-p99
        startingStep: 2
        args: [...]
      steps: [...]
  ```

- **`payment-authorisation-rate` does not work yet.** It queries
  `pos_payment_transactions_total`, which needs a Micrometer `Counter`
  incremented in `PaymentService` and tagged with the transaction status. Until
  that metric exists the template will not gate anything. It is included
  because technical health is not sufficient for a money path: a release can
  hold error rate and latency steady while declining more cards, and a declined
  card is a perfectly successful HTTP 200.

- **Thresholds are guesses.** `error-rate <= 0.02` and `p99 <= 1.5s` are
  starting points. The right values are "meaningfully worse than stable", which
  requires a baseline from production traffic.

- **Only check-service is converted.** The other six services still use
  Deployments.
