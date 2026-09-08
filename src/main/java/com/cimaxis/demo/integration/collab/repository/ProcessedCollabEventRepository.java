package com.cimaxis.demo.integration.collab.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cimaxis.demo.integration.collab.domain.ProcessedCollabEvent;

public interface ProcessedCollabEventRepository extends JpaRepository<ProcessedCollabEvent, String> {
}
