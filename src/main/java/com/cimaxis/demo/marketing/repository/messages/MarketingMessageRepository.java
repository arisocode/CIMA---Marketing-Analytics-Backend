package com.cimaxis.demo.marketing.repository.messages;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.messages.MarketingMessage;

@Repository
public interface MarketingMessageRepository extends JpaRepository<MarketingMessage, UUID> {

    List<MarketingMessage> findByClientIdOrderByCreatedAtAsc(String clientId);

    List<MarketingMessage> findAllByOrderByCreatedAtAsc();

    List<MarketingMessage> findByClientIdAndDirectionAndReadAtIsNull(String clientId,
                                                                    MarketingMessage.Direction direction);

    long countByClientIdAndDirectionAndReadAtIsNull(String clientId, MarketingMessage.Direction direction);

    long countByClientIdAndDirectionAndCreatedAtGreaterThanEqual(String clientId,
                                                                 MarketingMessage.Direction direction,
                                                                 LocalDateTime desde);
}
