#!/usr/bin/env bash
# JVM tests (no Android SDK). Usage: tests/run.sh [APP_ROOT]   (APP_ROOT defaults to the repo containing this script)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; ROOT="${1:-$HERE/..}"
A="$ROOT/app/src/main/java/com/dealflash/app"; OUT="${TMPDIR:-/tmp}/dealflash-tests"; rm -rf "$OUT"; mkdir -p "$OUT"
JC="java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -nowarn"
CORE=(DataQuality ProductUrl ProductIdentity ServerIdentity ImageSampling ListingIntegrity DealScore HistoryStore HotDealStore ProductUX ProductLinks DealUrgency PageScanner DealPipeline MarketComparison DealItem ServerDealsParser SyncDiag DealsRepository DataMigration FeedFilter PriceVerdict PriceJudge EndedSync FcntGuard)
SRC=(); for f in "${CORE[@]}"; do SRC+=("$A/$f.java"); done
$JC -d "$OUT" "$HERE"/stubs/android/content/*.java "$HERE"/stubs/android/util/*.java "$HERE"/stubs/org/json/*.java "${SRC[@]}" "$HERE/java/ServerFeedIntegrationTests.java" "$HERE/java/IdentityMigrationTests.java" "$HERE/java/PipelineTests.java" "$HERE/java/EndedSyncTests.java" "$HERE/java/FcntGuardTests.java" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
java -cp "$OUT" com.dealflash.app.ServerFeedIntegrationTests "$HERE/fixtures" 2>&1 | grep -v JAVA_TOOL_OPTIONS
java -cp "$OUT" com.dealflash.app.IdentityMigrationTests "$HERE/fixtures" 2>&1 | grep -v JAVA_TOOL_OPTIONS
java -cp "$OUT" com.dealflash.app.PipelineTests 2>&1 | grep -v JAVA_TOOL_OPTIONS
java -cp "$OUT" com.dealflash.app.EndedSyncTests "$HERE/fixtures" 2>&1 | grep -v JAVA_TOOL_OPTIONS
java -cp "$OUT" com.dealflash.app.FcntGuardTests 2>&1 | grep -v JAVA_TOOL_OPTIONS
