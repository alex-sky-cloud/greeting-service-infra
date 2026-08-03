# -*- coding: utf-8 -*-
"""PNG: Block 0 — инициализация транспорта (reactive-study). Стиль: gen_webflux_netty_diagrams.py."""
from __future__ import annotations

import textwrap
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "images"
OUT.mkdir(parents=True, exist_ok=True)

BG = (248, 250, 252)
GAP = 20
PAD = 14
LINE_H = 20
TITLE_H = 26
FOOTER_PAD = 20


def max_chars_for_width(x1: int, x2: int) -> int:
    return max(12, (x2 - x1 - PAD * 2) // 8)


def wrapped_line_count(lines: tuple[str, ...], max_chars: int) -> int:
    total = 0
    for line in lines:
        total += len(textwrap.wrap(line, width=max_chars) or [line])
    return total


def box_height(x1: int, x2: int, lines: tuple[str, ...]) -> int:
    n = wrapped_line_count(lines, max_chars_for_width(x1, x2))
    return TITLE_H + 26 + n * LINE_H + 16


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    for p in (
        ("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        ("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
    ):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


F_TITLE = font(22, True)
F_SUB = font(15)
F_BOX_TITLE = font(15, True)
F_BOX = font(13)
F_STEP = font(14, True)
F_FOOTER = font(13)
F_NUM = font(15, True)


@dataclass
class Rect:
    x1: int
    y1: int
    x2: int
    y2: int
    title: str
    lines: tuple[str, ...] = ()
    fill: tuple[int, int, int] = (239, 246, 255)
    outline: tuple[int, int, int] = (37, 99, 235)
    title_color: tuple[int, int, int] = (0, 112, 192)

    @property
    def cx(self) -> int:
        return (self.x1 + self.x2) // 2

    @property
    def cy(self) -> int:
        return (self.y1 + self.y2) // 2

    @property
    def right(self) -> int:
        return self.x2

    @property
    def left(self) -> int:
        return self.x1

    @property
    def top(self) -> int:
        return self.y1

    @property
    def bottom(self) -> int:
        return self.y2

    def draw(self, d: ImageDraw.ImageDraw) -> None:
        d.rounded_rectangle(
            (self.x1, self.y1, self.x2, self.y2), radius=14, fill=self.fill, outline=self.outline, width=2
        )
        y = self.y1 + PAD
        d.text((self.x1 + PAD, y), self.title, fill=self.title_color, font=F_BOX_TITLE, anchor="la")
        y += TITLE_H
        max_chars = max_chars_for_width(self.x1, self.x2)
        for line in self.lines:
            for part in textwrap.wrap(line, width=max_chars) or [line]:
                d.text((self.x1 + PAD, y), part, fill=(30, 41, 59), font=F_BOX, anchor="la")
                y += LINE_H


def box(x1: int, y1: int, x2: int, title: str, lines: tuple[str, ...] = (), **kw) -> Rect:
    h = box_height(x1, x2, lines)
    return Rect(x1, y1, x2, y1 + h, title, lines, **kw)


def header(d: ImageDraw.ImageDraw, w: int, title: str, subtitle: str) -> None:
    d.text((w // 2, 22), title, fill=(15, 23, 42), font=F_TITLE, anchor="mm")
    d.text((w // 2, 50), subtitle, fill=(100, 116, 139), font=F_SUB, anchor="mm")
    d.line([(40, 72), (w - 40, 72)], fill=(226, 232, 240), width=2)


def footer_height(lines: list[str]) -> int:
    wrapped: list[str] = []
    for line in lines:
        wrapped.extend(textwrap.wrap(line, width=95) or [""])
    return 32 + len(wrapped) * 22


def footer(d: ImageDraw.ImageDraw, w: int, y1: int, lines: list[str]) -> None:
    wrapped: list[str] = []
    for line in lines:
        wrapped.extend(textwrap.wrap(line, width=95) or [""])
    box_h = 32 + len(wrapped) * 22
    y2 = y1 + box_h
    d.rounded_rectangle((40, y1, w - 40, y2), radius=12, fill=(255, 255, 255), outline=(203, 213, 225), width=2)
    d.text((56, y1 + 12), "Итог:", fill=(15, 23, 42), font=F_BOX_TITLE, anchor="la")
    for i, ln in enumerate(wrapped):
        d.text((56, y1 + 38 + i * 22), ln, fill=(71, 85, 105), font=F_FOOTER, anchor="la")


def arrow_v(d: ImageDraw.ImageDraw, x: int, y1: int, y2: int, color: tuple[int, int, int], label: str | None = None) -> None:
    d.line([(x, y1), (x, y2 - 10)], fill=color, width=3)
    d.polygon([(x, y2), (x - 7, y2 - 12), (x + 7, y2 - 12)], fill=color)
    if label:
        d.text((x + 14, (y1 + y2) // 2), label, fill=color, font=F_BOX, anchor="lm")


def step_badge(d: ImageDraw.ImageDraw, x: int, y: int, n: int, color: tuple[int, int, int]) -> None:
    d.ellipse((x - 18, y - 18, x + 18, y + 18), fill=color, outline=(255, 255, 255), width=2)
    d.text((x, y), str(n), fill=(255, 255, 255), font=F_NUM, anchor="mm")


def draw_init_chronology() -> None:
    w = 1480
    cx = w // 2
    footer_lines = [
        "Init = один раз при перезапуске. Boss принимает TCP позже (первый curl), не при boot.",
        "В runtime нет ServerBootstrap / NioEventLoopGroup — Reactor Netty 1.3 использует TransportConnector и Netty 4.2 API.",
    ]

    y = 92
    rects: list[Rect] = []
    arrows: list[tuple[int, int, int, tuple[int, int, int], str | None]] = []
    badges: list[tuple[int, int, int, tuple[int, int, int]]] = []
    notes: list[tuple[int, int, str, tuple[int, int, int]]] = []

    p0 = box(cx - 320, y, cx + 320, "До bind — HTTP-порт закрыт",
             ("SpringApplication.run()", "Flyway, R2DBC, beans", "Listening socket ещё нет"),
             fill=(245, 245, 245), outline=(158, 158, 158), title_color=(66, 66, 66))
    rects.append(p0)
    y = p0.bottom + GAP
    arrows.append((cx, p0.bottom + 4, y + 24, (100, 116, 139), "NettyWebServer.start()"))
    y += 28

    badges.append((72, y + 40, 1, (46, 125, 50)))
    s1 = box(cx - 340, y, cx + 340, "Spring Boot — сборка сервера",
             ("NettyReactiveWebServerFactory.getWebServer()", "HttpServer (порт 8083)", "NettyWebServer + ReactorHttpHandlerAdapter"),
             fill=(232, 245, 233), outline=(46, 125, 50), title_color=(27, 94, 32))
    rects.append(s1)
    y = s1.bottom + GAP
    arrows.append((cx, s1.bottom + 4, y + 24, (21, 101, 192), None))
    y += 28

    badges.append((72, y + 40, 2, (21, 101, 192)))
    s2 = box(cx - 280, y, cx + 280, "Старт bind",
             ("NettyWebServer.start() → bindNow()", "Mono.block() на потоке server"),
             fill=(227, 242, 253), outline=(21, 101, 192), title_color=(13, 71, 161))
    rects.append(s2)
    y = s2.bottom + GAP
    arrows.append((cx, s2.bottom + 4, y + 24, (106, 27, 154), None))
    y += 28

    badges.append((72, y + 40, 3, (106, 27, 154)))
    s3 = box(cx - 300, y, cx + 300, "Reactor Netty — transport",
             ("ServerTransport.bind()", "TransportConnector.bind()"),
             fill=(237, 231, 246), outline=(106, 27, 154), title_color=(74, 20, 140))
    rects.append(s3)
    y = s3.bottom + GAP
    arrows.append((cx, s3.bottom + 4, y + 24, (239, 108, 0), None))
    y += 28

    badges.append((72, y + 44, 4, (239, 108, 0)))
    boss = box(cx - 420, y, cx - 30, "Acceptor / Boss",
               ("DefaultLoopResources.onServerSelect()", "EventLoopGroup acceptor", "поток reactor-http-nio-*"),
               fill=(255, 243, 224), outline=(239, 108, 0), title_color=(230, 81, 0))
    worker = box(cx + 30, y, cx + 420, "Worker pool",
                 ("DefaultLoopResources.onServer()", "EventLoopGroup worker", "≈ CPU потоков (мин. 4)"),
                 fill=(220, 237, 200), outline=(85, 139, 47), title_color=(51, 105, 30))
    rects.extend((boss, worker))
    row_bottom = max(boss.bottom, worker.bottom)
    notes.append((cx, row_bottom + 16, "У каждого потока создаётся Selector (NioIoHandler)", (194, 24, 91)))
    y = row_bottom + 44
    arrows.append((cx, row_bottom + 4, y + 24, (194, 24, 91), None))
    y += 28

    badges.append((72, y + 44, 5, (194, 24, 91)))
    s5 = box(cx - 360, y, cx + 360, "Server Channel + порт 8083",
             ("TransportConnector.doInitAndRegister()", "ServerSocketChannel → Netty Channel",
              "register на acceptor EventLoop · bind(8083)", "doBeginRead() — OP_ACCEPT"),
             fill=(252, 228, 236), outline=(194, 24, 91), title_color=(136, 14, 79))
    rects.append(s5)
    y = s5.bottom + GAP
    arrows.append((cx, s5.bottom + 4, y + 24, (0, 131, 143), None))
    y += 28

    badges.append((72, y + 44, 6, (0, 131, 143)))
    s6 = box(cx - 340, y, cx + 340, "Готово — ждём TCP",
             ('Лог: "Netty started on port 8083"', "Started ReactiveStudyApplication",
              "Client Channel = 0 · HTTP pipeline ещё не работал"),
             fill=(224, 247, 250), outline=(0, 131, 143), title_color=(0, 96, 100))
    rects.append(s6)

    content_bottom = s6.bottom
    h = content_bottom + GAP + footer_height(footer_lines) + FOOTER_PAD
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    header(
        d, w,
        "Block 0 — инициализация транспорта при старте Spring WebFlux",
        "reactive-study · Boot 4.0.5 · Reactor Netty 1.3.4 · Netty 4.2 · порт 8083",
    )

    for r in rects:
        r.draw(d)
    for x, y1, y2, color, label in arrows:
        arrow_v(d, x, y1, y2, color, label)
    for x, yb, n, color in badges:
        step_badge(d, x, yb, n, color)
    for x, yn, text, color in notes:
        d.text((x, yn), text, fill=color, font=F_STEP, anchor="mm")

    footer(d, w, content_bottom + GAP, footer_lines)

    path = OUT / "block0-init-chronology.png"
    img.save(path, "PNG")
    print("saved", path, f"({w}x{h})")


def draw_state_after_init() -> None:
    w = 1480
    footer_lines = [
        "После init: порт открыт, boss ждёт accept. Worker-потоки живы, но без клиентских соединений.",
    ]

    sel = box(80, 150, 340, "Selector", ("NioIoHandler.select()", "следит за server Channel"))
    srv = box(380, 150, 680, "Server Channel", (":8083 listening socket", "OP_ACCEPT — ждёт connect"))
    w1 = box(800, 160, 1040, "Worker #1", ("Selector", "Client Channel: 0"))
    w2 = box(1080, 160, 1320, "Worker #2", ("Selector", "Client Channel: 0"))
    wn = box(940, 300, 1180, "Worker #N", ("Selector", "Client Channel: 0"))

    zone_bottom = max(sel.bottom, srv.bottom, w1.bottom, w2.bottom, wn.bottom) + 24
    later_y1 = zone_bottom + 36
    later_lines = (
        "accept() → Client Channel",
        "register на Worker",
        "ChannelPipeline (HttpServerCodec → WebFlux)",
    )
    later_h = 56 + len(later_lines) * LINE_H
    later_y2 = later_y1 + later_h
    content_bottom = later_y2
    h = content_bottom + GAP + footer_height(footer_lines) + FOOTER_PAD

    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    header(
        d, w,
        "Состояние транспорта после инициализации",
        "Момент: Started ReactiveStudyApplication — до первого HTTP-запроса",
    )

    d.rounded_rectangle((48, 96, 720, zone_bottom), radius=16, fill=(255, 248, 225), outline=(255, 160, 0), width=2)
    d.text((64, 108), "Acceptor EventLoop (boss-роль)", fill=(230, 81, 0), font=F_BOX_TITLE, anchor="la")

    d.rounded_rectangle((760, 96, 1430, zone_bottom), radius=16, fill=(232, 245, 233), outline=(76, 175, 80), width=2)
    d.text((776, 108), "Worker EventLoop pool — готов, но пуст", fill=(46, 125, 50), font=F_BOX_TITLE, anchor="la")

    d.rounded_rectangle((200, later_y1, 1280, later_y2), radius=14, fill=(255, 235, 238), outline=(229, 57, 53), width=2)
    d.text((220, later_y1 + 12), "Появится только после первого curl (не Block 0):", fill=(198, 40, 40), font=F_BOX_TITLE, anchor="la")
    ly = later_y1 + 42
    for ln in later_lines:
        d.text((220, ly), ln, fill=(183, 28, 28), font=F_BOX, anchor="la")
        ly += LINE_H

    for r in (sel, srv, w1, w2, wn):
        r.draw(d)

    arrow_v(d, srv.cx, srv.bottom + 8, later_y1 - 4, (229, 57, 53), "первый TCP")

    footer(d, w, content_bottom + GAP, footer_lines)

    path = OUT / "block0-init-state-after.png"
    img.save(path, "PNG")
    print("saved", path, f"({w}x{h})")


def main() -> None:
    draw_init_chronology()
    draw_state_after_init()


if __name__ == "__main__":
    main()
