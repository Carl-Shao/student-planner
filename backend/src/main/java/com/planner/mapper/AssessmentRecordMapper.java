package com.planner.mapper;

import com.planner.entity.AssessmentRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface AssessmentRecordMapper {
    /** 回填自增 id */
    int insert(AssessmentRecord record);

    AssessmentRecord selectLatestByUserId(@Param("userId") Long userId);

    int updateSummary(@Param("id") Long id, @Param("userId") Long userId,
                      @Param("summary") String summary, @Param("now") LocalDateTime now);
}
