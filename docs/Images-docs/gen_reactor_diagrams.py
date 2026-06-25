# -*- coding: utf-8 -*-
"""PNG-диаграммы для project-reactor-interview-guide.md (без mermaid в markdown)."""
import textwrap
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent
W, H = 1400, 900
BG = "#ffffff"
FONT_PATHS = [r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf"]


def load_fonts():
    for p in FONT_PATHS:
        if Path(p).exists():
            return (
                ImageFont.truetype(p, 26),
                ImageFont.truetype(p, 18),
                ImageFont.truetype(p, 15),
                ImageFont.truetype(p, 13),
            )
    d = ImageFont.load_default()
    return d, d, d, d


TITLE_F, SUB_F, MSG_F, NOTE_F = load_fonts()


def canvas(title, subtitle):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    d.text((W // 2, 28), title, fill="#0f172a", font=TITLE_F, anchor="mm")
    d.text((W // 2, 58), subtitle, fill="#64748b", font=SUB_F, anchor="mm")
    d.line([(40, 78), (W - 40, 78)], fill="#e2e8f0", width=2)
    return img, d


def lifeline_x(count, index, left=100, right=W - 100):
    if count == 1:
        return (left + right) // 2
    return left + index * (right - left) // (count - 1)


def draw_participant(d, x, name, sub=None, color="#2563eb"):
    box_w, box_h = 160, 56 if sub else 44
    x1, y1 = x - box_w // 2, 88
    x2, y2 = x1 + box_w, y1 + box_h
    d.rounded_rectangle((x1, y1, x2, y2), radius=8, fill="#eff6ff", outline=color, width=2)
    if sub:
        d.text((x, y1 + 18), name, fill="#0f172a", font=MSG_F, anchor="mm")
        d.text((x, y1 + 38), sub, fill="#64748b", font=NOTE_F, anchor="mm")
    else:
        d.text((x, (y1 + y2) // 2), name, fill="#0f172a", font=MSG_F, anchor="mm")
    d.line([(x, y2), (x, H - 130)], fill="#cbd5e1", width=2)
    return y2


def arrow_h(d, x1, x2, y, label, color="#2563eb", dashed=False, numbered=None):
    if numbered:
        d.ellipse((42, y - 14, 68, y + 14), fill="#f1f5f9", outline="#94a3b8")
        d.text((55, y), str(numbered), fill="#334155", font=NOTE_F, anchor="mm")
    start, end = (x1, y), (x2, y)
    if dashed:
        steps = max(abs(end[0] - start[0]) // 12, 1)
        for i in range(steps):
            t0, t1 = i / steps, (i + 0.55) / steps
            p0 = (int(start[0] + (end[0] - start[0]) * t0), y)
            p1 = (int(start[0] + (end[0] - start[0]) * t1), y)
            d.line([p0, p1], fill=color, width=2)
    else:
        d.line([start, end], fill=color, width=3)
    tip_x = end[0] - 10 if end[0] > start[0] else end[0] + 10
    d.polygon([(tip_x, y - 6), (end[0], y), (tip_x, y + 6)], fill=color)
    lx = (x1 + x2) // 2
    d.text((lx, y - 18), label, fill=color, font=NOTE_F, anchor="mm")


def self_call(d, x, y, label, color="#d97706", numbered=None):
    if numbered:
        d.ellipse((42, y - 14, 68, y + 14), fill="#f1f5f9", outline="#94a3b8")
        d.text((55, y), str(numbered), fill="#334155", font=NOTE_F, anchor="mm")
    d.line([(x, y), (x + 50, y), (x + 50, y + 36), (x, y + 36)], fill=color, width=2)
    d.polygon([(x - 6, y + 30), (x, y + 36), (x + 6, y + 30)], fill=color)
    d.text((x + 78, y + 10), label, fill=color, font=NOTE_F, anchor="lm")


def footer_note(d, title, lines, color="#0f172a", fill="#f8fafc", border="#cbd5e1"):
    """Блок «Пояснение» внизу диаграммы."""
    y1, y2 = H - 118, H - 18
    d.rounded_rectangle((60, y1, W - 60, y2), radius=8, fill=fill, outline=border, width=2)
    d.text((80, y1 + 14), "Пояснение:", fill=color, font=MSG_F, anchor="lm")
    for i, line in enumerate(lines):
        d.text((80, y1 + 38 + i * 22), line, fill="#475569", font=NOTE_F, anchor="lm")


def save(img, name):
    p = OUT / name
    img.save(p, "PNG")
    print("saved", p)


def seq_map_email():
    """Mono.findById + map — синхронное преобразование."""
    img, d = canvas(
        "Sequence: map после findById",
        "User уже загружен · map меняет значение в памяти · второй запрос в БД не нужен",
    )
    names = ["Spring\n(WebFlux)", "userRepository\n.findById", "map\n(email)", "map\n(toUpperCase)", "HTTP\nответ"]
    xs = [lifeline_x(5, i) for i in range(5)]
    for i, n in enumerate(names):
        draw_participant(d, xs[i], n.replace("\n", " "), None if i == 4 else None)

    y = 180
    arrow_h(d, xs[0], xs[1], y, "subscribe на Mono", numbered=1)
    y += 65
    arrow_h(d, xs[1], xs[1], y, "", color="#64748b")  # placeholder
    self_call(d, xs[1], y, "SQL: SELECT user", color="#475569", numbered=2)
    y += 75
    arrow_h(d, xs[1], xs[0], y, "onNext(User)", dashed=True, color="#059669", numbered=3)
    y += 65
    arrow_h(d, xs[0], xs[2], y, "User → email", numbered=4)
    y += 65
    self_call(d, xs[2], y, "ann@mail.com", numbered=5)
    y += 75
    arrow_h(d, xs[2], xs[3], y, "строка", numbered=6)
    y += 65
    self_call(d, xs[3], y, "ANN@MAIL.COM", numbered=7)
    y += 75
    arrow_h(d, xs[3], xs[4], y, "Mono<String> → JSON", numbered=8)
    footer_note(d, "Пояснение", [
        "Читайте сверху вниз по номерам 1→8. User загружается один раз (шаг 2).",
        "map только меняет поля в памяти (4–7). flatMap не нужен — нет Mono/Flux внутри map.",
        "Итог: Mono<String> в JSON.",
    ], fill="#ecfdf5", color="#047857")
    save(img, "reactor-seq-map-email.png")


def seq_map_wrong():
    img, d = canvas(
        "Sequence: map + findById — ошибка",
        "map получает Mono<User>, но кладёт его в поток как объект · на Mono не подписывается",
    )
    names = ["Flux\n(ids)", "map\n(findById)", "Mono<User>\n(коробка)", "R2DBC", "Подписчик"]
    xs = [lifeline_x(5, i) for i in range(5)]
    for i, n in enumerate(names):
        draw_participant(d, xs[i], n.split("\n")[0], n.split("\n")[1] if "\n" in n else None,
                        color="#dc2626" if i == 2 else "#2563eb")

    y = 180
    arrow_h(d, xs[4], xs[0], y, "subscribe", numbered=1)
    y += 65
    arrow_h(d, xs[0], xs[1], y, "onNext(id=1)", numbered=2)
    y += 65
    self_call(d, xs[1], y, "findById(1) → new Mono", color="#dc2626", numbered=3)
    y += 75
    arrow_h(d, xs[1], xs[2], y, "кладёт Mono в поток", color="#dc2626", numbered=4)
    y += 65
    d.line([(xs[3], 160), (xs[3], y + 20)], fill="#fca5a5", width=3)
    d.text((xs[3], y + 35), "нет запроса", fill="#b91c1c", font=NOTE_F, anchor="mm")
    d.text((xs[3], y + 52), "X", fill="#b91c1c", font=TITLE_F, anchor="mm")
    y += 85
    arrow_h(d, xs[2], xs[4], y, "onNext(Mono<User>)  ≠ User", color="#dc2626", numbered=5)
    footer_note(d, "Пояснение", [
        "Шаг 3: findById создаёт Mono, но map НЕ подписывается на него.",
        "Шаг 4: в поток попадает объект Mono — не User. Столбец R2DBC: SQL не было.",
        "Итог: Flux<Mono<User>> — данные пользователя не приходят.",
    ], fill="#fef2f2", color="#991b1b", border="#fca5a5")
    save(img, "reactor-seq-map-wrong-db.png")


def seq_flatmap_ok():
    img, d = canvas(
        "Sequence: flatMap + findById — правильно",
        "flatMap подписывается на Mono от репозитория и отдаёт наружу User",
    )
    names = ["Flux\n(ids)", "flatMap", "userRepository\n.findById", "R2DBC", "Подписчик"]
    xs = [lifeline_x(5, i) for i in range(5)]
    for i, n in enumerate(names):
        draw_participant(d, xs[i], n.split("\n")[0], n.split("\n")[1] if "\n" in n else None)

    y = 180
    arrow_h(d, xs[4], xs[0], y, "subscribe", numbered=1)
    y += 65
    arrow_h(d, xs[0], xs[1], y, "onNext(id=1)", numbered=2)
    y += 65
    arrow_h(d, xs[1], xs[2], y, "subscribe на findById(1)", color="#d97706", numbered=3)
    y += 65
    arrow_h(d, xs[2], xs[3], y, "SQL SELECT", numbered=4)
    y += 65
    arrow_h(d, xs[3], xs[2], y, "строка User", dashed=True, color="#059669", numbered=5)
    y += 65
    arrow_h(d, xs[2], xs[1], y, "onNext(User)", dashed=True, color="#059669", numbered=6)
    y += 65
    arrow_h(d, xs[1], xs[4], y, "onNext(User)", color="#059669", numbered=7)
    footer_note(d, "Пояснение", [
        "Шаг 3 (оранжевый): flatMap ПОДПИСЫВАЕТСЯ на findById — в отличие от map.",
        "Шаги 4–5: реальный SQL в R2DBC. Шаг 7: подписчик получает User, не Mono.",
        "Итог: Flux<User> — flatMap развернул вложенный Mono.",
    ], fill="#ecfdf5", color="#047857")
    save(img, "reactor-seq-flatmap-db.png")


def seq_get_user_summary():
    img, d = canvas(
        "Sequence: getUserSummary — flatMap + map",
        "flatMap там, где возвращается Flux/Mono · map там, где собирается DTO",
    )
    names = ["Controller", "findById", "flatMap", "findByUserId", "collectList+map", "Ответ"]
    xs = [lifeline_x(6, i) for i in range(6)]
    for i, n in enumerate(names):
        draw_participant(d, xs[i], n)

    y = 175
    arrow_h(d, xs[0], xs[1], y, "findById(id)", numbered=1)
    y += 60
    arrow_h(d, xs[1], xs[0], y, "Mono<User>", dashed=True, color="#059669", numbered=2)
    y += 60
    arrow_h(d, xs[0], xs[2], y, "flatMap(user → …)", color="#d97706", numbered=3)
    y += 60
    arrow_h(d, xs[2], xs[3], y, "findByUserId(id)", numbered=4)
    y += 60
    arrow_h(d, xs[3], xs[2], y, "Flux<Order>", dashed=True, color="#059669", numbered=5)
    y += 60
    arrow_h(d, xs[2], xs[4], y, "collectList()", numbered=6)
    y += 60
    self_call(d, xs[4], y, "map → UserSummaryResponse", numbered=7)
    y += 75
    arrow_h(d, xs[4], xs[0], y, "Mono<UserSummaryResponse>", dashed=True, color="#059669", numbered=8)
    y += 60
    arrow_h(d, xs[0], xs[5], y, "JSON", numbered=9)
    footer_note(d, "Пояснение", [
        "flatMap (шаг 3): нужен, потому что findByUserId возвращает Flux.",
        "map (шаг 7): DTO из готового List — обычный объект, не Publisher.",
        "Ошибка: .map(u→findByUserId) дало бы Mono<Flux> без шагов 5–7.",
    ])
    save(img, "reactor-seq-get-user-summary.png")


def seq_concatmap():
    img, d = canvas(
        "Sequence: flatMap vs concatMap (упрощённо)",
        "flatMap — запросы параллельно · concatMap — следующий id только после ответа",
    )
    # left flatMap
    d.text((350, 100), "flatMap", fill="#1d4ed8", font=TITLE_F, anchor="mm")
    lx = [180, 350, 520]
    for i, n in enumerate(["Flux", "flatMap", "БД"]):
        draw_participant(d, lx[i], n)
    y = 200
    for step, (lbl, ret) in enumerate([("id=1", "User(3)"), ("id=2", "User(1)"), ("id=3", "User(2)")], 1):
        arrow_h(d, lx[0], lx[1], y, lbl, numbered=step if step == 1 else None)
        y += 45
        arrow_h(d, lx[1], lx[2], y, "findById", color="#64748b")
        y += 45
        arrow_h(d, lx[2], lx[1], y, ret, dashed=True, color="#059669")
        y += 55
    d.text((350, y + 10), "Порядок на выходе: 3, 1, 2", fill="#1e40af", font=MSG_F, anchor="mm")

    # right concatMap
    d.line([(700, 90), (700, H - 90)], fill="#e2e8f0", width=2)
    d.text((1050, 100), "concatMap", fill="#047857", font=TITLE_F, anchor="mm")
    rx = [880, 1050, 1220]
    for i, n in enumerate(["Flux", "concatMap", "БД"]):
        draw_participant(d, rx[i], n, color="#059669")
    y = 200
    for lbl, ret in [("id=1", "User(1)"), ("id=2", "User(2)"), ("id=3", "User(3)")]:
        arrow_h(d, rx[0], rx[1], y, lbl)
        y += 45
        arrow_h(d, rx[1], rx[2], y, "findById", color="#64748b")
        y += 45
        arrow_h(d, rx[2], rx[1], y, ret, dashed=True, color="#059669")
        y += 45
        if lbl != "id=3":
            d.text((rx[1] + 62, y), "ждёт", fill="#64748b", font=NOTE_F)
            y += 35
    d.text((1050, y + 10), "Порядок: 1, 2, 3", fill="#047857", font=MSG_F, anchor="mm")
    footer_note(d, "Пояснение", [
        "Слева flatMap: все id уходят в БД быстро → ответы в порядке готовности (3,1,2).",
        "Справа concatMap: следующий id только после ответа по предыдущему (1,2,3).",
        "Выбор: flatMap — скорость; concatMap — строгий порядок.",
    ])
    save(img, "reactor-seq-flatmap-vs-concatmap.png")


def seq_spring_boot_startup():
    """§2 — цепочка старта Spring Boot до ApplicationReadyEvent."""
    img, d = canvas(
        "Sequence: старт Spring Boot → ApplicationReadyEvent",
        "main → контекст → runners → событие → @EventListener.onAppReady",
    )
    names = ["main", "SpringApplication", "ApplicationContext", "Runners", "AppStartupListener"]
    xs = [lifeline_x(5, i, left=90, right=W - 90) for i in range(5)]
    for i, n in enumerate(names):
        sub = "@EventListener" if i == 4 else None
        color = "#b45309" if i == 4 else "#2563eb"
        draw_participant(d, xs[i], n, sub=sub, color=color)

    y = 175
    arrow_h(d, xs[0], xs[1], y, "run()", numbered=1)
    y += 58
    arrow_h(d, xs[1], xs[2], y, "refresh() · бины подняты", numbered=2)
    y += 58
    self_call(d, xs[2], y, "publish(ApplicationStartedEvent)", color="#d97706", numbered=3)
    y += 72
    arrow_h(d, xs[2], xs[3], y, "CommandLineRunner / ApplicationRunner", numbered=4)
    y += 58
    self_call(d, xs[2], y, "publish(ApplicationReadyEvent)", color="#d97706", numbered=5)
    y += 72
    arrow_h(d, xs[2], xs[4], y, "onAppReady(event)", color="#059669", numbered=6)
    y += 52
    d.text((W // 2, y), "→ приложение готово принимать HTTP-запросы", fill="#475569", font=SUB_F, anchor="mm")
    footer_note(d, "Пояснение", [
        "Шаг 5 — ApplicationReadyEvent: после runners, приложение готово к запросам (Spring Boot Reference).",
        "Шаг 6 — контекст вызывает метод bean'а с @EventListener (не вы вызываете onAppReady сами).",
        "Слушатель — bean AppStartupListener; onAppReady — обработчик события.",
    ], fill="#fffbeb", border="#fcd34d")
    save(img, "reactor-seq-spring-boot-startup.png")


# --- Концептуальные рисунки (разделы 0–18) ---

CW, CH = 1200, 720


def concept_canvas(title, subtitle, height=CH):
    img = Image.new("RGB", (CW, height), BG)
    d = ImageDraw.Draw(img)
    d.text((CW // 2, 26), title, fill="#0f172a", font=TITLE_F, anchor="mm")
    d.text((CW // 2, 54), subtitle, fill="#64748b", font=SUB_F, anchor="mm")
    d.line([(36, 72), (CW - 36, 72)], fill="#e2e8f0", width=2)
    return img, d, height


def box(d, cx, cy, text, w=170, h=52, fill="#eff6ff", outline="#2563eb", lines=None):
    x1, y1 = cx - w // 2, cy - h // 2
    x2, y2 = cx + w // 2, cy + h // 2
    d.rounded_rectangle((x1, y1, x2, y2), radius=8, fill=fill, outline=outline, width=2)
    if lines:
        for i, line in enumerate(lines):
            d.text((cx, cy - 8 + i * 18), line, fill="#0f172a", font=NOTE_F, anchor="mm")
    else:
        d.text((cx, cy), text, fill="#0f172a", font=MSG_F, anchor="mm")


def arr(d, x1, y1, x2, y2, color="#2563eb", label=None):
    d.line([(x1, y1), (x2, y2)], fill=color, width=3)
    if x2 > x1:
        d.polygon([(x2 - 10, y2 - 5), (x2, y2), (x2 - 10, y2 + 5)], fill=color)
    elif x2 < x1:
        d.polygon([(x2 + 10, y2 - 5), (x2, y2), (x2 + 10, y2 + 5)], fill=color)
    elif y2 > y1:
        d.polygon([(x2 - 5, y2 - 10), (x2, y2), (x2 + 5, y2 - 10)], fill=color)
    if label:
        d.text(((x1 + x2) // 2, (y1 + y2) // 2 - 14), label, fill=color, font=NOTE_F, anchor="mm")


def arr_down(d, x, y_from, y_to, label=None, color="#2563eb", label_dx=100):
    """Вертикальная стрелка вниз: от нижней грани верхнего блока к верхней грани нижнего."""
    tip = y_to - 6
    d.line([(x, y_from), (x, tip)], fill=color, width=3)
    d.polygon([(x - 5, tip - 8), (x, tip), (x + 5, tip - 8)], fill=color)
    if label:
        mid = (y_from + tip) // 2
        d.text((x + label_dx, mid), label, fill=color, font=NOTE_F, anchor="lm")


def concept_footer(d, lines, height=CH, fill="#f8fafc", color="#0f172a", wrap_chars=74):
    wrapped = []
    for line in lines:
        parts = textwrap.wrap(line, width=wrap_chars)
        wrapped.extend(parts if parts else [""])
    line_h = 18
    box_h = 28 + len(wrapped) * line_h + 12
    y2 = height - 16
    y1 = y2 - box_h
    d.rounded_rectangle((48, y1, CW - 48, y2), radius=8, fill=fill, outline="#cbd5e1", width=2)
    d.text((64, y1 + 10), "Пояснение:", fill=color, font=MSG_F, anchor="lm")
    for i, line in enumerate(wrapped):
        d.text((64, y1 + 30 + i * line_h), line, fill="#475569", font=NOTE_F, anchor="lm")


def concept_intro():
    img, d, h = concept_canvas("Reactor: цепочка в WebFlux", "Аналогия: конвейер от БД до JSON")
    y = 200
    xs = [120, 320, 520, 720, 920, 1080]
    labels = ["PostgreSQL", "R2DBC", "Service\nmap·flatMap", "Controller\nreturn Mono", "Netty", "JSON\nклиенту"]
    for x, lb in zip(xs, labels):
        box(d, x, y, lb, w=150, h=56 if "\n" in lb else 48)
    for i in range(len(xs) - 1):
        arr(d, xs[i] + 78, y, xs[i + 1] - 78, y)
    concept_footer(d, ["Код описывает станки на ленте; Spring подписывается сам — subscribe() в контроллере не нужен."], h)
    save(img, "reactor-concept-intro.png")


def concept_01():
    img, d, h = concept_canvas("§1 Project Reactor", "Конвейер операторов — lazy до subscribe")
    y = 220
    for x, t in [(200, "Источник"), (450, "map"), (700, "flatMap"), (950, "Подписчик")]:
        box(d, x, y, t)
    arr(d, 285, y, 365, y)
    arr(d, 535, y, 615, y)
    arr(d, 785, y, 865, y)
    d.text((600, 320), "Цепочка сама ничего не делает, пока её не «включат»", fill="#64748b", font=SUB_F, anchor="mm")
    concept_footer(d, ["Mono = 0–1 элемент, Flux = 0–N. Reactor — основа WebFlux, R2DBC, WebClient."], h)
    save(img, "reactor-concept-01.png")


def concept_02():
    img, d, h = concept_canvas(
        "§2 Observer и Listener",
        "Слева: Subject знает Observer · Справа: Spring шлёт событие → @EventListener",
    )
    # --- Observer (слева) ---
    d.text((350, 100), "OBSERVER", fill="#1d4ed8", font=SUB_F, anchor="mm")
    subj_y = 175
    box(d, 350, subj_y, "Subject", w=200, h=70, fill="#dbeafe", lines=[
        "наблюдаемый объект",
        "OrderModel / статус",
    ])
    box(d, 150, subj_y + 120, "Observer 1", w=140, h=50, fill="#eff6ff")
    box(d, 350, subj_y + 120, "Observer 2", w=140, h=50, fill="#eff6ff")
    box(d, 550, subj_y + 120, "Observer 3", w=140, h=50, fill="#eff6ff")
    arr(d, 350, subj_y + 38, 150, subj_y + 92, label="хранит список", color="#2563eb")
    arr(d, 350, subj_y + 38, 350, subj_y + 92, color="#2563eb")
    arr(d, 350, subj_y + 38, 550, subj_y + 92, color="#2563eb")
    d.text((350, subj_y + 195), "setStatus(\"PAID\") → update() у всех", fill="#475569", font=NOTE_F, anchor="mm")

    # --- Listener Spring (справа) ---
    listener_title_y = 100
    d.text((920, listener_title_y), "LISTENER (Spring)", fill="#b45309", font=SUB_F, anchor="mm")
    rx = 920
    gap = 48
    h1, w1 = 54, 230
    cy1 = 175  # как subj_y слева — заголовок на y=100 не перекрывается
    box(d, rx, cy1, "", w=w1, h=h1, fill="#fef3c7", lines=["Spring Context", "источник"])

    h2, w2 = 50, 250
    cy2 = cy1 + h1 // 2 + gap + h2 // 2
    box(d, rx, cy2, "", w=w2, h=h2, fill="#fff7ed", lines=["ApplicationReadyEvent", "событие"])

    h3, w3 = 76, 230
    cy3 = cy2 + h2 // 2 + gap + h3 // 2
    box(d, rx, cy3, "", w=w3, h=h3, fill="#ecfdf5", lines=["@EventListener", "onAppReady()", "ваш код"])

    arr_down(d, rx, cy1 + h1 // 2, cy2 - h2 // 2, label="публикует", color="#d97706", label_dx=115)
    arr_down(d, rx, cy2 + h2 // 2, cy3 - h3 // 2, label="вызывает", color="#059669", label_dx=115)
    d.text((rx, cy3 + h3 // 2 + 32), "Spring не знает, что внутри onAppReady", fill="#475569", font=NOTE_F, anchor="mm")

    d.line([(600, 120), (600, cy3 + h3 // 2 + 50)], fill="#e2e8f0", width=2)
    concept_footer(d, [
        "Observer: Subject сам зовёт наблюдателей при смене данных.",
        "Listener: Spring шлёт событие → @EventListener. Reactor ≈ Observer.",
    ], h)
    save(img, "reactor-concept-02.png")


def concept_02_1():
    """§2.1 — императивный vs реактивный: кто занимает поток, где ждёт запрос."""
    img_h = 880
    img, d, h = concept_canvas(
        "§2.1 Императивный vs реактивный — кто ждёт",
        "Слева: поток занят всё время ожидания · Справа: поток свободен, цепочка ждёт ответ",
        height=img_h,
    )
    d.text((300, 108), "ИМПЕРАТИВНО (Servlet)", fill="#b91c1c", font=SUB_F, anchor="mm")
    d.text((900, 108), "РЕАКТИВНО (WebFlux)", fill="#047857", font=SUB_F, anchor="mm")
    d.line([(600, 125), (600, 455)], fill="#e2e8f0", width=2)

    # --- слева: потоки заняты ---
    ly = 155
    for lbl in ["Поток-1", "Поток-2", "Поток-3"]:
        box(d, 300, ly, "", w=230, h=50, fill="#fee2e2", outline="#dc2626", lines=[
            f"{lbl}: запрос → ждёт БД",
            "поток ЗАНЯТ 2 мин",
        ])
        ly += 56
    d.text((300, ly + 6), "… ещё 97 потоков так же", fill="#64748b", font=NOTE_F, anchor="mm")
    box(d, 300, ly + 46, "", w=250, h=62, fill="#fef2f2", outline="#b91c1c", lines=[
        "Запрос 101",
        "очередь Tomcat",
        "ждёт свободный поток",
    ])

    # --- справа: event loop ---
    box(d, 900, 155, "", w=250, h=50, fill="#ecfdf5", outline="#059669", lines=[
        "Netty event loop",
        "мало потоков (напр. 4)",
    ])
    box(d, 900, 225, "", w=250, h=54, fill="#f0fdf4", outline="#059669", lines=[
        "Запрос A: findById → R2DBC",
        "запрос в БД ушёл",
    ])
    box(d, 900, 295, "", w=250, h=44, fill="#f0fdf4", outline="#059669", lines=[
        "Запросы B, C, D — те же потоки",
    ])
    box(d, 900, 355, "", w=250, h=50, fill="#dcfce7", outline="#047857", lines=[
        "Ответ БД → onNext",
        "цепочка A продолжается",
    ])
    arr_down(d, 900, 252, 268, label=None, color="#059669")
    arr_down(d, 900, 322, 330, label=None, color="#059669")
    d.text((640, 240), "поток", fill="#059669", font=NOTE_F, anchor="mm")
    d.text((640, 258), "свободен", fill="#059669", font=NOTE_F, anchor="mm")

    # --- аналогия: ж/д ---
    d.text((600, 420), "Аналогия: ж/д", fill="#475569", font=SUB_F, anchor="mm")
    box(d, 300, 475, "", w=250, h=54, fill="#fff7ed", outline="#d97706", lines=[
        "Поезд на главном пути",
        "линия занята — другие ждут",
    ])
    box(d, 900, 475, "", w=270, h=54, fill="#fff7ed", outline="#d97706", lines=[
        "Вагоны на отстойном пути",
        "локомотив едет по главной",
    ])

    concept_footer(d, [
        "Императивно: «очередь» — это очередь ЗАПРОСОВ на свободный ПОТОК (Tomcat). Поток всё время занят ожиданием.",
        "Реактивно: поток не ждёт — цепочка Mono/Flux «приостановлена» до callback от БД/HTTP. Очереди есть у операторов (backpressure) и у Schedulers.boundedElastic — но не «100 потоков в очереди».",
    ], h)
    save(img, "reactor-concept-02-1.png")


def concept_03():
    img, d, h = concept_canvas("§3 Mono vs Flux", "Одна посылка vs тележка курьера")
    box(d, 300, 220, "Mono", w=200, h=120, fill="#dbeafe", lines=["0 или 1", "findById"])
    box(d, 900, 220, "Flux", w=200, h=120, fill="#dcfce7", lines=["0…N", "findAll · SSE"])
    d.text((300, 320), "📦 одна", fill="#64748b", font=SUB_F, anchor="mm")
    d.text((900, 320), "📦📦📦 много", fill="#64748b", font=SUB_F, anchor="mm")
    concept_footer(d, ["Спросите: сколько элементов вернёт операция? Один → Mono, несколько → Flux."], h)
    save(img, "reactor-concept-03.png")


def concept_04():
    img, d, h = concept_canvas("§4 Backpressure", "Официант приносит порциями — не 50 тарелок сразу")
    box(d, 250, 210, "Кухня\n(Flux)", w=160, h=56)
    box(d, 950, 210, "Гость\n(подписчик)", w=160, h=56)
    arr(d, 330, 210, 870, 210, label="request(3)")
    arr(d, 870, 280, 330, 280, label="onNext × 3", color="#059669")
    arr(d, 330, 350, 870, 350, label="request(3) ещё")
    concept_footer(d, [
        "request(3) — подписчик просит порцию. Обработал → просит снова.",
        "subscribe() без настроек = «готов принять всё сразу» — для Mono OK, для миллионов строк — риск памяти.",
    ], h)
    save(img, "reactor-concept-04.png")


def concept_05():
    img, d, h = concept_canvas("§5 subscribe vs block", "Netflix фоном vs замереть перед экраном")
    box(d, 320, 210, "subscribe()", w=220, h=100, fill="#dcfce7", lines=["асинхронно", "цепочка живёт"])
    box(d, 880, 210, "block()", w=220, h=100, fill="#fef2f2", lines=["поток ждёт", "только тест/main"])
    d.text((600, 340), "WebFlux: return Mono — Spring подписывается сам", fill="#1d4ed8", font=SUB_F, anchor="mm")
    concept_footer(d, ["block() внутри WebFlux-сервиса запрещён — убивает неблокирующую модель."], h)
    save(img, "reactor-concept-05.png")


def concept_06_overview():
    img, d, h = concept_canvas("§6 map vs flatMap — сигнатуры", "map → обычный объект · flatMap → Mono/Flux")
    box(d, 280, 200, "map", w=200, h=90, fill="#ecfdf5", lines=["User → email", "String → UPPER"])
    box(d, 620, 200, "flatMap", w=220, h=90, fill="#eff6ff", lines=["id → Mono<User>", "открыть коробку"])
    box(d, 280, 360, "❌ map+findById", w=220, h=70, fill="#fef2f2", outline="#dc2626", lines=["Mono в потоке"])
    box(d, 620, 360, "✅ flatMap+findById", w=220, h=70, fill="#ecfdf5", outline="#059669", lines=["User наружу"])
    concept_footer(d, [
        "Аналогия: map — снять кожуру с яблока; flatMap — открыть коробку с обещанием (Mono).",
        "Подробные sequence-рисунки — ниже в §6.",
    ], h)
    save(img, "reactor-concept-06.png")


def concept_07():
    img, d, h = concept_canvas("§7 subscribeOn / publishOn", "Где включают конвейер vs где работают станки ниже")
    y = 230
    box(d, 180, y, "Источник")
    box(d, 420, y, "map")
    box(d, 660, y, "publishOn")
    box(d, 900, y, "map ниже")
    arr(d, 265, y, 335, y)
    arr(d, 505, y, 575, y)
    arr(d, 745, y, 815, y)
    d.text((180, y + 80), "subscribeOn\n→ здесь", fill="#d97706", font=NOTE_F, anchor="mm")
    d.text((660, y + 80), "дальше другой\nпул потоков", fill="#2563eb", font=NOTE_F, anchor="mm")
    concept_footer(d, ["subscribeOn — подписка к источнику. publishOn — всё ниже по цепочке."], h)
    save(img, "reactor-concept-07.png")


def concept_08():
    img, d, h = concept_canvas("§8 Schedulers", "Разные бригады: CPU vs блокирующий I/O")
    box(d, 300, 210, "parallel()", w=200, h=90, fill="#dbeafe", lines=["CPU, map", "без block()"])
    box(d, 900, 210, "boundedElastic()", w=220, h=90, fill="#fef3c7", lines=["JDBC, файлы", "legacy block"])
    concept_footer(d, ["block() на parallel() → IllegalStateException. Netty — не для JDBC."], h)
    save(img, "reactor-concept-08.png")


def concept_09():
    img, d, h = concept_canvas("§9 Cold vs Hot", "Netflix на каждого vs прямой эфир радио")
    box(d, 300, 210, "Cold", w=220, h=100, fill="#dbeafe", lines=["каждый subscribe", "новый SQL/HTTP"])
    box(d, 900, 210, "Hot", w=220, h=100, fill="#fce7f3", lines=["источник уже идёт", "прошлое не повторить"])
    concept_footer(d, [
        "Cold/Hot — это про источник (Publisher), не про map/flatMap.",
        "WebClient, R2DBC — cold. share()/cache() меняют поведение (см. §19).",
    ], h)
    save(img, "reactor-concept-09.png")


def concept_10():
    img, d, h = concept_canvas("§10 Ошибки", "Красная лампа на конвейере — нужен сценарий")
    box(d, 200, 210, "findById")
    box(d, 500, 210, "map")
    box(d, 800, 210, "onErrorResume", fill="#fef3c7", outline="#d97706")
    arr(d, 285, 210, 415, 210)
    arr(d, 585, 210, 710, 210, color="#dc2626", label="onError")
    arr(d, 890, 210, 1050, 210, label="fallback Mono", color="#059669")
    concept_footer(d, ["onErrorReturn — константа. onErrorResume — другой Mono/Flux."], h)
    save(img, "reactor-concept-10.png")


def concept_11():
    img, d, h = concept_canvas("§11 Retry", "Перезвонить — новая попытка с нуля")
    box(d, 250, 210, "Клиент")
    box(d, 600, 210, "WebClient")
    arr(d, 335, 210, 515, 210, label="запрос")
    arr(d, 515, 280, 335, 280, color="#dc2626", label="timeout")
    arr(d, 335, 350, 515, 350, label="retry — снова", color="#059669")
    concept_footer(d, ["retry = новая подписка. Осторожно с POST без idempotency-key."], h)
    save(img, "reactor-concept-11.png")


def concept_12():
    img, d, h = concept_canvas("§12 StepVerifier", "Чек-лист курьера: a → b → конец")
    y = 220
    for x, t in [(180, "Flux.just\na,b"), (420, "StepVerifier"), (660, "expectNext a"), (900, "verifyComplete")]:
        box(d, x, y, t, w=150, h=56 if "\n" in t else 48)
    for i, xs in enumerate([(255, 345), (495, 585), (735, 825)]):
        arr(d, xs[0], y, xs[1], y)
    concept_footer(d, ["Обычный assertEquals после subscribe() не сработает — результат ещё в пути."], h)
    save(img, "reactor-concept-12.png")


def concept_13():
    img, d, h = concept_canvas("§13 WebFlux + Reactor", "Официант передаёт заказ-Mono на кухню")
    names = ["Браузер", "Controller", "Service", "R2DBC", "JSON"]
    xs = [120, 320, 520, 720, 920]
    y = 210
    for x, n in zip(xs, names):
        box(d, x, y, n, w=140)
    for i in range(4):
        arr(d, xs[i] + 72, y, xs[i + 1] - 72, y)
    d.text((600, 310), "Spring подписывается сам — subscribe() не вызываем", fill="#64748b", font=SUB_F, anchor="mm")
    concept_footer(d, ["Один тип Mono/Flux от HTTP до БД — цепочка без block()."], h)
    save(img, "reactor-concept-13.png")


def concept_14():
    img, d, h = concept_canvas("§14 Reactor vs RxJava", "Похожие инструменты, разные коробки")
    box(d, 300, 210, "Reactor", w=200, h=90, fill="#dbeafe", lines=["Mono · Flux", "Spring"])
    box(d, 900, 210, "RxJava", w=200, h=90, fill="#f3e8ff", lines=["Single · Flowable", "Android"])
    concept_footer(d, ["В Spring Boot reactive — Reactor. Не смешивать без нужды."], h)
    save(img, "reactor-concept-14.png")


def concept_15():
    img, d, h = concept_canvas("§15 Когда reactive", "Автобус vs такси на каждого")
    box(d, 320, 200, "✅ WebFlux+R2DBC\nмного I/O", w=240, h=70, fill="#ecfdf5", outline="#059669")
    box(d, 880, 200, "✅ MVC+virtual threads\nпростой CRUD", w=260, h=70, fill="#dbeafe")
    box(d, 600, 320, "❌ reactive+block внутри", w=260, h=56, fill="#fef2f2", outline="#dc2626")
    concept_footer(d, ["Reactive имеет смысл end-to-end без block()."], h)
    save(img, "reactor-concept-15.png")


def concept_16():
    img, d, h = concept_canvas("§16 Disposable", "Пульт от будильника — dispose()")
    box(d, 300, 210, "Flux.interval")
    box(d, 700, 210, "Disposable")
    arr(d, 385, 210, 615, 210, label="subscribe()")
    arr(d, 615, 290, 385, 290, color="#dc2626", label="dispose() → cancel")
    concept_footer(d, ["WebFlux-контроллер — Spring сам управляет подпиской."], h)
    save(img, "reactor-concept-16.png")


def concept_17():
    img, d, h = concept_canvas("§17 Блокирующий код", "Одна касса — block() стопорит очередь")
    box(d, 300, 210, "Netty\nбыстрая касса", w=180, h=56)
    box(d, 900, 210, "boundedElastic\nмедленная касса", w=200, h=56, fill="#fef3c7")
    d.text((600, 300), "JDBC / sleep → subscribeOn(boundedElastic)", fill="#64748b", font=SUB_F, anchor="mm")
    concept_footer(d, ["Лучше R2DBC и WebClient — без block() вообще."], h)
    save(img, "reactor-concept-17.png")


def concept_18():
    img, d, h = concept_canvas("§18 Шпаргалка операторов", "Надписи над станками на конвейере")
    ops = [("map", "объект"), ("flatMap", "Mono/Flux"), ("filter", "отбор"), ("concatMap", "порядок"), ("zip", "парами")]
    x0 = 120
    for i, (op, hint) in enumerate(ops):
        box(d, x0 + i * 210, 220, op, w=150, h=70, lines=[hint])
    concept_footer(d, ["merge — по готовности; concat — строго по очереди."], h)
    save(img, "reactor-concept-18.png")


def concept_19():
    img, d, h = concept_canvas("§19 share() vs cache()", "Hot без истории vs повтор для новых подписчиков")
    box(d, 280, 210, "share()", w=220, h=90, fill="#fce7f3", lines=["общий поток", "опоздавший — мимо"])
    box(d, 880, 210, "cache()", w=220, h=90, fill="#dbeafe", lines=["запомнить", "новый subscribe — replay"])
    concept_footer(d, ["share — live-данные. cache — дорогой запрос один раз, раздать многим."], h)
    save(img, "reactor-concept-19.png")


def concept_20():
    img, d, h = concept_canvas("§20 switchMap vs flatMap", "Новый элемент — отменить предыдущий (поиск)")
    box(d, 250, 210, "flatMap", w=200, h=80, fill="#eff6ff", lines=["все запросы", "параллельно"])
    box(d, 650, 210, "switchMap", w=200, h=80, fill="#fef3c7", lines=["только последний", "старый cancel"])
    box(d, 1050, 210, "concatMap", w=200, h=80, fill="#ecfdf5", lines=["по очереди", "порядок id"])
    concept_footer(d, ["typeahead / автодополнение → switchMap. Пакет id → flatMap или concatMap."], h)
    save(img, "reactor-concept-20.png")


def concept_21():
    img, d, h = concept_canvas("§21 Отладка цепочки", ".log() · checkpoint() · Hooks.onOperatorDebug()")
    y = 220
    for x, t in [(180, "Flux"), (420, ".log()"), (660, ".checkpoint()"), (900, "stack trace")]:
        box(d, x, y, t, w=150)
    for xs in [(255, 345), (495, 585), (735, 825)]:
        arr(d, xs[0], y, xs[1], y)
    concept_footer(d, ["Senior: ReactorDebugAgent в prod. Junior: начните с .log() на dev."], h)
    save(img, "reactor-concept-21.png")


def concept_22():
    img, d, h = concept_canvas("§22 Context (MDC, traceId)", "ThreadLocal не едет между потоками — нужен Reactor Context")
    box(d, 280, 210, "ThreadLocal\n(MDC)", w=180, h=56, fill="#fef2f2", outline="#dc2626")
    box(d, 700, 210, "Reactor\nContext", w=180, h=56, fill="#dcfce7", outline="#059669")
    arr(d, 370, 210, 610, 210, label="publishOn → другой поток", color="#64748b")
    d.text((700, 310), "contextWrite + deferContextual", fill="#64748b", font=SUB_F, anchor="mm")
    concept_footer(d, ["Micrometer Tracing прокидывает traceId через Context автоматически."], h)
    save(img, "reactor-concept-22.png")


# --- Документ: где хранится состояние (reactive-where-state-lives.md) ---


def state_event_loop():
    """Что такое Event Loop — в начале reactive-where-state-lives.md."""
    img, d, h = concept_canvas(
        "Что такое Event Loop (Netty в WebFlux)",
        "Один поток обслуживает много соединений — короткие задачи, без блокировки на I/O",
        height=840,
    )
    box(d, 600, 135, "", w=300, h=52, fill="#ecfdf5", outline="#059669", lines=[
        "Event Loop Thread",
        "while (есть задачи) { взять → выполнить }",
    ])
    steps = [
        (320, 215, "1. взять задачу", "из внутренней очереди"),
        (880, 215, "2. короткая работа", "принять HTTP, onNext, map"),
        (320, 305, "3. I/O не готов", "зарегистрировать callback, уйти"),
        (880, 305, "4. снова в цикл", "другая задача / клиент"),
    ]
    for x, y, t1, t2 in steps:
        box(d, x, y, "", w=210, h=50, fill="#f8fafc", outline="#94a3b8", lines=[t1, t2])
    arr(d, 430, 215, 520, 155, color="#059669")
    arr(d, 770, 155, 860, 215, color="#059669")
    arr(d, 860, 255, 770, 305, color="#059669")
    arr(d, 430, 305, 520, 175, color="#059669")

    d.text((600, 375), "один поток — много клиентов", fill="#475569", font=SUB_F, anchor="mm")
    for i, lbl in enumerate(["Клиент A", "Клиент B", "Клиент C"]):
        box(d, 280 + i * 200, 410, lbl, w=140, h=40, fill="#eff6ff", outline="#2563eb")
    arr(d, 280, 435, 520, 165, color="#cbd5e1")
    arr(d, 480, 435, 560, 165, color="#cbd5e1")
    arr(d, 680, 435, 640, 165, color="#cbd5e1")

    box(d, 600, 490, "", w=420, h=50, fill="#fee2e2", outline="#dc2626", lines=[
        "НЕЛЬЗЯ на event loop: block(), sleep(), JDBC",
        "поток встанет — пострадают ВСЕ соединения",
    ])
    concept_footer(d, [
        "Event loop — не «магия», а поток + очередь задач + неблокирующий I/O (epoll).",
        "WebFlux = Spring поверх Netty. Reactor-цепочка выполняется на потоках loop (или publishOn).",
    ], h)
    save(img, "reactive-state-00-event-loop.png")


def state_two_requests_timeline():
    """Два запроса (баланс + курсы) — очередь loop и парковка в heap."""
    img_h = 1000
    img, d, h = concept_canvas(
        "Два запроса — один event loop",
        "А: баланс за май (R2DBC) · Б: курс валют (WebClient)",
        height=img_h,
    )
    lx, rx = 305, 895
    lw, rw = 370, 310
    d.text((lx, 118), "Очередь задач event loop", fill="#047857", font=SUB_F, anchor="mm")
    d.text((rx, 118), "Heap — парковка", fill="#1d4ed8", font=SUB_F, anchor="mm")
    d.line([(600, 128), (600, 720)], fill="#e2e8f0", width=2)

    rows = [
        (["T1. У клиента А пришёл HTTP"], ["пока пусто"], "#eff6ff", "#2563eb", "#f8fafc", "#94a3b8"),
        (["T2. А: контроллер → subscribe", "R2DBC: SQL отправлен"], ["цепочка А", "создана в heap"], "#f0fdf4", "#059669", "#f8fafc", "#94a3b8"),
        (["T3. А ждёт PostgreSQL"], ["А: ЖДЁТ", "приостановлена"], "#fef3c7", "#d97706", "#fff7ed", "#d97706"),
        (["T4. У клиента Б пришёл HTTP"], ["А всё ещё", "в heap"], "#eff6ff", "#2563eb", "#f8fafc", "#94a3b8"),
        (["T5. Б: контроллер → subscribe", "WebClient: HTTP отправлен"], ["А + Б", "в heap"], "#f0fdf4", "#059669", "#f8fafc", "#94a3b8"),
        (["T6. Б ждёт API курсов"], ["Б: ЖДЁТ", "приостановлена"], "#fef3c7", "#d97706", "#fff7ed", "#d97706"),
        (["T7. Задача: ответ БД для А"], ["А: onNext", "JSON → канал А"], "#dcfce7", "#047857", "#ecfdf5", "#059669"),
        (["T8. Задача: ответ API для Б"], ["Б: onNext", "JSON → канал Б"], "#dcfce7", "#047857", "#ecfdf5", "#059669"),
    ]
    y = 148
    for i, (left_lines, right_lines, fl, ol, fr, orr) in enumerate(rows):
        n = max(len(left_lines), len(right_lines))
        bh = max(50, 22 + n * 18)
        cy = y + bh // 2
        box(d, lx, cy, "", w=lw, h=bh, fill=fl, outline=ol, lines=left_lines)
        box(d, rx, cy, "", w=rw, h=bh, fill=fr, outline=orr, lines=right_lines)
        if i in (2, 4, 5):
            d.text((600, cy), "→", fill="#64748b", font=MSG_F, anchor="mm")
        y += bh + 14

    concept_footer(d, [
        "T3: запрос А не завершён — приостановлен в heap. Поток взял T4 (клиент Б).",
        "T7/T8: в очередь loop попали задачи «продолжи цепочку А/Б» после ответа БД и API.",
    ], h)
    save(img, "reactive-state-00-two-requests.png")


def state_three_containers():
    """Три места хранения: очередь Runnable, selector, heap Reactor."""
    img, d, h = concept_canvas(
        "Где что хранится: три «контейнера»",
        "Очередь задач loop · регистрация I/O в ОС · цепочка Reactor в heap",
        height=820,
    )
    # 1 — event loop
    box(d, 200, 200, "", w=300, h=120, fill="#ecfdf5", outline="#059669", lines=[
        "1. EventLoop (поток Netty)",
        "Queue<Runnable> taskQueue",
        "задачи: «продолжи чтение»",
    ])
    box(d, 200, 340, "", w=300, h=70, fill="#f0fdf4", outline="#059669", lines=[
        "maxPendingTasks",
        "по умолч. ≈ Integer.MAX_VALUE",
    ])
    # 2 — OS
    box(d, 600, 200, "", w=300, h=120, fill="#eff6ff", outline="#2563eb", lines=[
        "2. ОС (epoll / kqueue)",
        "каналы: HTTP, PostgreSQL",
        "«жду байты на сокете»",
    ])
    # 3 — heap reactor
    box(d, 1000, 200, "", w=300, h=120, fill="#fff7ed", outline="#d97706", lines=[
        "3. Heap JVM (Reactor)",
        "Subscription, операторы",
        "«после onNext сделать map»",
    ])
    arr(d, 350, 200, 450, 200, color="#64748b", label="ответ ОС")
    arr(d, 750, 200, 850, 200, color="#64748b", label="onNext")
    d.text((600, 320), "Runnable = объект в heap, ссылка кладётся в taskQueue", fill="#475569", font=NOTE_F, anchor="mm")
    concept_footer(d, [
        "Callback не «список в вашем коде» — объект Runnable + запись в Queue<Runnable> у EventLoop.",
        "Регулировка очереди loop: -Dio.netty.eventloop.maxPendingTasks=N (Netty 4). Иначе — риск OOM при перегрузке.",
    ], h)
    save(img, "reactive-state-05-three-containers.png")


def state_flow_request():
    img, d, h = concept_canvas(
        "Когда приходит HTTP-запрос (WebFlux)",
        "Цепочка = описание · subscribe запускает · I/O паркует состояние в heap",
        height=820,
    )
    steps = [
        ("1", "Контроллер возвращает Mono/Flux", "декларативное описание, не результат"),
        ("2", "WebFlux вызывает subscribe()", "цепочка запускается"),
        ("3", "Операторы до I/O выполняются", "map, filter — на event loop"),
        ("4", "БД / HTTP — неблокирующий запрос", "поток освобождён → event loop"),
        ("5", "Состояние в Subscription (heap)", "куда продолжить после ответа"),
        ("6", "Callback → onNext → цепочка дальше", "тот же или другой поток loop"),
    ]
    y = 130
    for num, title, sub in steps:
        box(d, 600, y, "", w=520, h=54, fill="#f0fdf4", outline="#059669", lines=[f"{num}. {title}", sub])
        if num != "6":
            arr_down(d, 600, y + 27, y + 55, color="#059669")
        y += 82
    concept_footer(d, [
        "Цепочка ленивая до subscribe. Ожидание I/O не держит поток — держит объект подписки в куче.",
    ], h)
    save(img, "reactive-state-01-request-flow.png")


def state_stack_vs_heap():
    img, d, h = concept_canvas(
        "Где хранится состояние задачи",
        "Императивно: стек потока · Реактивно: объекты в heap (Subscription, операторы)",
        height=800,
    )
    d.text((300, 108), "ИМПЕРАТИВНО", fill="#b91c1c", font=SUB_F, anchor="mm")
    d.text((900, 108), "РЕАКТИВНО", fill="#047857", font=SUB_F, anchor="mm")
    d.line([(600, 120), (600, 480)], fill="#e2e8f0", width=2)
    box(d, 300, 175, "", w=260, h=50, fill="#fee2e2", outline="#dc2626", lines=[
        "Request → Thread",
        "блокируется на БД",
    ])
    box(d, 300, 245, "", w=260, h=50, fill="#fee2e2", outline="#dc2626", lines=[
        "Состояние = стек вызовов",
        "~1 MB на поток",
    ])
    box(d, 300, 315, "", w=260, h=50, fill="#fee2e2", outline="#dc2626", lines=[
        "Поток ЗАНЯТ всё ожидание",
    ])
    box(d, 900, 175, "", w=280, h=50, fill="#ecfdf5", outline="#059669", lines=[
        "Request → Event Loop (Netty)",
        "создаёт Mono/Flux (lazy)",
    ])
    box(d, 900, 245, "", w=280, h=54, fill="#f0fdf4", outline="#059669", lines=[
        "subscribe() → I/O → поток свободен",
        "состояние в Subscription (heap)",
    ])
    box(d, 900, 320, "", w=280, h=54, fill="#dcfce7", outline="#047857", lines=[
        "callback → продолжение цепочки",
        "continuation, не стек вызовов",
    ])
    d.text((600, 400), "Нет привычного стека — state machine из операторов", fill="#475569", font=NOTE_F, anchor="mm")
    concept_footer(d, [
        "Императивно: переменные в frames стека заблокированного потока.",
        "Реактивно: Subscription + Subscriber + Context — объекты в куче; поток крутит event loop.",
    ], h)
    save(img, "reactive-state-02-stack-vs-heap.png")


def seq_webclient_user():
    img, d = canvas(
        "Sequence: WebClient — ожидание удалённого сервера",
        "Поток не ждёт HTTP — Netty callback возобновляет Mono",
    )
    names = ["Клиент", "Controller", "WebFlux", "WebClient", "Netty", "Удалённый API"]
    xs = [lifeline_x(6, i, left=80, right=W - 80) for i in range(6)]
    for x, n in zip(xs, names):
        draw_participant(d, x, n)
    y = 175
    arrow_h(d, xs[0], xs[1], y, "GET /user/1", numbered=1)
    y += 55
    arrow_h(d, xs[1], xs[2], y, "return Mono<User>", numbered=2)
    y += 55
    self_call(d, xs[2], y, "subscribe()", numbered=3)
    y += 75
    arrow_h(d, xs[2], xs[3], y, "webClient.get()", numbered=4)
    y += 55
    arrow_h(d, xs[3], xs[4], y, "async HTTP", numbered=5)
    y += 55
    arrow_h(d, xs[4], xs[5], y, "запрос", color="#64748b")
    y += 45
    d.text((xs[4], y), "поток в event loop", fill="#059669", font=NOTE_F, anchor="mm")
    d.text((xs[4], y + 18), "свободен", fill="#059669", font=NOTE_F, anchor="mm")
    y += 50
    arrow_h(d, xs[5], xs[4], y, "ответ", dashed=True, color="#059669")
    y += 55
    arrow_h(d, xs[4], xs[3], y, "callback", dashed=True, color="#059669", numbered=6)
    y += 55
    arrow_h(d, xs[3], xs[2], y, "onNext(User)", dashed=True, color="#059669", numbered=7)
    y += 55
    self_call(d, xs[2], y, "map(enrich)", numbered=8)
    y += 75
    arrow_h(d, xs[2], xs[0], y, "JSON", dashed=True, color="#059669", numbered=9)
    footer_note(d, "Пояснение", [
        "Шаг 5–6: состояние «ждём ответ» = Subscription в heap, не блокированный thread.",
        "Шаг 7–9: callback на event loop → map → ответ клиенту.",
    ])
    save(img, "reactive-state-03-webclient-sequence.png")


def state_virtual_threads():
    img, d, h = concept_canvas(
        "WebFlux vs Virtual Threads (Java 21+)",
        "Оба паркуют ожидание — разный механизм",
        height=760,
    )
    d.text((300, 108), "WebFlux + Reactor", fill="#047857", font=SUB_F, anchor="mm")
    d.text((900, 108), "Virtual Threads", fill="#1d4ed8", font=SUB_F, anchor="mm")
    d.line([(600, 120), (600, 420)], fill="#e2e8f0", width=2)
    for cx, lines in [
        (300, ["Состояние: Subscription (heap)", "Callback / continuation", "API: R2DBC, WebClient", "Мало потоков event loop"]),
        (900, ["Состояние: стек VT в heap", "Псевдо-блокирующий синтаксис", "API: JDBC, RestTemplate OK", "Carrier threads + планировщик JVM"]),
    ]:
        y = 160
        for line in lines:
            box(d, cx, y, line, w=260, h=44, fill="#f8fafc", outline="#94a3b8")
            y += 58
    concept_footer(d, [
        "WebFlux: реактивные типы и неблокирующий стек. VT: обычный код, JVM паркует виртуальный поток при I/O.",
    ], h)
    save(img, "reactive-state-04-virtual-threads.png")


if __name__ == "__main__":
    seq_map_email()
    seq_map_wrong()
    seq_flatmap_ok()
    seq_get_user_summary()
    seq_concatmap()
    seq_spring_boot_startup()
    concept_intro()
    concept_01()
    concept_02()
    concept_02_1()
    concept_03()
    concept_04()
    concept_05()
    concept_06_overview()
    concept_07()
    concept_08()
    concept_09()
    concept_10()
    concept_11()
    concept_12()
    concept_13()
    concept_14()
    concept_15()
    concept_16()
    concept_17()
    concept_18()
    concept_19()
    concept_20()
    concept_21()
    concept_22()
    state_event_loop()
    state_two_requests_timeline()
    state_three_containers()
    state_flow_request()
    state_stack_vs_heap()
    seq_webclient_user()
    state_virtual_threads()
