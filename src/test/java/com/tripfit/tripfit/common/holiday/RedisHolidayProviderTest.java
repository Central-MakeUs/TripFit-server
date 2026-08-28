package com.tripfit.tripfit.common.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisHolidayProviderTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private SetOperations<String, String> setOperations;

  @Test
  void findHolidaysBetween_redisFailure_returnsEmptyInsteadOfThrowing() {
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(setOperations.members(anyString())).thenThrow(new QueryTimeoutException("redis down"));

    Set<LocalDate> holidays =
        provider().findHolidaysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    assertThat(holidays).isEmpty();
  }

  @Test
  void findHolidaysBetween_coldCache_returnsEmpty() {
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(setOperations.members(anyString())).thenReturn(Set.of());

    assertThat(provider().findHolidaysBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)))
        .isEmpty();
  }

  @Test
  void findHolidaysBetween_spansTwoYears_readsBothKeysAndClipsToRange() {
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(setOperations.members("holiday:kr:2026")).thenReturn(Set.of("20261225", "20260301"));
    when(setOperations.members("holiday:kr:2027")).thenReturn(Set.of("20270101"));

    Set<LocalDate> holidays =
        provider().findHolidaysBetween(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 31));

    assertThat(holidays)
        .containsExactlyInAnyOrder(LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 1));
  }

  @Test
  void replaceYear_emptyResult_keepsExistingCache() {
    provider().replaceYear(2026, Set.of());

    verify(redisTemplate, never()).rename(anyString(), anyString());
    verify(setOperations, never()).add(anyString(), any(String[].class));
  }

  @Test
  void replaceYear_writesToStagingThenRenamesAtomically() {
    when(redisTemplate.opsForSet()).thenReturn(setOperations);

    provider().replaceYear(2026, Set.of(LocalDate.of(2026, 3, 1)));

    verify(redisTemplate).rename(anyString(), org.mockito.ArgumentMatchers.eq("holiday:kr:2026"));
  }

  private RedisHolidayProvider provider() {
    return new RedisHolidayProvider(redisTemplate);
  }
}
