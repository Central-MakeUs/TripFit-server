package com.tripfit.tripfit.notification.event;

import java.util.List;
import java.util.UUID;

public record ScheduleReminderEvent(
    List<UUID> userIds,
    int month
) {
}
