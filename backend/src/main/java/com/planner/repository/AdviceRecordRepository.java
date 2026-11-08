package com.planner.repository;

import com.planner.entity.AdviceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdviceRecordRepository extends JpaRepository<AdviceRecord, Long> {
    List<AdviceRecord> findByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    List<AdviceRecord> findByUserIdAndStatusOrderByIdAsc(Long userId, String status);

    /** 去重用：最近 100 条（含已删除/归档的） */
    List<AdviceRecord> findTop100ByUserIdOrderByIdDesc(Long userId);

    Optional<AdviceRecord> findByIdAndUserId(Long id, Long userId);
}
