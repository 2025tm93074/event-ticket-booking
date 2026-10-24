package com.eventticket.payment.api;

import com.eventticket.payment.api.dto.SimulationSettings;
import com.eventticket.payment.service.PaymentSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Failure injection for demos and test scripts. Not part of the booking flow. */
@RestController
@RequestMapping("/api/v1/admin/simulation")
@Tag(name = "Simulation (admin)")
public class SimulationController {

    private final PaymentSimulator simulator;

    public SimulationController(PaymentSimulator simulator) {
        this.simulator = simulator;
    }

    @GetMapping
    @Operation(summary = "Current simulated gateway behaviour")
    public SimulationSettings get() {
        return new SimulationSettings(simulator.getMode(), simulator.getDelayMs());
    }

    @PutMapping
    @Operation(summary = "Switch gateway behaviour (SUCCESS, FAIL, SLOW) without restarting")
    public SimulationSettings update(@Valid @RequestBody SimulationSettings settings) {
        simulator.update(settings.mode(), settings.delayMs());
        return get();
    }
}
