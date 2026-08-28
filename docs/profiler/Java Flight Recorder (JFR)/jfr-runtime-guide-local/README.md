# Как открыть руководство Java Flight Recorder

В архиве лежит локальная копия *Java Platform, Standard Edition Java Flight Recorder Runtime Guide* (**JMC** 5.4, документ E61551-01) **на русском**. Имена параметров, команд и пунктов интерфейса **JMC** оставлены на английском, рядом в скобках перевод. Пояснения — по-русски.

Источник: https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/about.htm

Распакуйте zip в любую папку. Дальше нужен браузер.

Ниже не шаги подряд, а **выбор одного способа**. Достаточно одного.

---

## Вариант А. Самый простой: просто файлы

Работает на Windows, macOS и Ubuntu. Ничего дополнительно ставить не нужно.

1. Распакуйте архив.
2. Зайдите в `jfr-runtime-guide-local/jmc-5-4/jfr-runtime-guide`.
3. Откройте двойным щелчком `toc.htm` (оглавление) или сразу `about.htm`.
4. Дальше ходите по ссылкам. Шапка сайта Oracle, поиск, вход и нижняя полоса убраны: на странице только текст главы и ссылка «Содержание».

---

## Вариант Б. Локальный сервер

Окно терминала или консоли **не закрывайте**, пока читаете. Откроется http://127.0.0.1:8765/

### Windows

Дважды щёлкните `start.bat`. Python не нужен.

### Ubuntu

В терминале:

```bash
cd jfr-runtime-guide-local
chmod +x start.sh
./start.sh
```

Нужен Python 3. На обычной Ubuntu он уже есть (`python3`). Если вдруг нет: `sudo apt install python3`. Браузер откроется сам.

### macOS

Дважды щёлкните `start.command`. Если система спросит разрешение — разрешите. Либо в Terminal:

```bash
cd jfr-runtime-guide-local
chmod +x start.command
./start.command
```

Нужен Python 3. Если его нет, используйте вариант А.

---

## Что лежит в архиве

| Файл или папка | Зачем |
|----------------|--------|
| `jmc-5-4/jfr-runtime-guide/toc.htm` | Оглавление |
| `jmc-5-4/jfr-runtime-guide/*.htm` | Главы руководства |
| `jmc-5-4/jfr-runtime-guide/JFRUH.pdf` | Оригинальный PDF на английском |
| `dcommon/`, `nav/` | Стили текста (шапка и подвал сайта Oracle убраны) |
| `index.html` | Переход к оглавлению (вариант Б) |
| `start.bat` | Сервер для Windows |
| `start-server.ps1` | Его вызывает `start.bat` |
| `start.sh` | Сервер для Ubuntu (и macOS из терминала) |
| `start.command` | Сервер для macOS двойным щелчком |
| `README.md` | Эта памятка |

Юридические тексты Oracle (страница заголовка, `dcommon/html/cpyr.htm`) оставлены на английском. PDF не переводился.

Интернет нужен только для ссылок наружу (сайт Oracle). Само руководство читается с диска.
