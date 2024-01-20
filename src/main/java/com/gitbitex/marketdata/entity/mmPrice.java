package com.gitbitex.marketdata.entity;

import com.gitbitex.enums.TimeInForce;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
public class mmPrice {

    private String productId;
    private LocalDateTime timeInForce;
    private BigDecimal randomPrice;




    public void setTimeInForce() {
        this.timeInForce = LocalDateTime.now();
    }
}
