package com.cimaxis.demo.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cimaxis.demo.analytics.domain.User;

@Repository
/**
 * Repository JPA para la entidad `USERS`.
 */
public interface UserRepository extends JpaRepository<User, Integer> {
}
