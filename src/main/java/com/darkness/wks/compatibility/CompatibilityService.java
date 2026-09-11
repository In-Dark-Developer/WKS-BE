package com.darkness.wks.compatibility;

import com.darkness.wks.result.ResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// TODO: 친구궁합 생성/조회 로직 구현
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompatibilityService {

    private final CompatibilityRepository compatibilityRepository;
    private final ResultRepository resultRepository;
    private final CompatibilityCalculator compatibilityCalculator;
}
