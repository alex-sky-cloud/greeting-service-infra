# Сравнение `bitbucket-pipelines.yml` и `.gitlab-ci.yml`

## Соответствие конструкций

| Bitbucket | GitLab | Описание |
|---|---|---|
| `definitions.steps` + `&anchor` | нет — шаги описываются напрямую | Bitbucket использует YAML-якоря для переиспользования, GitLab — нет |
| `definitions.caches.gradle` | `cache.paths: [.gradle/]` | Кэш зависимостей Gradle между шагами |
| `services: - docker` | `services: - docker:24.0-dind` + `DOCKER_HOST` | Docker-in-Docker для сборки образов |
| `$BITBUCKET_COMMIT` | `$CI_COMMIT_SHORT_SHA` | Short commit hash — тег Docker-образа |
| `trigger: manual` | `when: manual` | Ручное подтверждение деплоя в prod |
| `deployment: Production` | `environment: name: prod` | Трекинг окружения в UI |
| `branches: feature/*:` | `rules: if '$CI_COMMIT_BRANCH =~ /^feature\//'` | Фильтрация pipeline по ветке |
| `pull-requests: "**"` | `if '$CI_PIPELINE_SOURCE == "merge_request_event"'` | Запуск на Merge Request |
| `custom: rollback-prod` | `rollback-prod` job с `when: manual` | Ручной откат prod |
| `artifacts: - image_tag.txt` | `artifacts: paths: [image_tag.txt]` | Передача тега образа между шагами |
| `needs:` (нет явного) | `needs: [build-and-push-docker]` | Явные зависимости между шагами |

---

## Ключевые отличия в поведении

**Зависимости между шагами.**

- **GitLab** использует `needs:` для явного указания зависимостей.
  - Это позволяет `deploy-dev` и `deploy-prod` запускаться независимо — каждый ждёт только своего `needs`.
- В **Bitbucket** шаги внутри одной ветки выполняются строго последовательно.

**Docker-in-Docker.**

- **GitLab** требует явного указания `services: - docker:24.0-dind`
и переменных `DOCKER_TLS_CERTDIR: ""` / `DOCKER_HOST: tcp://docker:2375`.
- В **Bitbucket** достаточно `services: - docker`.

**Переиспользование шагов.**

- **Bitbucket** использует YAML-якоря (`&build-and-test`, `<<: *build-and-test`).
- **GitLab** не поддерживает такую конструкцию для job-ов — каждый job описывается явно.
  - Для **пере-использования** в GitLab применяются `extends:` или YAML-якоря только для фрагментов (не job-ов целиком).

**Расположение файла.**

- `.gitlab-ci.yml` должен лежать в **корне репозитория** — рядом с `app/`, `infra/`.
  - **GitLab** не поддерживает произвольный путь без дополнительной настройки в `Settings → CI/CD → General → Custom CI/CD config path`.

**Ручные pipeline (аналог `custom:`).**

- В **Bitbucket** секция `custom:` создаёт отдельные pipeline, запускаемые из UI.
- В **GitLab** `rollback-prod` — это обычный job с `when: manual`, запускается из
`CI/CD → Pipelines → Run pipeline` или кнопкой в интерфейсе **pipeline**.
