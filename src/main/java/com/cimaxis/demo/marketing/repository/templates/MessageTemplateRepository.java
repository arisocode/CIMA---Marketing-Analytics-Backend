package com.cimaxis.demo.marketing.repository.templates;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.marketing.domain.templates.MessageTemplate;

@Repository
public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, UUID> {

    List<MessageTemplate> findAllByOrderByNameAsc();
}
