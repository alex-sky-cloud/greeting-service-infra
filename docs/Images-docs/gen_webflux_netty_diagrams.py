# -*- coding: utf-8 -*-
"""PNG для spring-webflux-netty-event-loop.md — стиль terraform/k8s: крупный текст, стрелки только между блоками."""
from __future__ import annotations

import textwrap
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent
BG = (248, 250, 252)
GAP = 22


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    for p in (
        ("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        ("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
    ):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


F_TITLE = font(20, True)
F_SUB = font(14)
F_BOX_TITLE = font(16, True)
F_BOX = font(14)
F_LABEL = font(13)
F_FOOTER = font(14)
F_NUM = font(13, True)


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
            (self.x1, self.y1, self.x2, self.y2), radius=12, fill=self.fill, outline=self.outline, width=2
        )
        pad = 16
        y = self.y1 + pad
        d.text((self.x1 + pad, y), self.title, fill=self.title_color, font=F_BOX_TITLE, anchor="la")
        y += 28
        max_chars = max(10, (self.x2 - self.x1 - pad * 2) // 9)
        for line in self.lines:
            for part in textwrap.wrap(line, width=max_chars) or [line]:
                if y > self.y2 - pad - 4:
                    return
                d.text((self.x1 + pad, y), part, fill=(30, 41, 59), font=F_BOX, anchor="la")
                y += 22


def box(x1: int, y1: int, x2: int, title: str, lines: tuple[str, ...] = (), **kw) -> Rect:
    """Высота блока по числу строк."""
    h = 28 + 28 + len(lines) * 22 + 20
    return Rect(x1, y1, x2, y1 + h, title, lines, **kw)


def header(d: ImageDraw.ImageDraw, w: int, title: str, subtitle: str) -> None:
    d.text((32, 18), title, fill=(15, 23, 42), font=F_TITLE, anchor="la")
    d.text((32, 48), subtitle, fill=(100, 116, 139), font=F_SUB, anchor="la")
    d.line([(32, 76), (w - 32, 76)], fill=(226, 232, 240), width=2)


def footer(d: ImageDraw.ImageDraw, w: int, h: int, lines: list[str]) -> None:
    wrapped: list[str] = []
    for line in lines:
        wrapped.extend(textwrap.wrap(line, width=90) or [""])
    box_h = 36 + len(wrapped) * 24
    y2 = h - 24
    y1 = y2 - box_h
    d.rounded_rectangle((32, y1, w - 32, y2), radius=12, fill=(255, 255, 255), outline=(203, 213, 225), width=2)
    d.text((48, y1 + 14), "Пояснение:", fill=(15, 23, 42), font=F_BOX_TITLE, anchor="la")
    for i, ln in enumerate(wrapped):
        d.text((48, y1 + 44 + i * 24), ln, fill=(71, 85, 105), font=F_FOOTER, anchor="la")


def arrow_h(d: ImageDraw.ImageDraw, x1: int, x2: int, y: int, color: tuple[int, int, int], label: str | None = None) -> None:
    if x2 > x1:
        d.line([(x1, y), (x2 - 10, y)], fill=color, width=3)
        d.polygon([(x2, y), (x2 - 12, y - 6), (x2 - 12, y + 6)], fill=color)
    else:
        d.line([(x1, y), (x2 + 10, y)], fill=color, width=3)
        d.polygon([(x2, y), (x2 + 12, y - 6), (x2 + 12, y + 6)], fill=color)
    if label:
        d.text(((x1 + x2) // 2, y - 18), label, fill=color, font=F_LABEL, anchor="mm")


def arrow_v(d: ImageDraw.ImageDraw, x: int, y1: int, y2: int, color: tuple[int, int, int], label: str | None = None) -> None:
    if y2 > y1:
        d.line([(x, y1), (x, y2 - 10)], fill=color, width=3)
        d.polygon([(x, y2), (x - 6, y2 - 12), (x + 6, y2 - 12)], fill=color)
    else:
        d.line([(x, y1), (x, y2 + 10)], fill=color, width=3)
        d.polygon([(x, y2), (x - 6, y2 + 12), (x + 6, y2 + 12)], fill=color)
    if label:
        d.text((x + 20, (y1 + y2) // 2), label, fill=color, font=F_LABEL, anchor="lm")


def arrow_path(d: ImageDraw.ImageDraw, points: list[tuple[int, int]], color: tuple[int, int, int], label: str | None = None) -> None:
    for i in range(len(points) - 2):
        d.line([points[i], points[i + 1]], fill=color, width=3)
    x1, y1 = points[-2]
    x2, y2 = points[-1]
    if abs(x2 - x1) >= abs(y2 - y1):
        arrow_h(d, x1, x2, y2, color)
    else:
        arrow_v(d, x2, y1, y2, color)
    if label and len(points) >= 3:
        lx, ly = points[1]
        nx, ny = points[2]
        d.text(((lx + nx) // 2 + 12, (ly + ny) // 2), label, fill=color, font=F_LABEL, anchor="lm")


def h_link(d: ImageDraw.ImageDraw, a: Rect, b: Rect, color: tuple[int, int, int], label: str | None = None, y: int | None = None) -> None:
    y = a.cy if y is None else y
    arrow_h(d, a.right + GAP, b.left - GAP, y, color, label)


def v_link(d: ImageDraw.ImageDraw, a: Rect, b: Rect, color: tuple[int, int, int], label: str | None = None, x: int | None = None) -> None:
    x = a.cx if x is None else x
    arrow_v(d, x, a.bottom + GAP, b.top - GAP, color, label)


def render(w: int, h: int, title: str, subtitle: str, rects: list[Rect], arrows: Callable[[ImageDraw.ImageDraw], None], notes: list[str]) -> Image.Image:
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    header(d, w, title, subtitle)
    arrows(d)
    for r in rects:
        r.draw(d)
    footer(d, w, h, notes)
    return img


def save(img: Image.Image, name: str) -> None:
    p = OUT / name
    img.save(p, "PNG")
    print("saved", p)


def netty_vs_tomcat() -> None:
    w, h = 1280, 680
    tomcat = box(40, 88, 360, "Tomcat (Servlet)", ("поток блокируется", "на время запроса"), fill=(254, 242, 242), outline=(220, 38, 38), title_color=(185, 28, 28))
    reqs = [
        box(40, 200 + i * 78, 200, f"Запрос {n}", (), fill=(254, 226, 226), outline=(248, 113, 113), title_color=(185, 28, 28))
        for i, n in enumerate(["1", "2", "N"], 0)
    ]
    threads = [
        box(280, 200 + i * 78, 440, f"Thread {n}", (), fill=(254, 202, 202), outline=(239, 68, 68), title_color=(185, 28, 28))
        for i, n in enumerate(["1", "2", "N"], 0)
    ]

    netty_hdr = box(660, 88, 1220, "Netty (WorkerGroup)", ("N EventLoop потоков",), fill=(236, 253, 245), outline=(5, 150, 105), title_color=(5, 120, 85))
    channels = [
        box(660, 200 + i * 78, 840, f"Ch-{n}", (), fill=(220, 252, 231), outline=(52, 211, 153), title_color=(5, 120, 85))
        for i, n in enumerate(["A", "B", "C", "D"])
    ]
    eloop = box(
        960, 200, 1200, "EventLoop #1",
        ("1 поток · Selector", "обслуживает Ch-A…D"),
        fill=(209, 250, 229), outline=(16, 185, 129), title_color=(5, 120, 85),
    )
    eloop = Rect(eloop.x1, 200, eloop.x2, channels[-1].bottom, eloop.title, eloop.lines, eloop.fill, eloop.outline, eloop.title_color)

    rects = [tomcat, *reqs, *threads, netty_hdr, *channels, eloop]

    def arrows(d: ImageDraw.ImageDraw) -> None:
        for req, thr in zip(reqs, threads):
            h_link(d, req, thr, (220, 38, 38), "1:1")
        for i, ch in enumerate(channels):
            h_link(d, ch, eloop, (5, 150, 105), "N:1" if i == 0 else None)
        d.text((560, 340), "vs", fill=(100, 116, 139), font=font(22, True), anchor="mm")

    img = render(w, h, "Netty vs Tomcat: модель потоков",
                 "Слева 1:1 поток на запрос · справа N Channel на один EventLoop", rects, arrows, [
                     "Tomcat: отдельный поток ОС на запрос — при высокой конкуренции растут память и переключения контекста.",
                     "Netty: много Channel на один EventLoop; I/O через Selector (epoll/kqueue), поток не блокируется.",
                 ])
    save(img, "webflux-netty-vs-tomcat.png")


def netty_architecture() -> None:
    w, h = 1280, 620
    client = box(40, 100, 200, "Клиент", ("TCP connect",))
    boss = box(240, 100, 420, "BossGroup", ("1 поток", "accept"), fill=(255, 251, 235), outline=(217, 119, 6), title_color=(180, 83, 9))
    worker = box(460, 100, 660, "WorkerGroup", ("N EventLoop", "I/O"))
    channel = box(700, 100, 900, "Channel", ("NioSocketChannel",), fill=(236, 253, 245), outline=(5, 150, 105), title_color=(5, 120, 85))
    loops = [box(120 + i * 280, 260, 320 + i * 280, f"EventLoop {i+1}", ("Selector + Queue",)) for i in range(3)]
    pipeline = box(460, 420, 720, "ChannelPipeline", ("Inbound / Outbound",), fill=(250, 245, 255), outline=(168, 85, 247), title_color=(126, 34, 206))
    rects = [client, boss, worker, channel, *loops, pipeline]

    def arrows(d: ImageDraw.ImageDraw) -> None:
        h_link(d, client, boss, (71, 85, 105), "connect")
        h_link(d, boss, worker, (71, 85, 105), "register")
        h_link(d, worker, channel, (71, 85, 105), "assign")
        v_link(d, worker, loops[1], (37, 99, 235), "loop", x=worker.cx)
        v_link(d, channel, pipeline, (168, 85, 247), "handlers", x=channel.cx)

    img = render(w, h, "Архитектура Netty-сервера", "BossGroup принимает · WorkerGroup обрабатывает I/O", rects, arrows, [
        "BossGroup: один поток accept, передача канала в WorkerGroup.",
        "Channel навсегда привязан к одному EventLoop.",
    ])
    save(img, "webflux-netty-architecture.png")


def channel_pipeline() -> None:
    w, h = 1280, 580
    in_boxes = [
        box(40, 100, 220, "Socket read", ()),
        box(260, 100, 440, "HTTP codec", ()),
        box(480, 100, 660, "WebFlux", ()),
        box(700, 100, 920, "Controller", ("return Mono",)),
    ]
    out_boxes = [
        box(480, 300, 660, "HTTP codec", (), fill=(236, 253, 245), outline=(5, 150, 105), title_color=(5, 120, 85)),
        box(700, 300, 920, "Socket write", (), fill=(236, 253, 245), outline=(5, 150, 105), title_color=(5, 120, 85)),
        box(960, 300, 1160, "Клиент", (), fill=(236, 253, 245), outline=(5, 150, 105), title_color=(5, 120, 85)),
    ]
    rects = in_boxes + out_boxes

    def arrows(d: ImageDraw.ImageDraw) -> None:
        d.text((40, 82), "INBOUND →", fill=(37, 99, 235), font=F_BOX_TITLE, anchor="la")
        d.text((40, 282), "OUTBOUND ←", fill=(5, 150, 105), font=F_BOX_TITLE, anchor="la")
        for i in range(len(in_boxes) - 1):
            h_link(d, in_boxes[i], in_boxes[i + 1], (37, 99, 235))
        for i in range(len(out_boxes) - 1):
            h_link(d, out_boxes[i], out_boxes[i + 1], (5, 150, 105))
        ctrl, out0 = in_boxes[-1], out_boxes[0]
        bus_y = 248
        arrow_path(d, [
            (ctrl.cx, ctrl.bottom + GAP),
            (ctrl.cx, bus_y),
            (out0.cx, bus_y),
            (out0.cx, out0.top - GAP),
        ], (217, 119, 6), "Mono/Flux")

    img = render(w, h, "ChannelPipeline", "Inbound: сеть → приложение · Outbound: приложение → сеть", rects, arrows, [
        "InboundHandler читает сокет; OutboundHandler пишет ответ.",
        "Mono/Flux связывает inbound и outbound в коридоре между рядами блоков.",
    ])
    save(img, "webflux-netty-channel-pipeline.png")


def startup_sequence() -> None:
    w, h = 1280, 600
    cols = [120, 340, 560, 780, 1000]
    tops: list[Rect] = []
    for x, name in zip(cols, ["ServerBootstrap", "BossGroup", "WorkerGroup", "EventLoop", "Channel"]):
        tops.append(box(x - 90, 88, x + 90, name, ()))

    def arrows(d: ImageDraw.ImageDraw) -> None:
        for x, r in zip(cols, tops):
            d.line([(x, r.bottom), (x, h - 160)], fill=(203, 213, 225), width=2)
        steps = [(0, 1, "bind", 200), (1, 2, "accept", 280), (2, 3, "register", 360), (3, 4, "pipeline", 440)]
        for i, (a, b, label, y) in enumerate(steps, 1):
            d.ellipse((40, y - 14, 68, y + 14), fill=(241, 245, 249), outline=(148, 163, 184), width=2)
            d.text((54, y), str(i), fill=(51, 65, 85), font=F_NUM, anchor="mm")
            arrow_h(d, cols[a] + 90 + GAP, cols[b] - 90 - GAP, y, (37, 99, 235), label)

    img = render(w, h, "Старт Netty-сервера WebFlux", "Boss accept → Worker register → Selector → pipeline", tops, arrows, [
        "WebFlux поднимает Reactor Netty: boss (1) + worker (N).",
        "Read/write канала — только на назначенном EventLoop.",
    ])
    save(img, "webflux-seq-netty-startup.png")


def _event_loop_diagram(name: str, title: str, subtitle: str, queue_items: list[str], channels: list[str], notes: list[str]) -> None:
    w, h = 1280, 640
    eloop = box(40, 96, 280, "EventLoop", ("неблокирующий цикл",))
    selector = box(340, 96, 540, "Selector", ("epoll / kqueue",), fill=(241, 245, 249), outline=(100, 116, 139), title_color=(71, 85, 105))

    q_left, q_right, q_top = 40, 540, 220
    q_h = 56 + len(queue_items) * 72
    tasks: list[Rect] = []
    for i, item in enumerate(queue_items):
        t = box(60, q_top + 48 + i * 72, q_right - 24, item, ())
        tasks.append(Rect(t.x1, t.y1, t.x2, t.y2, t.title, t.lines, (254, 249, 195), (234, 179, 8), (146, 64, 14)))

    ch_boxes = [box(820, 220 + i * 88, 1020, ch, ()) for i, ch in enumerate(channels)]
    for c in ch_boxes:
        c.fill, c.outline, c.title_color = (220, 252, 231), (52, 211, 153), (5, 120, 85)

    rects = [eloop, selector, *tasks, *ch_boxes]

    def arrows(d: ImageDraw.ImageDraw) -> None:
        h_link(d, eloop, selector, (71, 85, 105), "poll")
        for i, ch in enumerate(ch_boxes):
            arrow_h(d, ch.left - GAP, q_right, ch.cy, (5, 150, 105), "enqueue" if i == 0 else None)

    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    header(d, w, title, subtitle)
    arrows(d)
    d.rounded_rectangle((q_left, q_top, q_right, q_top + q_h), radius=12, fill=(255, 251, 235), outline=(202, 138, 4), width=2)
    d.text((60, q_top + 16), "EventQueue (FIFO)", fill=(146, 64, 14), font=F_BOX_TITLE, anchor="la")
    for r in rects:
        r.draw(d)
    footer(d, w, h, notes)
    save(img, name)


def diagram_queue_1() -> None:
    _event_loop_diagram("webflux-seq-event-queue-1.png", "Новый запрос в EventQueue",
                        "R1 ставится в очередь EventLoop", ["Task: parse HTTP R1"], ["Channel #1"],
                        ["Запрос на Channel #1 → задача в FIFO-очереди."])


def diagram_queue_2() -> None:
    _event_loop_diagram("webflux-seq-event-queue-2.png", "Второй канал на том же EventLoop",
                        "R2 в очереди после R1", ["Task: R1 (в работе)", "Task: R2 (ожидает)"],
                        ["Channel #1", "Channel #2"], ["Один EventLoop; порядок FIFO."])


def diagram_blocking() -> None:
    w, h = 1280, 560
    eloop = box(60, 110, 280, "EventLoop", ("I/O",))
    elastic = box(380, 110, 620, "boundedElastic", ("отдельный пул",), fill=(252, 231, 243), outline=(219, 39, 119), title_color=(157, 23, 77))
    queue = box(60, 340, 280, "EventQueue", ("следующая задача",), fill=(255, 251, 235), outline=(202, 138, 4), title_color=(146, 64, 14))
    db = box(380, 340, 620, "DB / HTTP", ("блокирующий вызов",), fill=(254, 226, 226), outline=(220, 38, 38), title_color=(185, 28, 28))
    rects = [eloop, elastic, queue, db]

    def arrows(d: ImageDraw.ImageDraw) -> None:
        h_link(d, eloop, elastic, (219, 39, 119), "offload")
        v_link(d, elastic, db, (220, 38, 38), "blocking", x=elastic.cx)
        v_link(d, eloop, queue, (37, 99, 235), "next", x=eloop.cx)

    img = render(w, h, "Offload блокирующей операции", "Блокировка уходит в boundedElastic", rects, arrows, [
        "JDBC / sleep нельзя на EventLoop.",
        "subscribeOn(boundedElastic) — отдельный пул.",
    ])
    save(img, "webflux-seq-blocking-offload.png")


def diagram_cpu_done() -> None:
    w, h = 1280, 520
    rects = [
        box(60, 120, 260, "EventLoop", ("map · filter",)),
        box(340, 120, 580, "ChannelPipeline", ("Outbound",), fill=(250, 245, 255), outline=(168, 85, 247), title_color=(126, 34, 206)),
        box(660, 120, 860, "Клиент", ("HTTP 200",), fill=(220, 252, 231), outline=(52, 211, 153), title_color=(5, 120, 85)),
    ]

    def arrows(d: ImageDraw.ImageDraw) -> None:
        h_link(d, rects[0], rects[1], (37, 99, 235), "process")
        h_link(d, rects[1], rects[2], (5, 150, 105), "write")

    img = render(w, h, "CPU-bound на EventLoop", "Неблокирующая обработка → ответ", rects, arrows, [
        "map/filter на EventLoop без смены потока.",
    ])
    save(img, "webflux-seq-cpu-response.png")


def diagram_requeue() -> None:
    w, h = 1280, 520
    rects = [
        box(60, 120, 300, "boundedElastic", ("worker done",), fill=(252, 231, 243), outline=(219, 39, 119), title_color=(157, 23, 77)),
        box(400, 120, 640, "EventQueue", ("finish R1",), fill=(255, 251, 235), outline=(202, 138, 4), title_color=(146, 64, 14)),
        box(740, 120, 980, "EventLoop", ("resume I/O",)),
    ]

    def arrows(d: ImageDraw.ImageDraw) -> None:
        h_link(d, rects[0], rects[1], (202, 138, 4), "complete")
        h_link(d, rects[1], rects[2], (37, 99, 235), "pick")

    img = render(w, h, "Worker завершил задачу", "Результат снова в EventQueue", rects, arrows, [
        "Продолжение планируется на EventLoop канала.",
    ])
    save(img, "webflux-seq-requeue.png")


def diagram_final() -> None:
    w, h = 1280, 520
    rects = [
        box(60, 120, 280, "EventLoop", ("завершение R1",)),
        box(360, 120, 580, "Channel #1", ("тот же сокет",), fill=(220, 252, 231), outline=(52, 211, 153), title_color=(5, 120, 85)),
        box(660, 120, 880, "HTTP 200", ("ответ",), fill=(209, 250, 229), outline=(16, 185, 129), title_color=(5, 120, 85)),
    ]

    def arrows(d: ImageDraw.ImageDraw) -> None:
        h_link(d, rects[0], rects[1], (37, 99, 235), "resume")
        h_link(d, rects[1], rects[2], (5, 150, 105), "response")

    img = render(w, h, "Ответ по Channel", "EventLoop пишет ответ клиенту", rects, arrows, [
        "Один Channel — один EventLoop до закрытия.",
    ])
    save(img, "webflux-seq-final-response.png")


def webflux_threading() -> None:
    w, h = 1280, 560
    y = 120
    rects = [
        box(40, y, 220, "WebClient", ("HTTP",)),
        box(280, y, 460, "subscribeOn", ("boundedElastic",), fill=(255, 251, 235), outline=(217, 119, 6), title_color=(180, 83, 9)),
        box(520, y, 720, "boundedElastic", ("блокирующий I/O",), fill=(252, 231, 243), outline=(219, 39, 119), title_color=(157, 23, 77)),
        box(780, y, 960, "resume", ("EventLoop",), fill=(236, 253, 245), outline=(5, 150, 105), title_color=(5, 120, 85)),
        box(1020, y, 1200, "Netty I/O", ("event loop",)),
    ]

    def arrows(d: ImageDraw.ImageDraw) -> None:
        for i in range(len(rects) - 1):
            h_link(d, rects[i], rects[i + 1], (37, 99, 235) if i >= 3 else (71, 85, 105))

    img = render(w, h, "Потоки Spring WebFlux", "WebClient → elastic → resume → EventLoop", rects, arrows, [
        "WebFlux: cores × 2 event-loop потоков.",
        "subscribeOn(boundedElastic) для блокирующих участков.",
    ])
    save(img, "webflux-threading-model.png")


def main() -> None:
    netty_vs_tomcat()
    netty_architecture()
    channel_pipeline()
    startup_sequence()
    diagram_queue_1()
    diagram_queue_2()
    diagram_blocking()
    diagram_cpu_done()
    diagram_requeue()
    diagram_final()
    webflux_threading()


if __name__ == "__main__":
    main()
