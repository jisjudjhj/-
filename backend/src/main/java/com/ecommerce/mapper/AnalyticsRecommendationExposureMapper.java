package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.AnalyticsRecommendationExposure;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AnalyticsRecommendationExposureMapper extends BaseMapper<AnalyticsRecommendationExposure> {

    Map<String, Object> selectOverallMetrics(@Param("startTime") LocalDateTime startTime);

    Map<String, Object> selectBusinessMetrics(@Param("startTime") LocalDateTime startTime);

    List<Map<String, Object>> selectSceneMetrics(@Param("startTime") LocalDateTime startTime);

    List<Map<String, Object>> selectSegmentMetrics(@Param("startTime") LocalDateTime startTime);

    List<Map<String, Object>> selectAlgorithmMetrics(@Param("startTime") LocalDateTime startTime);

    List<Map<String, Object>> selectAlgorithmSegmentMetrics(@Param("startTime") LocalDateTime startTime);

    List<Map<String, Object>> selectSceneAlgorithmMetrics(@Param("startTime") LocalDateTime startTime);

    List<Map<String, Object>> selectSourceTypeMetrics(@Param("startTime") LocalDateTime startTime);

    List<Map<String, Object>> selectReasonTypeMetrics(@Param("startTime") LocalDateTime startTime);

    List<Map<String, Object>> selectModelVersionMetrics(@Param("startTime") LocalDateTime startTime);

    Map<String, Object> selectSevenDayRepurchaseMetrics(@Param("startTime") LocalDateTime startTime);

    List<Map<String, Object>> selectDailyMetrics(@Param("startTime") LocalDateTime startTime);
}
