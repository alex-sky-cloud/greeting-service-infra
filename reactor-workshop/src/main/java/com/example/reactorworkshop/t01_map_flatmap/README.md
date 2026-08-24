# t01_map_flatmap (глава 10)

## Цель

`map` / `Mono.flatMap` / `Flux.flatMap` на домене счётчиков, не магазина.

## Данные

Таблица `meters` (250) и `readings` (100 000). `flux-flatmap` берёт только первые `METERS_IN_FLATMAP_DEMO` счётчиков, иначе один HTTP утащит все показания.

## HTTP

- `GET /api/t01/map`
- `GET /api/t01/mono-flatmap/{meterId}`
- `GET /api/t01/flux-flatmap`
