package com.monty.matchbook.gateway;

import com.monty.matchbook.event.OrderCommand;
import com.monty.matchbook.gateway.domain.OutboxEntity;
import com.monty.matchbook.gateway.domain.OutboxRepository;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper json;
    private final Clock clock;
    private final int batchSize;

    OutboxPublisher(
            OutboxRepository outbox,
            KafkaTemplate<String, Object> kafka,
            ObjectMapper json,
            Clock clock,
            @Value("${matchbook.outbox.batch-size}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.json = json;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${matchbook.outbox.poll-interval-ms}",
            initialDelayString = "${matchbook.outbox.poll-interval-ms}")
    public int publishPending() {
        List<OutboxEntity> pending = outbox.findByPublishedAtIsNullOrderByIdAsc(Limit.of(batchSize));
        int published = 0;

        for (OutboxEntity row : pending) {
            if (!publish(row)) {
                break;
            }
            published++;
        }

        return published;
    }

    private boolean publish(OutboxEntity row) {
        try {
            OrderCommand command = json.readValue(row.getPayload(), OrderCommand.class);
            kafka.send(row.getTopic(), row.getMsgKey(), command).join();
        } catch (RuntimeException failure) {
            log.error("outbox row {} could not be published", row.getId(), failure);
            return false;
        }

        row.markPublished(clock.instant());
        outbox.save(row);
        return true;
    }
}
