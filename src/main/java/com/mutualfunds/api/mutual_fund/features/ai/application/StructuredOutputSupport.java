package com.mutualfunds.api.mutual_fund.features.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class StructuredOutputSupport {

    private final ObjectMapper objectMapper;

    public <T> Optional<T> generate(
            Class<T> outputType,
            Supplier<T> nativeStructuredSupplier,
            Supplier<String> rawTextSupplier,
            Function<String, String> repairSupplier,
            Function<T, Optional<T>> validator) {
        if (nativeStructuredSupplier != null) {
            try {
                Optional<T> validated = validate(nativeStructuredSupplier.get(), validator);
                if (validated.isPresent()) {
                    return validated;
                }
                log.warn("Native structured output failed validation for {}", outputType.getSimpleName());
            } catch (Exception ex) {
                log.warn("Native structured output failed for {}: {}", outputType.getSimpleName(), ex.getMessage());
            }
        }

        String raw = null;
        if (rawTextSupplier != null) {
            try {
                raw = rawTextSupplier.get();
                Optional<T> validated = parseAndValidate(raw, outputType, validator);
                if (validated.isPresent()) {
                    return validated;
                }
            } catch (Exception ex) {
                log.warn("Raw structured output failed for {}: {}", outputType.getSimpleName(), ex.getMessage());
            }
        }

        if (repairSupplier != null && raw != null && !raw.isBlank()) {
            try {
                String repaired = repairSupplier.apply(raw);
                return parseAndValidate(repaired, outputType, validator);
            } catch (Exception ex) {
                log.warn("Repaired structured output failed for {}: {}", outputType.getSimpleName(), ex.getMessage());
            }
        }

        return Optional.empty();
    }

    private <T> Optional<T> parseAndValidate(String raw, Class<T> outputType, Function<T, Optional<T>> validator)
            throws Exception {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        T parsed = objectMapper.readValue(stripCodeFences(raw), outputType);
        return validate(parsed, validator);
    }

    private <T> Optional<T> validate(T parsed, Function<T, Optional<T>> validator) {
        if (validator == null) {
            return Optional.ofNullable(parsed);
        }
        return validator.apply(parsed);
    }

    private String stripCodeFences(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        return trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
    }
}
