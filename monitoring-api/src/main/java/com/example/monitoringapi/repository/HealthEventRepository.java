package com.example.monitoringapi.repository;

import com.example.monitoringapi.entity.HealthEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthEventRepository extends JpaRepository<HealthEvent, Long> {
}
