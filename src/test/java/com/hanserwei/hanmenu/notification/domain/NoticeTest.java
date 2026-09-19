package com.hanserwei.hanmenu.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 验证实时提示重试、崩溃领取隔离和个人阅读单调性. */
class NoticeTest {
  private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");

  @Test
  void staleDeliveryCannotCompleteReplacementAttempt() {
    var notice = notice();
    UUID old = notice.claim(NOW);
    assertThat(notice.claim(NOW.plusSeconds(59))).isNull();
    UUID replacement = notice.claim(NOW.plusSeconds(60));
    assertThat(replacement).isNotEqualTo(old);
    assertThat(notice.finish(old, true, null, NOW.plusSeconds(61))).isFalse();
    assertThat(notice.status()).isEqualTo(Notice.Status.IN_FLIGHT);
    assertThat(notice.finish(replacement, true, null, NOW.plusSeconds(61))).isTrue();
    assertThat(notice.status()).isEqualTo(Notice.Status.DELIVERED);
    assertThat(notice.claim(NOW.plusSeconds(120))).isNull();
  }

  @Test
  void retriesAreBoundedButExplicitVersionedRedeliveryPreservesHistory() {
    var notice = notice();
    Instant now = NOW;
    for (int i = 0; i < 5; i++) {
      var attempt = notice.claim(now);
      assertThat(attempt).isNotNull();
      notice.finish(attempt, false, "NO_SUBSCRIBERS", now);
      if (i < 4) {
        now = notice.nextAttemptAt();
      }
    }
    assertThat(notice.status()).isEqualTo(Notice.Status.EXHAUSTED);
    assertThat(notice.attempts()).isEqualTo(5);
    assertThatThrownBy(() -> notice.retry(1, NOW.plusSeconds(3600)))
        .isInstanceOf(NotificationException.class);
    notice.retry(0, NOW.plusSeconds(3600));
    assertThat(notice.status()).isEqualTo(Notice.Status.PENDING);
    assertThat(notice.attempts()).isEqualTo(5);
    assertThat(notice.claim(NOW.plusSeconds(3600))).isNotNull();
    assertThat(notice.attempts()).isEqualTo(6);
  }

  @Test
  void receiptsCannotRegressCrossTheCommittedHeadOrOverwriteNewerDevices() {
    var receipt = new Receipt(UUID.randomUUID(), 5, 2, NOW);
    assertThatThrownBy(() -> receipt.acknowledge(6, 1, 10, NOW))
        .isInstanceOf(NotificationException.class);
    assertThatThrownBy(() -> receipt.acknowledge(4, 2, 10, NOW))
        .isInstanceOf(NotificationException.class);
    assertThatThrownBy(() -> receipt.acknowledge(11, 2, 10, NOW))
        .isInstanceOf(NotificationException.class);
    assertThat(receipt.acknowledge(7, 2, 10, NOW).sequence()).isEqualTo(7);
  }

  @Test
  void streamTicketsExpireAndCannotBeReplayedOrPrinted() {
    var ticket =
        new StreamTicket(
            "session-secret", "ticket-secret", UUID.randomUUID(), 0, NOW.plusSeconds(30), null);
    ticket.consume(NOW);
    assertThatThrownBy(() -> ticket.consume(NOW)).isInstanceOf(NotificationException.class);
    assertThat(ticket.toString()).doesNotContain("session-secret", "ticket-secret");
    var expired = new StreamTicket("session", "ticket", UUID.randomUUID(), 0, NOW, null);
    assertThatThrownBy(() -> expired.consume(NOW)).isInstanceOf(NotificationException.class);
  }

  private Notice notice() {
    return Notice.create(UUID.randomUUID(), 1, UUID.randomUUID(), Notice.Kind.NEW_ORDER, NOW, NOW);
  }
}
