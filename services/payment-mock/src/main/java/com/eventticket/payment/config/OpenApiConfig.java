package com.eventticket.payment.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Payment Mock Service",
        version = "v1",
        description = "Simulated payment provider for the Event Ticket Booking system. "
                + "Charges are idempotent per Idempotency-Key and can be forced to fail or slow down."))
public class OpenApiConfig {
}
