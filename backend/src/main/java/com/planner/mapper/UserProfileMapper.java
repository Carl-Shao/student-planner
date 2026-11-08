package com.planner.mapper;

import com.planner.entity.UserProfile;
import org.apache.ibatis.annotations.Param;

public interface UserProfileMapper {
    UserProfile selectByUserId(@Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId);

    /** SELECT ... FOR UPDATE：同一用户的画像/建议写入串行化（必须在事务内调用） */
    UserProfile lockByUserId(@Param("userId") Long userId);

    int insert(UserProfile profile);

    int updateById(UserProfile profile);
}
