package com.mtole.taskmanager.activity;

import java.time.Instant;

public record ActivityStatsFilter(
        Instant from,
        Instant to
) {}
