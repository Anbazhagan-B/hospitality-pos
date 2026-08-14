package com.pos.cook.batch;

import com.pos.cook.dto.CookOrder;
import org.springframework.batch.item.ItemReader;

import java.util.Iterator;
import java.util.List;

public class CookOrderItemReader implements ItemReader<CookOrder> {

    private final Iterator<CookOrder> orderIterator;

    public CookOrderItemReader(List<CookOrder> orders) {
        this.orderIterator = orders.iterator();
    }

    @Override
    public CookOrder read() {
        if (orderIterator.hasNext()) {
            return orderIterator.next();
        }
        return null;
    }
}
