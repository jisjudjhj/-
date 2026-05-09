package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.entity.AnalyticsKmeansUserResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface AnalyticsKmeansUserResultMapper extends BaseMapper<AnalyticsKmeansUserResult> {

    IPage<Map<String, Object>> selectSegmentUserPage(Page<?> page,
                                                     @Param("taskId") Long taskId,
                                                     @Param("segmentCode") String segmentCode);

    Map<String, Object> selectUserClusterDetail(@Param("taskId") Long taskId,
                                                @Param("userId") Long userId);

    List<Long> selectUserIdsByTaskAndSegmentCodes(@Param("taskId") Long taskId,
                                                  @Param("segmentCodes") Collection<String> segmentCodes);
}
