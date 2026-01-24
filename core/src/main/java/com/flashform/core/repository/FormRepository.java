package com.flashform.core.repository;

import com.flashform.core.entity.Form;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FormRepository extends JpaRepository<Form, Long> {
    // ✨ 新增：找出該用戶擁有的所有表單
    List<Form> findByOwnerId(String ownerId);
}
