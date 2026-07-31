# Диаграммы Reactor

**В markdown только PNG/SVG** — блоки ` ```mermaid ` запрещены (см. `.cursor/rules/reactor-docs-visual.mdc`).

## Генерация

```bash
python docs/Images-docs/gen_reactor_diagrams.py
```

Создаёт:

| Файл | Раздел гайда |
|------|----------------|
| `reactor-concept-intro.png` | Введение |
| `reactor-concept-01.png` … `18.png` | §1–§18 |
| `reactor-seq-*.png` | §6 map/flatMap (sequence) |

Исходник рисунков: `docs/Images-docs/gen_reactor_diagrams.py`.
