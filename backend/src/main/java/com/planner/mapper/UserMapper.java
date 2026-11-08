package com.planner.mapper;

import com.planner.entity.User;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface UserMapper {
    User selectByPhone(@Param("phone") String phone);

    int countByPhone(@Param("phone") String phone);

    default boolean existsByPhone(String phone) {
        return countByPhone(phone) > 0;
    }

    /** 回填自增 id */
    int insert(User user);

    int updateLastLoginAt(@Param("id") Long id, @Param("time") LocalDateTime time);

    int markAssessmentDone(@Param("id") Long id);
}
