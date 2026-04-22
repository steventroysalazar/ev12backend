package com.example.smsbackend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SimBulkActivationRequest(
    @NotEmpty List<Long> deviceIds,
    @NotNull Boolean activate
) {
}
