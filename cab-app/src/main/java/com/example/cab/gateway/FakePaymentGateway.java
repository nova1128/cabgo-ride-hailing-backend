package com.example.cab.gateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Pretends to be a payment provider. Always succeeds, and is idempotent per key like a real one. */
@Component
public class FakePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentGateway.class);

    private final Map<String, String> chargesByKey = new ConcurrentHashMap<>();

    @Override
    public String charge(String idempotencyKey, BigDecimal amount) {
        return chargesByKey.computeIfAbsent(idempotencyKey, key -> {
            String chargeId = "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            log.info("FAKE GATEWAY: charged {} (charge {})", amount, chargeId);
            return chargeId;
        });
    }
}
