# Context в Reactor как immutable-хранилище (матрёшка)

## Оглавление

- [1. Context — иммутабельное хранилище (упрощённая версия)](#1-context--иммутабельное-хранилище-упрощённая-версия)
- [2. Три уровня операторов — три версии Context](#2-три-уровня-операторов--три-версии-context)
- [3. Что происходит на уровне объектов](#3-что-происходит-на-уровне-объектов)

---

## 1. Context — иммутабельное хранилище (упрощённая версия)

**Утверждение:**
Context в Reactor неизменяем: методы записи (`put`, `putAll`) не изменяют существующий объект, а создают новый экземпляр.

Источник: https://github.com/reactor/reactor-core/blob/master/reactor-core/src/main/java/reactor/util/context/Context.java

> "A Context is immutable. It exposes write methods like put and putAll but they produce a new instance."

RU:

> "Context неизменяем (immutable). Он предоставляет методы записи, такие как put и putAll, но они создают новый экземпляр."

Реальная реализация Reactor хранит значения в компактных внутренних классах (Context1, Context2... ContextN), но суть можно показать проще — как маленький иммутабельный узел, который хранит ссылку на предыдущий Context и свою собственную пару ключ-значение:

```java
public final class Context {

    private final Object key;
    private final Object value;
    private final Context parent; // ссылка на "предыдущий" (более старый) Context

    private Context(Object key, Object value, Context parent) {
        this.key = key;
        this.value = value;
        this.parent = parent;
    }

    public static Context empty() {
        return null; // пустой контекст — просто отсутствие узла
    }

    // put НЕ изменяет текущий объект — он создаёт НОВЫЙ,
    // который оборачивает старый как parent
    public Context put(Object key, Object value) {
        return new Context(key, value, this);
    }

    public Object get(Object key) {
        Context node = this;
        while (node != null) {
            if (node.key.equals(key)) return node.value;
            node = node.parent;
        }
        return null;
    }
}
```

Обратите внимание: `put()` не трогает старый объект — он буквально создаёт новый узел, у которого поле `parent` ссылается на предыдущий Context. Это ровно та же механика, что и в паттерне Decorator: новый объект оборачивает старый и хранит ссылку на него.

---

### Как это работает шаг за шагом (см.пример класса ниже):

  - Возьмём c3 (контекст верхнего оператора) и вызовем c3.get("K3"). 


- Вот что происходит **внутри** цикла **while**:

    - **node** = c3 (_Context №3_) — _проверяем_: **node.key** равен "K1", а нам нужен "K3" → не совпадает, значит условие `if()` пропускается и достаем другой объект из **Context** (`node = node.parent`).
        - таким образом внутри `while`, теперь будет ссылка на объект который был внутри объекта `матрешки`. **Обратите внимание** на поле объект `Context` -> `private final Context parent;`

    - node = **node.parent** → теперь **node** — это c2 (_Context №2_). Проверяем: **node.key** равен "K2" → не совпадает, значит условие `if()` пропускается и достаем другой объект из **Context** (`node = node.parent`).

    - node = **node.parent** → теперь node — это c1 (_Context №1_). Проверяем: node.key равен "K3" → совпадает! Возвращаем node.value, то есть "value3".

- Именно поэтому get() "видит" ключи из вложенных объектов — потому что каждый **Context-узел** хранит не сами данные всех предков, а только одну свою пару ключ-значение плюс ссылку на предыдущий узел. 
- Чтобы найти чужой ключ, метод просто идёт по этим ссылкам вглубь, как по цепочке, пока не наткнётся на нужный узел или не дойдёт до null.
---

## 2. Три уровня операторов — три версии Context

```java
public class ContextDemo {
    
    public static void main(String[] args) {
        Context c0 = Context.empty();

        // Operator3 — ближе всего к subscribe()
        Context c1 = c0 == null
                ? new Context("K3", "value3", null)
                : c0.put("K3", "value3");
        System.out.println("Operator3 видит: K3=" + c1.get("K3"));

        // Operator2 — оборачивает c1
        Context c2 = c1.put("K2", "value2");
        System.out.println("Operator2 видит: K3=" + c2.get("K3") + ", K2=" + c2.get("K2"));

        // Operator1 — самый верхний, оборачивает c2
        Context c3 = c2.put("K1", "value1");
        System.out.println("Operator1 видит: K3=" + c3.get("K3")
                + ", K2=" + c3.get("K2") + ", K1=" + c3.get("K1"));
    }
}
```

Вывод:

```
Operator3 видит: K3=value3
Operator2 видит: K3=value3, K2=value2
Operator1 видит: K3=value3, K2=value2, K1=value1
```

---

## 3. Что происходит на уровне объектов

Здесь и видна настоящая "матрёшка" на уровне данных: `c3` (контекст Operator1) — это объект, чьё поле `parent` указывает на `c2`, а поле `parent` у `c2` указывает на `c1`. Три отдельных иммутабельных объекта, вложенных друг в друга, — старые объекты `c1` и `c2` при этом никуда не исчезают и не меняются, они просто становятся "внутренним слоем" для новых.

Официальная документация описывает то же самое, только словами:

Источник: https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html

> "contextWrite(ContextView) merges the ContextView you provide and the Context from downstream [...] resulting in a NEW Context for upstream."

RU:

> "contextWrite(ContextView) объединяет переданный вами ContextView с Context, пришедшим снизу [...] в результате создаётся НОВЫЙ Context для операторов выше."
