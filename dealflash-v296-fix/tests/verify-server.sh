#!/usr/bin/env bash
# Run on a machine with network access. Answers: "does the production API actually return items, and in what shape?"
B=https://dealflash-server.pittytntn1.workers.dev
mkdir -p tests/fixtures
echo "== /api/status";  curl -s -o /dev/stderr -w "\nHTTP %{http_code}\n" "$B/api/status"
for p in "/api/v1/deals?limit=300" "/api/deals?limit=300" "/api/v1/deals?limit=300&signal=*" "/api/deals?limit=300&signal=*"; do
  echo "== $p"; curl -s -g -w "HTTP %{http_code}\n" -o /tmp/r.json "$B$p"; tail -c 0 /tmp/r.json
  python3 - <<'PY'
import json
try:
  d=json.load(open('/tmp/r.json')); it=d.get('items') if isinstance(d,dict) else d
  print('root keys:',list(d) if isinstance(d,dict) else 'array'); print('items.length =',len(it or []))
  for x in (it or [])[:3]: print(json.dumps(x,ensure_ascii=False)[:600])
except Exception as e: print('not JSON:',e)
PY
done
curl -s -g "$B/api/v1/deals?limit=300" -o tests/fixtures/prod_deals.json && echo "saved tests/fixtures/prod_deals.json -> run tests/run.sh to replay it through the Android pipeline"
cat <<'SQL'

-- D1 checks (wrangler d1 execute dealflash-db --remote --command "<sql>")
SELECT (SELECT COUNT(*) FROM products) products,(SELECT COUNT(*) FROM offers) offers,(SELECT COUNT(*) FROM product_signals) signals,(SELECT COUNT(*) FROM price_history) history;
SELECT COUNT(*) FROM products WHERE last_seen > (strftime('%s','now')-86400)*1000;   -- if 0, try without *1000 (seconds)
SELECT COUNT(*) FROM offers   WHERE checked_at > (strftime('%s','now')-86400)*1000;
SELECT COUNT(*) FROM product_signals WHERE seen_at > (strftime('%s','now')-86400)*1000;
SELECT MAX(last_seen), MIN(last_seen) FROM products;       -- ms (13 digits) or seconds (10 digits)?
SELECT product_key FROM push_events ORDER BY rowid DESC LIMIT 1;  -- then check that key in products/offers/product_signals
SQL
