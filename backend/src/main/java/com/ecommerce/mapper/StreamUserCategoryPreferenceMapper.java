package com.ecommerce.mapper;

import com.ecommerce.entity.StreamUserCategoryPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StreamUserCategoryPreferenceMapper {

    @Select("SELECT user_id, category_id, category_name, preference_score, behavior_count, last_event_time, update_time " +
            "FROM stream_user_category_preference " +
            "WHERE user_id = #{userId} AND preference_score > 0 " +
            "ORDER BY preference_score DESC, behavior_count DESC, update_time DESC " +
            "LIMIT #{limit}")
    List<StreamUserCategoryPreference> selectTopByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT user_id, category_id, category_name, preference_score, behavior_count, last_event_time, update_time " +
            "FROM stream_user_category_preference " +
            "WHERE user_id = #{userId} " +
            "ORDER BY update_time DESC " +
            "LIMIT 1")
    StreamUserCategoryPreference selectLatestByUserId(@Param("userId") Long userId);
}
