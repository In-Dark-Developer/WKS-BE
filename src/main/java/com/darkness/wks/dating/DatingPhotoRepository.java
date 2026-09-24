package com.darkness.wks.dating;

import com.darkness.wks.dating.entity.DatingPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DatingPhotoRepository extends JpaRepository<DatingPhoto, UUID> {
}
