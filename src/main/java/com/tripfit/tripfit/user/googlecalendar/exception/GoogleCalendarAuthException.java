package com.tripfit.tripfit.user.googlecalendar.exception;

public class GoogleCalendarAuthException extends RuntimeException {

  public GoogleCalendarAuthException(String message) {
    super(message);
  }

  public GoogleCalendarAuthException(String message, Throwable cause) {
    super(message, cause);
  }
}
