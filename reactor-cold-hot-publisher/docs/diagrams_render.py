# -*- coding: utf-8 -*-
"""Render PNG diagrams for reactor-cold-hot-publisher/docs/README.md."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Literal

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
OUT_DIR = ROOT / "images"
RENDER_SCALE = 3
OUTPUT_DPI = 300
LOGICAL_DPI = 150
GAP_PX = round(5 / 25.4 * LOGICAL_DPI)
LINE_CLEARANCE = 4
ARROW = 10
LINE_W = 2

FONT_DIAGRAM_TITLE = 26
FONT_GROUP = 16
FONT_BOX_TITLE = 19
FONT_BOX_BODY = 15
FONT_LINE_LABEL = 16
FONT_NOTE = 15
FONT_ENDPOINT = 22
FONT_ENDPOINT_DESC = 16
ENDPOINT_TEXT_PAD = 68

Side = Literal["n", "s", "e", "w"]


@dataclass(frozen=True)
class Rect:
    x1: int
    y1: int
    x2: int
    y2: int

    @property
    def cx(self) -> int:
        return (self.x1 + self.x2) // 2

    @property
    def cy(self) -> int:
        return (self.y1 + self.y2) // 2

    def inflate(self, p: int) -> Rect:
        return Rect(self.x1 - p, self.y1 - p, self.x2 + p, self.y2 + p)

    def port(self, side: Side) -> tuple[int, int]:
        if side == "n":
            return self.cx, self.y1
        if side == "s":
            return self.cx, self.y2
        if side == "w":
            return self.x1, self.cy
        return self.x2, self.cy


@dataclass
class _BoxCmd:
    rect: Rect
    title: str
    lines: list[str]
    fill: tuple[int, int, int]
    outline: tuple[int, int, int]
    tc: tuple[int, int, int]
    title_size: int = FONT_BOX_TITLE
    body_size: int = FONT_BOX_BODY
    text_pad_x: int = 14
    title_bold: bool = True
    line_step: int = 24
    mask_connector_left: bool = False


@dataclass
class _GroupCmd:
    rect: Rect
    title: str


@dataclass
class _TextCmd:
    xy: tuple[int, int]
    value: str
    size: int
    color: tuple[int, int, int]
    bold: bool


@dataclass
class _LineCmd:
    points: list[tuple[int, int]]
    label: str | None
    color: tuple[int, int, int]
    dashed: bool
    arrow_end: bool


class Diagram:
    def __init__(self, width: int, height: int, title: str):
        self.logical_w = width
        self.logical_h = height
        self.scale = RENDER_SCALE
        pw, ph = width * self.scale, height * self.scale
        self.img = Image.new("RGB", (pw, ph), (248, 250, 252))
        self.draw = ImageDraw.Draw(self.img)
        self.obstacles: list[Rect] = []
        self._boxes: list[_BoxCmd] = []
        self._groups: list[_GroupCmd] = []
        self._texts: list[_TextCmd] = []
        self._lines: list[_LineCmd] = []
        self._title = title

    def _s(self, v: int | float) -> int:
        return int(round(v * self.scale))

    def _sp(self, xy: tuple[int, int]) -> tuple[int, int]:
        return self._s(xy[0]), self._s(xy[1])

    def _draw_text(self, xy: tuple[int, int], value: str, size: int, color: tuple[int, int, int], bold: bool) -> None:
        self.draw.text(self._sp(xy), value, fill=color, font=_font(size * self.scale, bold))

    def _draw_text_halo(
        self, xy: tuple[int, int], value: str, size: int, color: tuple[int, int, int], bold: bool = False, *, pad: int = 5
    ) -> None:
        font = _font(size * self.scale, bold)
        x, y = self._sp(xy)
        bb = self.draw.textbbox((x, y), value, font=font)
        p = self._s(pad)
        self.draw.rectangle([bb[0] - p, bb[1] - p, bb[2] + p, bb[3] + p], fill=(248, 250, 252))
        self.draw.text((x, y), value, fill=color, font=font)

    def group(self, rect: Rect, title: str) -> None:
        self._groups.append(_GroupCmd(rect, title))

    def box(
        self,
        rect: Rect,
        title: str,
        lines: list[str] | None = None,
        fill=(255, 255, 255),
        outline=(55, 65, 81),
        tc=(0, 112, 192),
        *,
        title_size: int = FONT_BOX_TITLE,
        body_size: int = FONT_BOX_BODY,
        text_pad_x: int = 14,
        title_bold: bool = True,
        line_step: int = 24,
        mask_connector_left: bool = False,
    ) -> Rect:
        lines = lines or []
        self.obstacles.append(rect.inflate(LINE_CLEARANCE))
        self._boxes.append(
            _BoxCmd(rect, title, lines, fill, outline, tc, title_size, body_size, text_pad_x, title_bold, line_step, mask_connector_left)
        )
        return rect

    def endpoint_box(self, rect: Rect, path: str, description: str) -> Rect:
        return self.box(
            rect,
            path,
            [description],
            tc=(15, 23, 42),
            title_size=FONT_ENDPOINT,
            body_size=FONT_ENDPOINT_DESC,
            text_pad_x=ENDPOINT_TEXT_PAD,
            title_bold=True,
            line_step=26,
            mask_connector_left=True,
        )

    def text(self, xy: tuple[int, int], value: str, size: int = FONT_NOTE, color=(30, 41, 59), bold: bool = False) -> None:
        self._texts.append(_TextCmd(xy, value, size, color, bold))

    def connect(self, src: Rect, src_side: Side, dst: Rect, dst_side: Side, label: str | None = None, color=(71, 85, 105)) -> None:
        path = route_orthogonal(src, src_side, dst, dst_side, self.obstacles)
        self._lines.append(_LineCmd(path, label, color, False, True))

    def connect_points(
        self,
        points: list[tuple[int, int]],
        label: str | None = None,
        color=(71, 85, 105),
        dashed: bool = False,
        arrow_end: bool = True,
        *,
        strict: bool = False,
    ) -> None:
        path = simplify(points)
        if not path_clear(path, self.obstacles):
            routed: list[tuple[int, int]] | None = None
            for candidate in (
                route_points(path[0], path[-1], self.obstacles),
                reroute_polyline(path, self.obstacles),
            ):
                candidate = simplify(candidate)
                if path_clear(candidate, self.obstacles):
                    routed = candidate
                    break
            if routed is not None:
                path = routed
            elif strict:
                pass
        self._lines.append(_LineCmd(path, label, color, dashed, arrow_end))

    def connect_ports(
        self,
        src: Rect,
        src_side: Side,
        dst: Rect,
        dst_side: Side,
        *,
        label: str | None = None,
        via: list[tuple[int, int]] | None = None,
    ) -> None:
        g = GAP_PX
        path = [outward_point(src, src_side, g)]
        if via:
            path.extend(via)
        path.append(inward_point(dst, dst_side, g))
        path.append(dst.port(dst_side))
        self.connect_points(path, label=label, strict=True)

    def bus_h(self, y: int, x_from: int, x_to: int, *, arrow_end: bool = True, label: str | None = None) -> None:
        lo, hi = sorted((x_from, x_to))
        self.connect_points([(lo, y), (hi, y)], label=label, arrow_end=arrow_end, strict=True)

    def bus_v(self, x: int, y_from: int, y_to: int, *, arrow_end: bool = True, label: str | None = None) -> None:
        lo, hi = sorted((y_from, y_to))
        self.connect_points([(x, lo), (x, hi)], label=label, arrow_end=arrow_end, strict=True)

    def seq_call(self, src: Rect, dst: Rect, bus_y: int, label: str | None = None) -> None:
        """Message below a row of boxes: down → horizontal → up into dst."""
        self.connect_points(
            [(src.cx, src.y2), (src.cx, bus_y), (dst.cx, bus_y), (dst.cx, dst.y2)],
            label=label,
            strict=True,
        )

    def seq_return_west(self, src: Rect, dst: Rect, bus_y: int, west_x: int, label: str | None = None) -> None:
        """Response via left gutter; horizontal only below the actor row."""
        self.connect_points(
            [
                (src.cx, src.y2),
                (src.cx, bus_y),
                (west_x, bus_y),
                (dst.x1, bus_y),
                (dst.x1, dst.cy),
            ],
            label=label,
            dashed=True,
            strict=True,
        )

    def seq_skip(self, src: Rect, dst: Rect, bus_y: int, west_x: int, label: str | None = None) -> None:
        """Skip intermediate columns via left gutter (no horizontal through actor row)."""
        self.connect_points(
            [
                (src.cx, src.y2),
                (src.cx, bus_y),
                (west_x, bus_y),
                (dst.x1, bus_y),
                (dst.x1, dst.cy),
            ],
            label=label,
            strict=True,
        )

    @staticmethod
    def _label_xy(path: list[tuple[int, int]]) -> tuple[int, int]:
        for i in range(len(path) - 1):
            x1, y1 = path[i]
            x2, y2 = path[i + 1]
            if y1 == y2 and abs(x2 - x1) > 60:
                lo, hi = sorted((x1, x2))
                return (lo + (hi - lo) // 3, y1 - 24)
        mx, my = path[len(path) // 2]
        return mx + 8, my - 22

    def _flush(self) -> None:
        for cmd in self._lines:
            draw_polyline(self.draw, cmd.points, self.scale, color=cmd.color, arrow_end=cmd.arrow_end, dashed=cmd.dashed)

        for g in self._groups:
            x1, y1, x2, y2 = self._s(g.rect.x1), self._s(g.rect.y1), self._s(g.rect.x2), self._s(g.rect.y2)
            self.draw.rounded_rectangle((x1, y1, x2, y2), radius=self._s(14), outline=(100, 116, 139), width=self._s(2))
            self._draw_text((g.rect.x1 + 14, g.rect.y1 + 10), g.title, FONT_GROUP, (71, 85, 105), True)

        for b in self._boxes:
            x1, y1, x2, y2 = self._s(b.rect.x1), self._s(b.rect.y1), self._s(b.rect.x2), self._s(b.rect.y2)
            self.draw.rounded_rectangle((x1, y1, x2, y2), radius=self._s(10), fill=b.fill, outline=b.outline, width=self._s(2))
            if b.mask_connector_left:
                self.draw.rectangle(
                    (x1 + self._s(2), y1 + self._s(2), x1 + self._s(b.text_pad_x - 6), y2 - self._s(2)),
                    fill=b.fill,
                )
            ty = b.rect.y1 + 14
            self._draw_text((b.rect.x1 + b.text_pad_x, ty), b.title, b.title_size, b.tc, b.title_bold)
            ty += b.line_step
            for ln in b.lines:
                self._draw_text((b.rect.x1 + b.text_pad_x, ty), ln, b.body_size, (30, 41, 59), False)
                ty += b.line_step

        for cmd in self._lines:
            if cmd.label and len(cmd.points) >= 2:
                lx, ly = self._label_xy(cmd.points)
                color = cmd.color if not cmd.dashed else (30, 41, 59)
                self._draw_text_halo((lx, ly), cmd.label, FONT_LINE_LABEL, color, bold=True)

        for t in self._texts:
            self._draw_text(t.xy, t.value, t.size, t.color, t.bold)

        self._draw_text((20, 14), self._title, FONT_DIAGRAM_TITLE, (30, 41, 59), True)

    def save(self, name: str) -> None:
        self._flush()
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        self.img.save(OUT_DIR / name, "PNG", dpi=(OUTPUT_DPI, OUTPUT_DPI), optimize=True)


def _font(size: int, bold: bool = False):
    size = max(8, int(size))
    for p in (
        ("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        ("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
    ):
        if Path(p).exists():
            return ImageFont.truetype(p, size, layout_engine=ImageFont.Layout.BASIC)
    return ImageFont.load_default()


def segment_crosses_rect_interior(x1: int, y1: int, x2: int, y2: int, rect: Rect) -> bool:
    if x1 == x2:
        if not (rect.x1 < x1 < rect.x2):
            return False
        lo, hi = sorted((y1, y2))
        return lo < rect.y2 and hi > rect.y1
    if y1 == y2:
        if not (rect.y1 < y1 < rect.y2):
            return False
        lo, hi = sorted((x1, x2))
        return lo < rect.x2 and hi > rect.x1
    return True


def path_clear(points: list[tuple[int, int]], obstacles: list[Rect]) -> bool:
    for i in range(len(points) - 1):
        x1, y1 = points[i]
        x2, y2 = points[i + 1]
        if x1 != x2 and y1 != y2:
            return False
        for obs in obstacles:
            if segment_crosses_rect_interior(x1, y1, x2, y2, obs):
                return False
    return True


def validate_lines(lines: list[_LineCmd], obstacles: list[Rect], name: str) -> list[str]:
    errs: list[str] = []
    for i, cmd in enumerate(lines):
        if not path_clear(cmd.points, obstacles):
            errs.append(f"line {i} crosses a block")
    return errs


def simplify(points: list[tuple[int, int]]) -> list[tuple[int, int]]:
    if not points:
        return points
    out = [points[0]]
    for p in points[1:]:
        if p != out[-1]:
            out.append(p)
    cleaned: list[tuple[int, int]] = [out[0]]
    for i in range(1, len(out)):
        if i + 1 < len(out):
            ax, ay = cleaned[-1]
            bx, by = out[i]
            cx, cy = out[i + 1]
            if (ax == bx == cx) or (ay == by == cy):
                continue
        cleaned.append(out[i])
    return cleaned


def outward_point(rect: Rect, side: Side, gutter: int) -> tuple[int, int]:
    x, y = rect.port(side)
    if side == "e":
        return x + gutter, y
    if side == "w":
        return x - gutter, y
    if side == "s":
        return x, y + gutter
    return x, y - gutter


def inward_point(rect: Rect, side: Side, gutter: int) -> tuple[int, int]:
    x, y = rect.port(side)
    if side == "e":
        return x - gutter, y
    if side == "w":
        return x + gutter, y
    if side == "s":
        return x, y - gutter
    return x, y + gutter


def route_orthogonal(src: Rect, src_side: Side, dst: Rect, dst_side: Side, obstacles: list[Rect]) -> list[tuple[int, int]]:
    start = src.port(src_side)
    end = dst.port(dst_side)
    gutter = GAP_PX
    out_s = outward_point(src, src_side, gutter)
    in_d = inward_point(dst, dst_side, gutter)
    candidates: list[list[tuple[int, int]]] = [
        [start, out_s, (in_d[0], out_s[1]), in_d, end],
        [start, out_s, (out_s[0], in_d[1]), in_d, end],
        [start, out_s, in_d, end],
    ]
    sx, sy = out_s
    ex, ey = in_d
    candidates.extend(
        [
            [start, out_s, (sx, (sy + ey) // 2), (ex, (sy + ey) // 2), in_d, end],
            [start, out_s, (max(sx, ex) + gutter * 2, sy), (max(sx, ex) + gutter * 2, ey), in_d, end],
            [start, out_s, (min(sx, ex) - gutter * 2, sy), (min(sx, ex) - gutter * 2, ey), in_d, end],
            [start, out_s, (sx, max(sy, ey) + gutter * 2), (ex, max(sy, ey) + gutter * 2), in_d, end],
        ]
    )
    for path in candidates:
        path = simplify(path)
        if path_clear(path, obstacles):
            return path
    return simplify([start, out_s, (max(sx, ex) + gutter * 3, sy), (max(sx, ex) + gutter * 3, ey), in_d, end])


def route_points(start: tuple[int, int], end: tuple[int, int], obstacles: list[Rect]) -> list[tuple[int, int]]:
    sx, sy = start
    ex, ey = end
    gutter = GAP_PX
    cands = [
        [start, (sx, ey), end],
        [start, (ex, sy), end],
        [start, (sx, sy + gutter), (ex, sy + gutter), end],
        [start, (sx, sy - gutter), (ex, sy - gutter), end],
        [start, (sx + gutter, sy), (sx + gutter, ey), end],
        [start, (sx - gutter, sy), (sx - gutter, ey), end],
        [start, (min(sx, ex) - gutter * 2, sy), (min(sx, ex) - gutter * 2, ey), end],
        [start, (max(sx, ex) + gutter * 2, sy), (max(sx, ex) + gutter * 2, ey), end],
        [start, (sx, max(sy, ey) + gutter * 2), (ex, max(sy, ey) + gutter * 2), end],
    ]
    for c in cands:
        c = simplify(c)
        if path_clear(c, obstacles):
            return c
    return [start, end]


def reroute_polyline(points: list[tuple[int, int]], obstacles: list[Rect]) -> list[tuple[int, int]]:
    if len(points) < 2:
        return points
    out: list[tuple[int, int]] = [points[0]]
    for i in range(1, len(points)):
        a = out[-1]
        b = points[i]
        seg = simplify([a, b])
        if path_clear(seg, obstacles):
            out.append(b)
        else:
            mid = route_points(a, b, obstacles)
            out.extend(mid[1:])
    return simplify(out)


def draw_polyline(
    draw: ImageDraw.ImageDraw,
    points: list[tuple[int, int]],
    scale: int,
    color=(71, 85, 105),
    arrow_end: bool = True,
    dashed: bool = False,
) -> None:
    points = simplify(points)
    sw = max(1, LINE_W * scale)
    arrow = ARROW * scale

    def pt(p: tuple[int, int]) -> tuple[int, int]:
        return int(round(p[0] * scale)), int(round(p[1] * scale))

    scaled = [pt(p) for p in points]
    for i in range(len(scaled) - 1):
        x1, y1 = scaled[i]
        x2, y2 = scaled[i + 1]
        if dashed:
            dash, gap = 8 * scale, 6 * scale
            if x1 == x2:
                y, y_end = min(y1, y2), max(y1, y2)
                while y < y_end:
                    draw.line([(x1, y), (x1, min(y + dash, y_end))], fill=color, width=sw)
                    y += dash + gap
            else:
                x, x_end = min(x1, x2), max(x1, x2)
                while x < x_end:
                    draw.line([(x, y1), (min(x + dash, x_end), y1)], fill=color, width=sw)
                    x += dash + gap
        else:
            draw.line([(x1, y1), (x2, y2)], fill=color, width=sw)

    if not arrow_end or len(scaled) < 2:
        return
    x1, y1 = scaled[-2]
    x2, y2 = scaled[-1]
    ah = max(4, arrow // 2)
    if x2 > x1:
        draw.polygon([(x2, y2), (x2 - arrow, y2 - ah), (x2 - arrow, y2 + ah)], fill=color)
    elif x2 < x1:
        draw.polygon([(x2, y2), (x2 + arrow, y2 - ah), (x2 + arrow, y2 + ah)], fill=color)
    elif y2 > y1:
        draw.polygon([(x2, y2), (x2 - ah, y2 - arrow), (x2 + ah, y2 - arrow)], fill=color)
    else:
        draw.polygon([(x2, y2), (x2 - ah, y2 + arrow), (x2 + ah, y2 + arrow)], fill=color)


def gutter_between(a: Rect, b: Rect, axis: Literal["x", "y"]) -> int:
    if axis == "x":
        if a.x2 <= b.x1:
            return (a.x2 + b.x1) // 2
        if b.x2 <= a.x1:
            return (b.x2 + a.x1) // 2
        return (a.cx + b.cx) // 2
    if a.y2 <= b.y1:
        return (a.y2 + b.y1) // 2
    if b.y2 <= a.y1:
        return (b.y2 + a.y1) // 2
    return (a.cy + b.cy) // 2


def endpoint_port_y(ep: Rect) -> int:
    return ep.y1 + 14 + FONT_ENDPOINT // 2


def render_01_overview() -> None:
    g = GAP_PX
    d = Diagram(1580, 1000, "Схема 1. Крупные узлы системы")

    entry = Rect(40, 90, 420, 320)
    business = Rect(entry.x2 + g, 90, entry.x2 + g + 920, 420)
    config = Rect(business.x2 + g, 90, business.x2 + g + 280, 300)
    infra = Rect(entry.x2 + g, business.y2 + g, entry.x2 + g + 420, business.y2 + g + 250)
    stubs = Rect(infra.x2 + g, business.y2 + g, infra.x2 + g + 560, business.y2 + g + 250)

    d.group(entry, "Точка входа (клиент магазина)")
    app = d.box(
        Rect(entry.x1 + 15, entry.y1 + 42, entry.x2 - 15, entry.y1 + 110),
        "ReactorColdHotPublisherApplication",
        ["Запуск приложения"],
        title_size=16,
        line_step=22,
    )
    shop = d.box(
        Rect(entry.x1 + 15, entry.y1 + 110 + g, entry.x2 - 15, entry.y2 - 15),
        "controller.shop",
        ["Shop*Controller", "/api/shop/..."],
    )

    d.group(business, "Бизнес-логика магазина")
    inner_w = business.x2 - business.x1 - 30
    col3 = (inner_w - 2 * g) // 3
    row_h = 100
    y1 = business.y1 + 42
    y2 = y1 + row_h + g
    modules = [
        (Rect(business.x1 + 15, y1, business.x1 + 15 + col3, y1 + row_h), "Каталог", ["ShopProductController", "ProductCatalog"], (240, 255, 240), (34, 139, 34), (34, 139, 34)),
        (Rect(business.x1 + 15 + col3 + g, y1, business.x1 + 15 + 2 * col3 + g, y1 + row_h), "Anti-fraud", ["OrderFraudOrchestrator"], (255, 248, 230), (200, 100, 20), (200, 100, 20)),
        (Rect(business.x1 + 15 + 2 * (col3 + g), y1, business.x2 - 15, y1 + row_h), "Тарифы", ["TariffDirectory"], (230, 245, 255), (30, 90, 160), (30, 90, 160)),
        (Rect(business.x1 + 15 + col3 // 2, y2, business.x1 + 15 + col3 // 2 + col3, business.y2 - 15), "Трекинг", ["OrderStatusStream"], (245, 240, 255), (90, 60, 150), (90, 60, 150)),
        (Rect(business.x1 + 15 + col3 // 2 + col3 + g, y2, business.x1 + 15 + col3 // 2 + 2 * col3 + g, business.y2 - 15), "Котировки", ["MarketDataStream"], (255, 240, 245), (180, 60, 90), (180, 60, 90)),
    ]
    for rect, title, lines, fill, outline, tc in modules:
        d.box(rect, title, lines, fill=fill, outline=outline, tc=tc)

    d.group(config, "Настройки")
    prop = d.box(Rect(config.x1 + 15, config.y1 + 42, config.x2 - 15, config.y2 - 15), "DemoProperties", ["application.yml", "товары, паузы, порт"])

    d.group(infra, "Инфраструктура вызовов")
    reg = d.box(Rect(infra.x1 + 15, infra.y1 + 42, infra.x2 - 15, infra.y1 + 138), "ExternalApiClientRegistry", ["выбор канала по ApiClientKind"])
    wcc = d.box(Rect(infra.x1 + 15, infra.y1 + 138 + g, infra.x2 - 15, infra.y2 - 15), "WebClientConfig", ["correlationIdFilter"])

    d.group(stubs, "Учебная подмена сети (WebClient)")
    stub = d.box(
        Rect(stubs.x1 + 15, stubs.y1 + 42, stubs.x1 + 15 + 420, stubs.y2 - 15),
        "ExternalSystemStubExchange",
        ["infra.webclient.stub", "ExchangeFunction", "+ StubResponses"],
    )

    d.connect(app, "s", shop, "n")

    far_w = 12
    south_y = business.y2 + g + LINE_CLEARANCE
    top_y = business.y1 - g
    shop_out = outward_point(shop, "e", g)
    d.connect_points(
        [
            shop_out,
            (shop_out[0], top_y),
            (business.cx, top_y),
            inward_point(business, "n", g),
            business.port("n"),
        ],
        label="HTTP",
        strict=True,
    )

    d.connect_ports(
        business,
        "s",
        reg,
        "w",
        via=[(business.cx, south_y), (far_w, south_y), (far_w, reg.cy)],
    )

    stub_x = gutter_between(infra, stubs, "x")
    d.connect_ports(reg, "e", stub, "w", via=[(stub_x, reg.cy), (stub_x, stub.cy)])

    cfg_x = gutter_between(business, config, "x")
    top_y = min(entry.y1, business.y1, config.y1) - g
    d.connect_points(
        [(prop.x1, prop.cy), (cfg_x, prop.cy), (cfg_x, top_y), (business.cx, top_y), (business.cx, business.y1)],
        strict=True,
    )

    d.save("01-overview-nodes.png")


def render_02_config() -> None:
    g = GAP_PX
    d = Diagram(1320, 680, "Схема 2. Конфигурация и старт")

    y0 = 95
    row_h = 76
    yml = d.box(Rect(40, y0, 230, y0 + row_h), "application.yml", ["demo.*, server.port"])
    cfg = d.box(Rect(230 + g, y0, 230 + g + 220, y0 + row_h), "DemoPropertiesConfig", ["@EnableConfigurationProperties"])
    prop = d.box(Rect(230 + g + 220 + g, y0, 230 + g + 220 + g + 220, y0 + row_h), "DemoProperties", ["порт, данные, timing"])
    port = d.box(Rect(230 + g + 220 + g + 220 + g, y0, 230 + g + 220 + g + 220 + g + 200, y0 + row_h), "server.port", ["8082"])
    data = d.box(Rect(230 + g + 220 + g + 220 + g, y0 + row_h + g, 230 + g + 220 + g + 220 + g + 200, y0 + row_h + g + row_h), "stub-data / timing", ["товары, паузы"])

    split_x = port.x1 - g
    d.connect(yml, "e", cfg, "w")
    d.connect(cfg, "e", prop, "w")
    d.connect_points([(prop.x2, prop.cy), (split_x, prop.cy), (split_x, port.cy), (port.x1, port.cy)], strict=True)
    d.connect_points([(prop.x2, prop.cy), (split_x, prop.cy), (split_x, data.cy), (data.x1, data.cy)], strict=True)

    row_y = data.y2 + g
    boot_h = 96
    main = d.box(Rect(40, row_y, 340, row_y + boot_h), "ReactorColdHotPublisherApplication", ["@SpringBootApplication"], title_size=16, line_step=22)
    scan = d.box(Rect(40 + 340 + g, row_y, 40 + 340 + g + 250, row_y + boot_h), "Spring scan", ["com.example.coldhotpublisher.*"])
    beans = d.box(Rect(40 + 340 + g + 250 + g, row_y, 40 + 340 + g + 250 + g + 270, row_y + boot_h), "Бины", ["@Service @Component", "@RestController"], line_step=22)
    http = d.box(Rect(40 + 340 + g + 250 + g + 270 + g, row_y, 40 + 340 + g + 250 + g + 270 + g + 250, row_y + boot_h), "HTTP-клиент", ["/api/shop/..."])

    d.connect(main, "e", scan, "w")
    d.connect(scan, "e", beans, "w")
    d.connect(beans, "e", http, "w")

    bus_y = row_y - g - LINE_CLEARANCE
    d.connect_points([(prop.cx, prop.y2), (prop.cx, bus_y), (main.cx, bus_y), (main.cx, main.y1)], strict=True)
    d.connect_points([(data.cx, data.y2), (data.cx, bus_y), (http.cx, bus_y), (http.cx, http.y1)], strict=True)

    d.save("02-config-start.png")


def render_03_channels() -> None:
    g = GAP_PX
    d = Diagram(1400, 940, "Схема 3. Исходящие каналы к внешним системам")

    svc_x1, svc_w = 40, 240
    reg_x1 = svc_x1 + svc_w + g
    reg_w = 270
    ch_x1 = reg_x1 + reg_w + g
    ch_w = 240
    cfg_x1 = ch_x1 + ch_w + g

    services: list[Rect] = []
    y = 95
    box_h = 86
    for name, kind in zip(
        ["ProductCatalogClient", "WebClientFraudChecker", "TariffDirectoryClient", "OrderStatusStreamClient", "MarketDataClient"],
        ["CATALOG", "FRAUD", "TARIFF", "ORDER_STATUS", "MARKET"],
    ):
        r = d.box(Rect(svc_x1, y, svc_x1 + svc_w, y + box_h), name, [f"ApiClientKind.{kind}"])
        services.append(r)
        y += box_h + g

    svc_bottom = services[-1].y2
    reg_h = 130
    reg_y1 = (services[0].y1 + svc_bottom - reg_h) // 2
    reg = Rect(reg_x1, reg_y1, reg_x1 + reg_w, reg_y1 + reg_h)
    reg_box = d.box(reg, "ExternalApiClientRegistry", ["get(kind)", "webClient(kind)"])

    channels: list[Rect] = []
    y = 95
    for name in ["CatalogExternalApiClient", "FraudExternalApiClient", "TariffExternalApiClient", "OrderStatusExternalApiClient", "MarketExternalApiClient"]:
        r = d.box(Rect(ch_x1, y, ch_x1 + ch_w, y + box_h), name, ["ExternalApiClient"], fill=(240, 255, 240), outline=(34, 139, 34), tc=(34, 139, 34))
        channels.append(r)
        y += box_h + g

    factory = d.box(Rect(cfg_x1, 100, cfg_x1 + 300, 175), "ExternalApiClientFactory", ["jsonClient /", "eventStreamClient", "+ StubExchange"])
    wcc = d.box(Rect(cfg_x1, 175 + g, cfg_x1 + 300, 250), "WebClientConfig", ["correlationIdFilter"])
    conf = d.box(Rect(cfg_x1, 500, cfg_x1 + 340, 600), "ExternalApiClientConfiguration", ["List<ExternalApiClient> → Map", "→ Registry"])

    west_bus = svc_x1 - g
    east_bus = gutter_between(reg, Rect(ch_x1, 95, ch_x1 + ch_w, 95 + box_h), "x")
    south_bus = svc_bottom + g
    east_south = max(channels[-1].y2, reg.y2) + g

    for s in services:
        d.connect_points([(s.x2, s.cy), (west_bus, s.cy)], strict=True)
        d.bus_v(west_bus, s.cy, south_bus, arrow_end=False)
    d.connect_points([(west_bus, south_bus), (reg.cx, south_bus), (reg.cx, reg.y2)], strict=True)

    d.connect_points([(reg.x2, reg.y2), (reg.x2, east_south), (east_bus, east_south)], arrow_end=False, strict=True)
    for c in channels:
        d.bus_v(east_bus, east_south, c.cy, arrow_end=False)
        d.connect_points([(east_bus, c.cy), (c.x1, c.cy)], strict=True)

    bottom_bus = max(south_bus, east_south, conf.y2) + g
    d.connect_points([(conf.x1, conf.y2), (conf.x1, bottom_bus), (reg.cx, bottom_bus), (reg.cx, reg.y2)], strict=True)

    d.connect(wcc, "n", factory, "s")
    cfg_bus = cfg_x1 + 340 + g
    d.connect_points(
        [(factory.x2, factory.cy), (cfg_bus, factory.cy), (cfg_bus, conf.cy), (conf.x2, conf.cy)],
        strict=True,
    )

    d.save("03-outbound-channels.png")


def render_04_stubs() -> None:
    g = GAP_PX
    ep_h = 96
    d = Diagram(1360, 820, "Схема 4. Учебная подмена внешних систем в WebClient")

    routes = [
        ("GET /products/{id}", "→ ProductDto"),
        ("POST /fraud/check/{orderId}", "→ FraudDecision"),
        ("GET /tariffs", "→ TariffTable"),
        ("GET /orders/{id}/statuses/stream", "→ OrderStatusEvent* (SSE)"),
        ("GET /quotes/{symbol}/stream", "→ QuoteEvent* (SSE)"),
    ]

    y0 = 90
    exchange = d.box(Rect(40, y0, 400, y0 + 120), "ExternalSystemStubExchange", ["ExchangeFunction", "маршрутизация по URI"])
    responses = d.box(Rect(40, y0 + 120 + g, 400, y0 + 120 + g + 100), "ExternalSystemStubResponses", ["stub-data", "stub-timing"])

    bus_x = 460
    ep_x1 = bus_x + g + 10
    last_y = y0 + (len(routes) - 1) * (ep_h + g)

    for i, (title, line) in enumerate(routes):
        y = y0 + i * (ep_h + g)
        ep = d.endpoint_box(Rect(ep_x1, y, 1320, y + ep_h), title, line)
        py = endpoint_port_y(ep)
        d.connect_points([(exchange.x2, exchange.cy), (bus_x, exchange.cy), (bus_x, py), (ep.x1 - 4, py)], strict=True)

    d.connect(exchange, "s", responses, "n")
    d.text((40, last_y + ep_h + 24), "Нет публичного HTTP-контроллера: ответ собирается внутри WebClient", color=(71, 85, 105))
    d.save("04-stub-controller.png")


def _render_sequence(
    d: Diagram,
    names: list[str],
    classes: list[str],
    steps: list[tuple],
    footer: list[tuple[str, tuple[int, int, int] | None]] | None = None,
    lane_w: int = 220,
) -> None:
    g = GAP_PX
    top = 70
    box_h = 76
    west = 20
    rects: list[Rect] = []
    for i, (name, cls) in enumerate(zip(names, classes)):
        x1 = 50 + i * (lane_w + g)
        rects.append(d.box(Rect(x1, top, x1 + lane_w, top + box_h), name, [cls], tc=(0, 112, 192)))

    row_bottom = top + box_h
    bus_y = row_bottom + g

    for step in steps:
        kind = step[0]
        if kind == "call":
            _, a, b, label = step
            d.seq_call(rects[a], rects[b], bus_y, label)
            bus_y += 56 + g
        elif kind == "skip":
            _, a, b, label = step
            d.seq_skip(rects[a], rects[b], bus_y, west, label)
            bus_y += 56 + g
        elif kind == "return":
            _, a, b, label = step
            d.seq_return_west(rects[a], rects[b], bus_y, west, label)
            bus_y += 48 + g
        elif kind == "note":
            _, text, color = step
            d.text((west + 36, bus_y - 18), text, size=FONT_NOTE, color=color or (30, 41, 59))
            bus_y += 36 + g

    if footer:
        bus_y += 10
        for text, color in footer:
            d.text((50, bus_y), text, size=FONT_NOTE, color=color or (30, 41, 59))
            bus_y += 28


def render_05_catalog() -> None:
    d = Diagram(1320, 820, "Схема 5. Каталог товаров — два виджета на странице")
    _render_sequence(
        d,
        ["HTTP-клиент", "ShopProductController", "ProductCatalog", "ProductCatalogClient", "ExternalSystemStubExchange"],
        ["curl / shop-demo.http", "controller.shop", "interface", "service.catalog", "infra.webclient.stub"],
        [
            ("call", 0, 1, "GET /products/{id} #1"),
            ("call", 1, 2, "getProduct()"),
            ("call", 2, 3, None),
            ("call", 3, 4, "GET /products/{id}"),
            ("return", 4, 3, "ProductDto"),
            ("note", "второй GET — повторный поход в каталог", (180, 60, 60)),
            ("skip", 0, 1, "GET /products/{id} #2"),
            ("call", 1, 2, "getProduct()"),
            ("call", 2, 3, None),
            ("call", 3, 4, "GET /products/{id}"),
        ],
        footer=[("Ожидание в логах: две строки catalog -> GET /products/...", (34, 139, 34))],
        lane_w=210,
    )
    d.save("05-catalog-two-widgets.png")


def render_06_fraud() -> None:
    g = GAP_PX
    d = Diagram(1240, 620, "Схема 6. Anti-fraud при приёме заказа")

    orch = d.box(Rect(420, 70, 800, 140), "OrderFraudOrchestrator", ["processOrder(orderId)"])
    checker = d.box(Rect(40, 70 + 140 + g, 320, 70 + 140 + g + 80), "FraudChecker", ["check()"])
    impl = d.box(Rect(40, 70 + 140 + g + 80 + g, 320, 70 + 140 + g + 80 + g + 80), "WebClientFraudChecker", ["POST /fraud/check"])
    hub = d.box(Rect(420, 70 + 140 + g, 800, 70 + 140 + g + 100), "Один результат проверки", ["на всех потребителей"])
    audit = d.box(Rect(840 + g, 70 + 140 + g, 840 + g + 300, 70 + 140 + g + 80), "FraudAuditService", ["LoggingFraudAuditService"])
    metrics = d.box(Rect(840 + g, 70 + 140 + g + 80 + g, 840 + g + 300, 70 + 140 + g + 80 + g + 80), "FraudMetricsService", ["LoggingFraudMetricsService"])
    mapper = d.box(Rect(840 + g, 70 + 140 + g + 80 + g + 80 + g, 840 + g + 300, 70 + 140 + g + 80 + g + 80 + g + 80), "FraudResponseMapper", ["DefaultFraudResponseMapper"])

    d.connect(orch, "s", hub, "n")
    left_g = checker.x2 + g
    d.connect_points([(hub.x1, hub.cy), (left_g, hub.cy), (left_g, checker.cy), (checker.x2, checker.cy)], strict=True)
    d.connect(checker, "s", impl, "n")
    right_g = hub.x2 + g
    for target in (audit, metrics, mapper):
        d.connect_points([(hub.x2, hub.cy), (right_g, hub.cy), (right_g, target.cy), (target.x1, target.cy)], strict=True)

    d.text((40, 520), "Бизнес-смысл: службу anti-fraud вызываем один раз; аудит, метрики и ответ клиенту — из одного вердикта")
    d.text((40, 552), "Ожидание в логах: одна строка fraud -> POST /fraud/check", color=(34, 139, 34))
    d.save("06-antifraud-order.png")


def render_07_tariffs() -> None:
    d = Diagram(1180, 680, "Схема 7. Справочник тарифов доставки")
    _render_sequence(
        d,
        ["HTTP-клиент", "ShopTariffController", "TariffDirectory", "TariffDirectoryClient", "ExternalSystemStubExchange"],
        ["curl / shop-demo.http", "controller.shop", "interface", "service.tariff", "infra.webclient.stub"],
        [
            ("call", 0, 1, "GET /tariffs #1"),
            ("call", 1, 2, "getTariffs()"),
            ("call", 2, 3, None),
            ("call", 3, 4, "GET /tariffs (1 раз)"),
            ("return", 4, 3, "TariffTable"),
            ("note", "пауза", (100, 116, 139)),
            ("skip", 0, 1, "GET /tariffs #2"),
            ("call", 1, 2, "getTariffs()"),
            ("note", "из кэша, без GET", (100, 116, 139)),
        ],
        footer=[("TTL кэша: DemoProperties.Cache.tariffTtlMinutes", (71, 85, 105))],
        lane_w=240,
    )
    d.save("07-tariff-directory.png")


def render_08_tracking() -> None:
    g = GAP_PX
    d = Diagram(1300, 760, "Схема 8. Трекинг статуса заказа")

    client = d.box(Rect(500, 85, 820, 160), "ShopOrderController", ["SSE /statuses/stream"])

    shared_g = Rect(40, 195, 620, 470)
    replay_g = Rect(620 + g, 195, 1260, 470)
    d.group(shared_g, "liveStatusesShared — только новые этапы")
    stream_h = 78
    d.box(
        Rect(shared_g.x1 + 15, shared_g.y1 + 44, shared_g.x2 - 15, shared_g.y1 + 44 + stream_h),
        "OrderStatusStream",
        ["→ OrderStatusStreamClient"],
    )
    sub_y = shared_g.y1 + 44 + stream_h + g
    d.box(Rect(shared_g.x1 + 15, sub_y, shared_g.x1 + 240, shared_g.y2 - 18), "audit", ["с начала потока"])
    d.box(Rect(shared_g.x1 + 240 + g, sub_y, shared_g.x2 - 15, shared_g.y2 - 18), "ui-late", ["опоздавший — без прошлого"])

    d.group(replay_g, "liveStatusesReplayLast — последний + дальше")
    d.box(
        Rect(replay_g.x1 + 15, replay_g.y1 + 44, replay_g.x2 - 15, replay_g.y1 + 44 + stream_h),
        "OrderStatusStream",
        ["→ OrderStatusStreamClient"],
    )
    d.box(Rect(replay_g.x1 + 15, sub_y, replay_g.x2 - 15, replay_g.y2 - 18), "ui-late", ["сразу последний статус"])

    stub_y1 = shared_g.y2 + g + 24
    stub_h = 130
    stub = d.box(
        Rect(400, stub_y1, 900, stub_y1 + stub_h),
        "ExternalSystemStubExchange",
        ["SSE:", "CREATED → PAID →", "PACKED → SHIPPED"],
        line_step=26,
    )

    top_bus = gutter_between(client, shared_g, "y")
    d.connect_points([(client.cx, client.y2), (client.cx, top_bus), (shared_g.cx, top_bus), (shared_g.cx, shared_g.y1)], strict=True)
    d.connect_points([(client.cx, client.y2), (client.cx, top_bus), (replay_g.cx, top_bus), (replay_g.cx, replay_g.y1)], strict=True)

    stub_bus = gutter_between(shared_g, Rect(400, stub_y1, 900, stub_y1 + stub_h), "y")
    d.connect_points([(shared_g.cx, shared_g.y2), (shared_g.cx, stub_bus), (stub.cx, stub_bus), (stub.cx, stub.y1)], strict=True)
    d.connect_points([(replay_g.cx, replay_g.y2), (replay_g.cx, stub_bus), (stub.cx, stub_bus), (stub.cx, stub.y1)], strict=True)

    d.save("08-order-tracking.png")


def render_09_quotes() -> None:
    d = Diagram(1180, 700, "Схема 9. Поток котировок")
    _render_sequence(
        d,
        ["HTTP-клиент", "ShopMarketController", "MarketDataStream", "MarketDataClient", "ExternalSystemStubExchange"],
        ["curl / shop-demo.http", "controller.shop", "interface", "service.market", "infra.webclient.stub"],
        [
            ("call", 0, 1, "GET /quotes/.../stream #1"),
            ("call", 1, 2, "streamQuotes()"),
            ("note", "подписчик ui — соединение ещё не открыто", (180, 60, 60)),
            ("skip", 0, 1, "GET /quotes/.../stream #2"),
            ("call", 1, 2, "streamQuotes()"),
            ("call", 2, 3, "OPEN SSE /quotes/..."),
            ("return", 3, 2, "QuoteEvent..."),
        ],
        footer=[("Платный поток открывается, когда котировки нужны и витрине, и аудиту", None)],
        lane_w=240,
    )
    d.save("09-market-quotes.png")


def render_10_runner() -> None:
    g = GAP_PX
    d = Diagram(1480, 380, "Схема 10. Учебные сценарии (HTTP-клиент)")

    steps_data = [
        ("bootRun", "приложение"),
        ("GET products×2", "cold Mono"),
        ("POST process", "shared Mono"),
        ("GET tariffs×2", "cache"),
        ("GET stream shared", "Flux.share"),
        ("GET stream replay", "replay(1)"),
        ("GET quotes×2", "refCount(2)"),
    ]
    x, w = 40, 175
    rects: list[Rect] = []
    for title, line in steps_data:
        r = d.box(Rect(x, 90, x + w, 175), title, [line])
        rects.append(r)
        x += w + g

    end = d.box(Rect(rects[-1].x1, 90 + 175 + g, rects[-1].x2, 90 + 175 + g + 80), "Логи", ["catalog / fraud / …"])
    for i in range(1, len(rects)):
        d.connect(rects[i - 1], "e", rects[i], "w")
    d.connect(rects[-1], "s", end, "n")
    d.save("10-demo-runner.png")


def main() -> None:
    render_01_overview()
    render_02_config()
    render_03_channels()
    render_04_stubs()
    render_05_catalog()
    render_06_fraud()
    render_07_tariffs()
    render_08_tracking()
    render_09_quotes()
    render_10_runner()
    print(f"Saved 10 PNG to {OUT_DIR} (scale={RENDER_SCALE}x, dpi={OUTPUT_DPI}, gap={GAP_PX}px)")


if __name__ == "__main__":
    main()
