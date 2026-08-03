# -*- coding: utf-8 -*-
"""Build InitPathAgent fat jar for Block 0 verification."""
from __future__ import annotations

import shutil
import subprocess
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
AGENT_DIR = ROOT / "agent"
BUILD = AGENT_DIR / "build"
LIB = BUILD / "lib"
CLASSES = BUILD / "classes"
STAGE = BUILD / "stage"
ASM = LIB / "asm-9.7.1.jar"
AGENT_JAR = BUILD / "init-path-agent.jar"
JDK = Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin")
JAVAC = JDK / "javac.exe"
JAR = JDK / "jar.exe"


def ensure_asm() -> None:
    LIB.mkdir(parents=True, exist_ok=True)
    if ASM.exists():
        return
    url = "https://repo1.maven.org/maven2/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar"
    print("Downloading", url)
    urllib.request.urlretrieve(url, ASM)


def compile_agent() -> None:
    if CLASSES.exists():
        shutil.rmtree(CLASSES)
    CLASSES.mkdir(parents=True)
    cmd = [
        str(JAVAC),
        "-classpath", str(ASM),
        "-source", "11",
        "-target", "11",
        "-d", str(CLASSES),
        str(AGENT_DIR / "InitPathAgent.java"),
    ]
    print("Running:", " ".join(cmd))
    subprocess.run(cmd, check=True)


def package_agent() -> None:
    if STAGE.exists():
        shutil.rmtree(STAGE)
    STAGE.mkdir(parents=True)
    with zipfile.ZipFile(ASM) as zf:
        zf.extractall(STAGE)
    shutil.copytree(CLASSES / "block0verify", STAGE / "block0verify", dirs_exist_ok=True)
    manifest = AGENT_DIR / "META-INF" / "MANIFEST.MF"
    subprocess.run(
        [str(JAR), "cfm", str(AGENT_JAR), str(manifest), "-C", str(STAGE), "."],
        check=True,
    )
    print("Agent:", AGENT_JAR)


def main() -> None:
    ensure_asm()
    compile_agent()
    package_agent()


if __name__ == "__main__":
    main()
