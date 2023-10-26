package com.gitbitex.openapi.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderDto {
    private String id;
    private String createdAt;
    private String updatedAt;
    private String productId;
    private String UserId;
    private String clientOid;
    private String size;
    private String funds;
    private String filledSize;
    private String executedValue;
    private String price;
    private String  FillFees;
    private String type;
    private String side;
    private String timeInForce;
    private String status;
    private boolean settled;
}
