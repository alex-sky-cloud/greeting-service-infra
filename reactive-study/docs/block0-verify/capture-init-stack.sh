#!/usr/bin/env bash
# Capture JVM thread stacks during reactive-study startup (Block 0 verification).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/mnt/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot}"
export PATH="$JAVA_HOME/bin:$PATH"

LOG="/tmp/reactive-study-boot.log"
STACK="/tmp/reactive-study-init-stacks.txt"
: >"$LOG"
: >"$STACK"

echo "Starting bootRun (profile local)..."
./gradlew --no-daemon bootRun --args='--spring.profiles.active=local' >>"$LOG" 2>&1 &
GRADLE_PID=$!

cleanup() {
  kill "$GRADLE_PID" 2>/dev/null || true
  pkill -P "$GRADLE_PID" 2>/dev/null || true
  # kill java process for reactive-study if still up
  pgrep -f 'reactive-study.*ReactiveStudyApplication|reactive-study.jar' | xargs -r kill 2>/dev/null || true
}
trap cleanup EXIT

JAVA_PID=""
for i in $(seq 1 120); do
  JAVA_PID=$(pgrep -f 'com.example.reactivestudy.ReactiveStudyApplication' | head -1 || true)
  if [[ -n "$JAVA_PID" ]]; then
    echo "Java PID: $JAVA_PID (after ${i}x0.5s)"
    break
  fi
  sleep 0.5
done

if [[ -z "$JAVA_PID" ]]; then
  echo "ERROR: Java process not found"
  tail -50 "$LOG"
  exit 1
fi

echo "Polling jstack for Block 0 classes..."
FOUND=0
for i in $(seq 1 80); do
  TS="$(date +%H:%M:%S.%3N)"
  DUMP="$(jstack "$JAVA_PID" 2>/dev/null || true)"
  if echo "$DUMP" | grep -qE 'NettyWebServer|ServerTransport|TransportConnector|DefaultLoopResources|NettyReactiveWebServerFactory'; then
    {
      echo "===== jstack @ $TS iteration $i ====="
      echo "$DUMP"
      echo
    } >>"$STACK"
    FOUND=1
  fi
  if grep -q 'Started ReactiveStudyApplication' "$LOG" 2>/dev/null; then
    echo "Application started (iteration $i)"
    break
  fi
  sleep 0.25
done

# final stack + health check
{
  echo "===== jstack FINAL @ $(date +%H:%M:%S) ====="
  jstack "$JAVA_PID" 2>/dev/null || true
} >>"$STACK"

echo "--- boot log tail ---"
tail -30 "$LOG"

echo "--- relevant stacks (grep) ---"
grep -nE 'NettyWebServer|NettyReactiveWebServerFactory|ServerTransport|TransportConnector|DefaultLoopResources|MultiThreadIoEventLoopGroup|AbstractNioChannel|ServerBootstrap|NioEventLoopGroup|doBeginRead|onServerSelect|bindNow|bind\(\)' "$STACK" || true

if [[ "$FOUND" -eq 0 ]]; then
  echo "WARN: no Block 0 frames in captured stacks"
  exit 2
fi

# health
sleep 1
curl -sf "http://localhost:8083/actuator/health" && echo || echo "health check failed"

echo "Full stacks: $STACK"
echo "Full log: $LOG"
