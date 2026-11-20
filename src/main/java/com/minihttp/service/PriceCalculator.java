package com.minihttp.service;

public abstract class PriceCalculator {
    public final int calculate(int unitPrice, int quantity) {
        int subtotal = unitPrice * quantity;
        return subtotal - this.discount(subtotal, quantity);
    }

    protected abstract int discount(int subtotal, int quantity);
}
