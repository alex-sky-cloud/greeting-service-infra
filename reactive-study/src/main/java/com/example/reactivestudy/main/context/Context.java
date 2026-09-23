package com.example.reactivestudy.main.context;

public final class Context {

    private final Object key;
    private final Object value;
    private final Context parent; // ссылка на "предыдущий" (более старый) Context

    Context(Object key, Object value, Context parent) {
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
            if (node.key.equals(key)){
                return node.value;
            }
            node = node.parent;
        }

        return null;
    }
}