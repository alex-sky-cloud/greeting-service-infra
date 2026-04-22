package com.example.greeting.selectForShareAndSelectForUpdate.service;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;

@AllArgsConstructor
@Getter
public class OrderLineQtyDto {

    private Long id;
    private Integer qty;
}