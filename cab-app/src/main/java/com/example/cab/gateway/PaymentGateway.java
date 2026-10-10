package com.example.cab.gateway;

import java.math.BigDecimal;

/**
 * CONCEPT: program against an interface so the real payment provider can be swapped in later.
 * Today the only implementation is FakePaymentGateway (free, no real money). A StripePaymentGateway
 * (Stripe TEST mode is also free) would implement the same method.
 */
public interface PaymentGateway {

    /**
     * Charge the given amount. The idempotency key lets the gateway recognise a retried request and return
     * the SAME charge instead of charging twice (real gateways such as Stripe support this natively).
     *
     * @return the gateway's charge id
     */
    String charge(String idempotencyKey, BigDecimal amount);
}
