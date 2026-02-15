package com.flashform.core.repository;

import com.flashform.core.entity.Form;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FormRepository extends JpaRepository<Form, Long> {
    List<Form> findByOwnerId(String ownerId);
}
