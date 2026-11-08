package com.planner.mapper;

import com.planner.entity.AdviceRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AdviceRecordMapper {
    int insert(AdviceRecord advice);

    List<AdviceRecord> selectByUserAndStatusDesc(@Param("userId") Long userId, @Param("status") String status);

    List<AdviceRecord> selectByUserAndStatusAsc(@Param("userId") Long userId, @Param("status") String status);

    /** 去重用：最近 limit 条（含已删除/归档的），新的在前 */
    List<AdviceRecord> selectRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);

    AdviceRecord selectByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    int updateStatus(@Param("id") Long id, @Param("status") String status);
}
