package com.tripfit.tripfit.user.googlecalendar.client;

import java.time.Instant;

public record GoogleFreeBusyInterval(
    Instant start,
    Instant end
) {
}
