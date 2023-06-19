package it.begenau.sample.messaging.domain.models;

public record Message(String payload, int executionId) {
}
