package com.flashform.core.repository;

import com.flashform.core.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    // inherited from JpaRepository, including methods save(), findAll(), findById()
}