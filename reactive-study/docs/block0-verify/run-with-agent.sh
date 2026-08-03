#!/usr/bin/env bash
# Build InitPathAgent and run reactive-study with runtime method tracing (Block 0).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AGENT_DIR="$(cd "$(dirname "$0")/agent" && pwd)"
BUILD="$AGENT_DIR/build"
LOG="$AGENT_DIR/block0-init-trace.log"
ASM_VERSION="9.7.1"
JDK="${JAVA_HOME:-/mnt/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot}"

mkdir -p "$BUILD/lib"
cd "$BUILD/lib"

if [[ ! -f "asm-${ASM_VERSION}.jar" ]]; then
  echo "Downloading ASM ${ASM_VERSION}..."
  curl -fsSL "https://repo1.maven.org/maven2/org/ow2/asm/asm/${ASM_VERSION}/asm-${ASM_VERSION}.jar" -o "asm-${ASM_VERSION}.jar"
fi

echo "Compiling agent..."
mkdir -p "$BUILD/classes"
"$JDK/bin/javac" -cp "$BUILD/lib/asm-${ASM_VERSION}.jar" \
  -d "$BUILD/classes" "$AGENT_DIR/InitPathAgent.java"

echo "Packaging agent jar..."
"$JDK/bin/jar" cfm "$BUILD/init-path-agent.jar" "$AGENT_DIR/META-INF/MANIFEST.MF" -C "$BUILD/classes" .

: >"$LOG"
cd "$ROOT"

echo "Running bootRun with javaagent..."
AGENT_JAR="$BUILD/init-path-agent.jar"
export JAVA_TOOL_OPTIONS="-javaagent:${AGENT_JAR//\//\\/}= -Dblock0.agent.log=${LOG//\//\\/}"

# Windows path fix for WSL gradlew
if [[ -f "$ROOT/gradlew.bat" ]]; then
  cmd.exe /c "cd /d $(wslpath -w "$ROOT") && set JAVA_TOOL_OPTIONS=-javaagent:$(wslpath -w "$AGENT_JAR") -Dblock0.agent.log=$(wslpath -w "$LOG") && gradlew.bat --no-daemon bootRun --args=--spring.profiles.active=local" > /tmp/reactive-study-agent-boot.log 2>&1 &
else
  ./gradlew --no-daemon bootRun --args='--spring.profiles.active=local' > /tmp/reactive-study-agent-boot.log 2>&1 &
fi

GRADLE_PID=$!
JAVA_PID=""

for i in $(seq 1 120); do
  JAVA_PID=$(pgrep -f 'ReactiveStudyApplication' | head -1 || true)
  if grep -q 'Started ReactiveStudyApplication' /tmp/reactive-study-agent-boot.log 2>/dev/null; then
    break
  fi
  sleep 0.5
done

sleep 1
curl -sf "http://localhost:8083/actuator/health" >/dev/null && echo "Health: UP" || echo "Health: FAIL"

# stop
if [[ -n "$JAVA_PID" ]]; then kill "$JAVA_PID" 2>/dev/null || true; fi
kill "$GRADLE_PID" 2>/dev/null || true

echo "--- Block 0 trace (unique ENTER lines) ---"
grep '^>>> ENTER' "$LOG" | sort -u || true

echo "--- Full trace file: $LOG ---"
echo "--- Boot log tail ---"
tail -20 /tmp/reactive-study-agent-boot.log
