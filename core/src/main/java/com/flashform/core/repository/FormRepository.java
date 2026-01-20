package com.flashform.core.repository;

import com.flashform.core.entity.Form;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FormRepository extends JpaRepository<Form, Long> {
    // JpaRepository 已經幫你寫好了 save(), findById(), findAll() 等方法
}