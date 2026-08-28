package com.monty.matchbook.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ProcessedEventsTest {

    @Test
    void anUnseenEventIsNew() {
        ProcessedEvents processed = new ProcessedEvents(10);

        assertThat(processed.isNew(UUID.randomUUID())).isTrue();
    }

    @Test
    void theSameEventIsNotNewTwice() {
        // given
        ProcessedEvents processed = new ProcessedEvents(10);
        UUID eventId = UUID.randomUUID();

        // when
        processed.isNew(eventId);

        // then
        assertThat(processed.isNew(eventId)).isFalse();
    }

    @Test
    void differentEventsAreEachNew() {
        ProcessedEvents processed = new ProcessedEvents(10);

        assertThat(processed.isNew(UUID.randomUUID())).isTrue();
        assertThat(processed.isNew(UUID.randomUUID())).isTrue();
    }

    @Test
    void theSetIsBoundedByCapacity() {
        // given
        ProcessedEvents processed = new ProcessedEvents(5);

        // when
        IntStream.range(0, 100).forEach(i -> processed.isNew(UUID.randomUUID()));

        // then
        assertThat(processed.size()).isEqualTo(5);
    }

    @Test
    void theOldestEventsAreForgottenFirst() {
        // given
        ProcessedEvents processed = new ProcessedEvents(3);
        UUID oldest = UUID.randomUUID();
        processed.isNew(oldest);

        // when
        IntStream.range(0, 3).forEach(i -> processed.isNew(UUID.randomUUID()));

        // then
        assertThat(processed.isNew(oldest)).isTrue();
    }

    @Test
    void onlyOneOfManyThreadsSeesAnEventAsNew() throws Exception {
        // given
        ProcessedEvents processed = new ProcessedEvents(1000);
        UUID eventId = UUID.randomUUID();
        AtomicInteger claimedNew = new AtomicInteger();
        CountDownLatch startLine = new CountDownLatch(1);

        // when
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            List<? extends Future<?>> attempts = IntStream.range(0, 16)
                    .mapToObj(i -> pool.submit(() -> {
                        startLine.await();
                        if (processed.isNew(eventId)) {
                            claimedNew.incrementAndGet();
                        }
                        return null;
                    }))
                    .toList();

            startLine.countDown();

            for (Future<?> attempt : attempts) {
                attempt.get();
            }
        }

        // then
        assertThat(claimedNew).hasValue(1);
    }
}
