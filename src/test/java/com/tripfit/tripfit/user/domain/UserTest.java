package com.tripfit.tripfit.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void reviveIfWithdrawn_whenDeleted_clearsDeletedAtAndAllFree() {
    User user = new User("social-id", SocialProvider.GOOGLE, null, null, null);
    user.markDeleted();
    user.applyAllFree(true);

    user.reviveIfWithdrawn();

    assertThat(user.getDeletedAt()).isNull();
    assertThat(user.isAllFree()).isFalse();
  }

  @Test
  void reviveIfWithdrawn_whenNotDeleted_doesNothing() {
    User user = new User("social-id", SocialProvider.GOOGLE, null, null, null);
    user.applyAllFree(true);

    user.reviveIfWithdrawn();

    assertThat(user.getDeletedAt()).isNull();
    assertThat(user.isAllFree()).isTrue();
  }
}
