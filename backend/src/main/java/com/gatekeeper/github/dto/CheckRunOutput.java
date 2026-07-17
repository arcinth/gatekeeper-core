package com.gatekeeper.github.dto;

/** The "output" object GitHub's Checks API accepts on both create and update. */
public record CheckRunOutput(String title, String summary) {
}
