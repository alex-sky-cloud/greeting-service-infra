package com.example.greeting.readCommited.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "on_call_summary", schema = "iso_demo")
@Getter
@Setter
public class OnCallSummaryEntity {

    @Id
    private Long id;

    @Column(name = "on_call_count")
    private Long onCallCount;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}