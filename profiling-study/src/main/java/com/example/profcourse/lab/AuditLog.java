package com.example.profcourse.lab;

import com.example.profcourse.common.Slow;

/** Журнал аудита: запись на «диск» под монитором журнала. */
public class AuditLog {

    private long written;

    public synchronized void write(String line) {
        Slow.sleep(5);
        written++;
    }

    public synchronized long written() {
        return written;
    }
}
