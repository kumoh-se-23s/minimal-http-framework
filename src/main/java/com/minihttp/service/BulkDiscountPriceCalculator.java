package com.minihttp.service;

public final class BulkDiscountPriceCalculator extends PriceCalculator {
    private static final int BULK_QUANTITY = 10;
    private static final int DISCOUNT_PERCENT = 10;

    @Override
    protected int discount(int subtotal, int quantity) {
        if (quantity < BULK_QUANTITY) {
            return 0;
        }

        return subtotal * DISCOUNT_PERCENT / 100;
    }
}
