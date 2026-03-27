package com.example.heritage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceRepository extends JpaRepository<Resource, Long> {
    List<Resource> findByContributorIdOrderByUpdatedAtDesc(Long contributorId);

    List<Resource> findByContributorIdAndStatusOrderByUpdatedAtDesc(Long contributorId, ResourceStatus status);
    List<Resource> findByContributorIdAndStatusInOrderByUpdatedAtDesc(Long contributorId, List<ResourceStatus> statuses);

    List<Resource> findByStatusOrderByUpdatedAtDesc(ResourceStatus status);
}
