package com.cimaxis.demo.marketing.repository.notifications;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.notifications.EmailDelivery;

@Repository
public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {

    long countByStatusAndCreatedAtGreaterThanEqual(String status, LocalDateTime desde);
}
