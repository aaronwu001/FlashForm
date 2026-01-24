package com.flashform.core.repository;

import com.flashform.core.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    @Query("SELECT s.userId FROM Submission s WHERE s.formId = :formId")
    List<String> findAllUserIdsByFormId(Long formId);
    List<Submission> findByFormId(Long formId);
}