package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.UserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserBehaviorMapper extends BaseMapper<UserBehavior> {

    List<Map<String, Object>> selectUserProductScores(@Param("userId") Long userId);

    List<Map<String, Object>> selectUserProductScoresWithDecay(@Param("userId") Long userId);

    List<Map<String, Object>> selectUserPreferences(@Param("userId") Long userId);

    List<Map<String, Object>> selectUserSearchCategoryPreferences(@Param("userId") Long userId,
                                                                  @Param("limit") int limit);

    List<Map<String, Object>> selectBehaviorStats();

    List<Long> selectActiveUserIds(@Param("limit") int limit);

    List<Long> selectActiveUserIdsSince(@Param("startTime") LocalDateTime startTime,
                                        @Param("limit") int limit);

    List<Long> selectSimilarUsers(@Param("userId") Long userId, @Param("limit") int limit);

    List<Long> selectRecommendedProductIds(@Param("userIds") List<Long> userIds,
                                            @Param("currentUserId") Long currentUserId,
                                            @Param("limit") int limit);

    List<Long> selectSimilarProductIds(@Param("productId") Long productId, @Param("limit") int limit);

    List<Map<String, Object>> selectUserBehaviorStats(@Param("userId") Long userId);

    @Select("SELECT " +
            "(SELECT COUNT(DISTINCT sh.user_id) " +
            "   FROM search_history sh " +
            "  WHERE sh.update_time >= #{startTime}) AS search_user_count, " +
            "(SELECT COUNT(DISTINCT sh.user_id) " +
            "   FROM search_history sh " +
            "  WHERE sh.update_time >= #{startTime} " +
            "    AND EXISTS ( " +
            "      SELECT 1 " +
            "        FROM user_behavior ub " +
            "       WHERE ub.user_id = sh.user_id " +
            "         AND ub.behavior_type = 'view' " +
            "         AND ub.create_time >= #{startTime} " +
            "    )) AS search_to_click_user_count, " +
            "(SELECT COUNT(DISTINCT sh.user_id) " +
            "   FROM search_history sh " +
            "  WHERE sh.update_time >= #{startTime} " +
            "    AND EXISTS ( " +
            "      SELECT 1 " +
            "        FROM user_behavior ub " +
            "       WHERE ub.user_id = sh.user_id " +
            "         AND ub.behavior_type = 'purchase' " +
            "         AND ub.create_time >= #{startTime} " +
            "    )) AS search_to_purchase_user_count")
    Map<String, Object> selectSearchConversionStats(@Param("startTime") LocalDateTime startTime);
}
