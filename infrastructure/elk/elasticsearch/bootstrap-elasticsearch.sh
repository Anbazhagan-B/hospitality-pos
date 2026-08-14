#!/usr/bin/env bash
#
# Registers the index template and ILM policy for POS logs.
#
# Run once after the stack is first started:
#   ./infrastructure/elk/elasticsearch/bootstrap-elasticsearch.sh
#
# Without a template, Elasticsearch guesses field types from whichever document
# happens to arrive first. That is how durationMs ends up mapped as text and
# stops being chartable, and how a stray field explodes the mapping.

set -euo pipefail

ES="${ELASTICSEARCH_URL:-http://localhost:9200}"

echo "Waiting for Elasticsearch at ${ES} ..."
until curl -fsS "${ES}/_cluster/health?wait_for_status=yellow&timeout=60s" >/dev/null 2>&1; do
  sleep 3
done
echo "Elasticsearch is up."

# ---------------------------------------------------------------------------
# ILM policy
#
# Retention is a cost and a compliance decision, not a technical one. 30 days of
# searchable application logs is a reasonable default; PCI-DSS requires at least
# 12 months for audit trails, of which 3 must be immediately available - which
# is why security-tagged events belong in their own index with a longer policy.
# ---------------------------------------------------------------------------
echo "Registering ILM policy 'pos-logs-policy' ..."
curl -fsS -X PUT "${ES}/_ilm/policy/pos-logs-policy" \
  -H 'Content-Type: application/json' -d '{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": { "max_primary_shard_size": "10gb", "max_age": "1d" },
          "set_priority": { "priority": 100 }
        }
      },
      "warm": {
        "min_age": "3d",
        "actions": {
          "forcemerge": { "max_num_segments": 1 },
          "set_priority": { "priority": 50 }
        }
      },
      "delete": {
        "min_age": "30d",
        "actions": { "delete": {} }
      }
    }
  }
}' >/dev/null
echo "  done."

# ---------------------------------------------------------------------------
# Index template
#
# Field type choices that matter:
#   correlationId / service / level -> keyword, because they are filtered and
#     aggregated on, never full-text searched. Mapping them as text would make
#     terms aggregations fail and multiply index size.
#   message / stack_trace -> text, because they are searched by substring.
#   durationMs -> integer so it can be averaged, percentiled and charted.
# ---------------------------------------------------------------------------
echo "Registering index template 'pos-logs' ..."
curl -fsS -X PUT "${ES}/_index_template/pos-logs" \
  -H 'Content-Type: application/json' -d '{
  "index_patterns": ["pos-logs-*"],
  "priority": 200,
  "template": {
    "settings": {
      "number_of_shards": 1,
      "number_of_replicas": 0,
      "index.lifecycle.name": "pos-logs-policy",
      "index.refresh_interval": "5s",
      "index.mapping.total_fields.limit": 200
    },
    "mappings": {
      "dynamic": "strict",
      "properties": {
        "@timestamp":     { "type": "date" },
        "service":        { "type": "keyword" },
        "environment":    { "type": "keyword" },
        "level":          { "type": "keyword" },
        "logger":         { "type": "keyword" },
        "thread":         { "type": "keyword" },
        "message":        { "type": "text" },
        "stack_trace":    { "type": "text" },

        "correlationId":  { "type": "keyword" },
        "userId":         { "type": "keyword" },
        "username":       { "type": "keyword" },
        "organizationId": { "type": "keyword" },
        "terminalId":     { "type": "keyword" },

        "httpMethod":     { "type": "keyword" },
        "path":           { "type": "keyword" },
        "httpStatus":     { "type": "integer" },
        "durationMs":     { "type": "integer" },
        "kafkaTopic":     { "type": "keyword" },

        "operation":      { "type": "keyword" },
        "errorType":      { "type": "keyword" },
        "exceptionType":  { "type": "keyword" },
        "responseStatus": { "type": "integer" },
        "invalidFields":  { "type": "keyword" },
        "args":           { "type": "text", "index": false },
        "result":         { "type": "text", "index": false },

        "tags":           { "type": "keyword" },
        "container":      {
          "properties": {
            "id":    { "type": "keyword" },
            "name":  { "type": "keyword" },
            "image": { "properties": { "name": { "type": "keyword" } } }
          }
        }
      }
    }
  }
}' >/dev/null
echo "  done."

# "dynamic": "strict" above means an unmapped field is rejected rather than
# silently indexed. That is intentional: it turns a mapping mistake into a
# visible error at write time instead of a field that quietly cannot be queried.

echo
echo "Bootstrap complete. Create the Kibana data view with:"
echo "  Index pattern : pos-logs-*"
echo "  Time field    : @timestamp"
