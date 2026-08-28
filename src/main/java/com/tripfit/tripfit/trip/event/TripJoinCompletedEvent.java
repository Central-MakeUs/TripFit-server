package com.tripfit.tripfit.trip.event;

import java.util.UUID;

public record TripJoinCompletedEvent(
    UUID tripId,
    UUID joinedMemberUserId
) {
}
