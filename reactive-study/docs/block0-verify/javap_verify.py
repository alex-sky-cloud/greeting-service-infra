# -*- coding: utf-8 -*-
"""Verify Block 0 breakpoint classes/methods via javap on Gradle JARs."""
from __future__ import annotations

import glob
import subprocess
from pathlib import Path

JDK = Path(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\javap.exe")
CACHE = Path(r"D:\.gradle\caches\modules-2\files-2.1")
OUT = Path(__file__).resolve().parent / "javap-verified.txt"

CHECKS = [
    ("org.springframework.boot", "spring-boot-reactor-netty", "4.0.5",
     "org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory", "getWebServer"),
    ("org.springframework.boot", "spring-boot-reactor-netty", "4.0.5",
     "org.springframework.boot.reactor.netty.NettyWebServer", "start"),
    ("org.springframework.boot", "spring-boot-reactor-netty", "4.0.5",
     "org.springframework.boot.reactor.netty.NettyWebServer", "startHttpServer"),
    ("io.projectreactor.netty", "reactor-netty-http", "1.3.4",
     "reactor.netty.http.server.HttpServer", "bindNow"),
    ("io.projectreactor.netty", "reactor-netty-http", "1.3.4",
     "reactor.netty.transport.ServerTransport", "bind"),
    ("io.projectreactor.netty", "reactor-netty-core", "1.3.4",
     "reactor.netty.transport.TransportConnector", "bind"),
    ("io.projectreactor.netty", "reactor-netty-core", "1.3.4",
     "reactor.netty.transport.TransportConnector", "doInitAndRegister"),
    ("io.projectreactor.netty", "reactor-netty-core", "1.3.4",
     "reactor.netty.resources.DefaultLoopResources", "onServerSelect"),
    ("io.projectreactor.netty", "reactor-netty-core", "1.3.4",
     "reactor.netty.resources.DefaultLoopResources", "onServer"),
    ("io.projectreactor.netty", "reactor-netty-http", "1.3.4",
     "reactor.netty.http.server.HttpResources", "get"),
    ("io.projectreactor.netty", "reactor-netty-core", "1.3.4",
     "reactor.netty.transport.ServerTransportConfig", "eventLoopGroup"),
    ("io.projectreactor.netty", "reactor-netty-core", "1.3.4",
     "reactor.netty.transport.ServerTransportConfig", "childEventLoopGroup"),
    ("io.netty", "netty-transport", "4.2.12.Final",
     "io.netty.channel.nio.AbstractNioChannel", "doBeginRead"),
    ("io.netty", "netty-transport", "4.2.12.Final",
     "io.netty.channel.nio.NioIoHandler", "run"),
    ("io.projectreactor.netty", "reactor-netty-core", "1.3.4",
     "reactor.netty.transport.ServerTransport$Acceptor", "channelRead"),
    ("io.netty", "netty-transport", "4.2.12.Final",
     "io.netty.bootstrap.ServerBootstrap", "doBind"),
    ("io.netty", "netty-transport", "4.2.12.Final",
     "io.netty.bootstrap.AbstractBootstrap", "doBind"),
]


def find_jar(group: str, artifact: str, version: str) -> Path:
    pattern = str(CACHE / group / artifact / version / "**" / f"{artifact}-{version}.jar")
    for p in glob.glob(pattern, recursive=True):
        if "sources" not in p and "javadoc" not in p:
            return Path(p)
    raise FileNotFoundError(pattern)


def javap_lines(jar: Path, cls: str) -> list[str]:
    cp = subprocess.run(
        [str(JDK), "-classpath", str(jar), "-p", cls],
        capture_output=True,
        text=True,
        check=False,
    )
    if cp.returncode != 0:
        return [f"ERROR: {cp.stderr.strip()}"]
    return cp.stdout.splitlines()


def main() -> None:
    lines: list[str] = ["=== javap verification ==="]
    for group, artifact, version, cls, method in CHECKS:
        jar = find_jar(group, artifact, version)
        hits = [ln for ln in javap_lines(jar, cls) if f"{method}(" in ln.replace(" ", "")]
        lines.append("")
        lines.append(f"--- {cls}#{method}  jar={jar.name} ---")
        if hits:
            lines.extend(hits)
        else:
            lines.append("NOT FOUND")
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(OUT.read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
