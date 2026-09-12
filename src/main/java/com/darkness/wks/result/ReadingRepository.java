package com.darkness.wks.result;

import com.darkness.wks.result.entity.Reading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReadingRepository extends JpaRepository<Reading, UUID> {
}
