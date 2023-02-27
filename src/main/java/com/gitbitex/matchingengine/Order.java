package com.gitbitex.matchingengine;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import com.gitbitex.enums.OrderSide;
import com.gitbitex.enums.OrderType;
import com.gitbitex.matchingengine.command.PlaceOrderCommand;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.BeanUtils;

@Getter
@Setter
public class Order implements Cloneable {
    private String userId;
    private String orderId;
    private OrderType type;
    private OrderSide side;
    private BigDecimal remainingSize;
    private BigDecimal price;
    private BigDecimal remainingFunds;
    private BigDecimal size;
    private BigDecimal funds;
    private boolean postOnly;
    private Date time;

    public Order() {}

    public Order(PlaceOrderCommand command) {
        if (command.getUserId() == null) {
            throw new NullPointerException("userId");
        }
        if (command.getOrderId() == null) {
            throw new NullPointerException("orderId");
        }
        if (command.getOrderType() == null) {
            throw new NullPointerException("orderType");
        }
        if (command.getPrice() == null) {
            throw new NullPointerException("price");
        }

        this.userId = command.getUserId();
        this.orderId = command.getOrderId();
        this.type = command.getOrderType();
        this.side = command.getOrderSide();
        this.price = command.getPrice();
        this.size = command.getSize();
        if (command.getOrderType() == OrderType.LIMIT) {
            this.funds = command.getSize().multiply(command.getPrice());
        } else {
            this.funds = command.getFunds();
        }
        this.remainingSize = this.size;
        this.remainingFunds = this.funds;

        this.time = command.getTime();
    }

    public Order copy() {
        Order copy = new Order();
        BeanUtils.copyProperties(this, copy);
        return copy;
    }

    @Override
    public Order clone() {
        try {
            Order clone = (Order) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
