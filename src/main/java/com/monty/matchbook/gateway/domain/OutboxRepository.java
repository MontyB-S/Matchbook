package com.monty.matchbook.gateway.domain;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEntity, Long> {

    List<OutboxEntity> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

    long countByPublishedAtIsNull();
}
