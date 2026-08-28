package com.tripfit.tripfit.trip.event;

import java.util.UUID;

public record AllMembersSubmittedEvent(
    UUID tripId
) {
}
