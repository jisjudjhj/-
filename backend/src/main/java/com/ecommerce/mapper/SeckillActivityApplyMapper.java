package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.SeckillActivityApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SeckillActivityApplyMapper extends BaseMapper<SeckillActivityApply> {

    @Update("UPDATE seckill_activity_apply " +
            "SET sold_count = sold_count + #{amount} " +
            "WHERE id = #{applyId} " +
            "AND audit_status = 1 " +
            "AND sold_count + #{amount} <= seckill_stock")
    int lockSoldCount(@Param("applyId") Long applyId, @Param("amount") Integer amount);

    @Update("UPDATE seckill_activity_apply " +
            "SET sold_count = CASE WHEN sold_count >= #{amount} THEN sold_count - #{amount} ELSE 0 END " +
            "WHERE id = #{applyId}")
    int restoreSoldCount(@Param("applyId") Long applyId, @Param("amount") Integer amount);

    @Select("SELECT saa.*, sa.name AS activity_name, sa.start_time AS activity_start_time, " +
            "sa.end_time AS activity_end_time, sa.publish_status AS publish_status " +
            "FROM seckill_activity_apply saa " +
            "JOIN seckill_activity sa ON sa.id = saa.activity_id " +
            "WHERE saa.id = #{applyId} " +
            "LIMIT 1")
    SeckillActivityApply selectWithActivity(@Param("applyId") Long applyId);

    @Select("SELECT saa.*, sa.name AS activity_name, sa.start_time AS activity_start_time, " +
            "sa.end_time AS activity_end_time, sa.publish_status AS publish_status " +
            "FROM seckill_activity_apply saa " +
            "JOIN seckill_activity sa ON sa.id = saa.activity_id " +
            "WHERE saa.product_id = #{productId} " +
            "AND saa.audit_status = 1 " +
            "AND sa.publish_status = 1 " +
            "AND sa.start_time <= #{now} " +
            "AND sa.end_time >= #{now} " +
            "ORDER BY sa.start_time ASC, saa.id ASC " +
            "LIMIT 1")
    SeckillActivityApply selectActiveByProductId(@Param("productId") Long productId, @Param("now") LocalDateTime now);

    @Select("SELECT saa.*, sa.name AS activity_name, sa.start_time AS activity_start_time, " +
            "sa.end_time AS activity_end_time, sa.publish_status AS publish_status " +
            "FROM seckill_activity_apply saa " +
            "JOIN seckill_activity sa ON sa.id = saa.activity_id " +
            "WHERE saa.product_id = #{productId} " +
            "AND saa.audit_status = 1 " +
            "AND sa.publish_status = 1 " +
            "AND sa.end_time > #{now} " +
            "ORDER BY sa.start_time ASC, saa.id ASC " +
            "LIMIT 1")
    SeckillActivityApply selectUpcomingOrActiveByProductId(@Param("productId") Long productId, @Param("now") LocalDateTime now);

    @Select("SELECT " +
            "p.id AS productId, p.name AS productName, p.image AS productImage, p.price AS productPrice, " +
            "p.sales_count AS salesCount, p.rating AS rating, " +
            "saa.id AS applyId, saa.activity_id AS activityId, saa.seckill_price AS seckillPrice, " +
            "saa.seckill_stock AS seckillStock, saa.sold_count AS soldCount, " +
            "saa.limit_per_user AS limitPerUser, " +
            "sa.name AS activityName, sa.cover_image AS activityCoverImage, sa.description AS activityDescription, " +
            "sa.start_time AS startTime, sa.end_time AS endTime " +
            "FROM seckill_activity_apply saa " +
            "JOIN seckill_activity sa ON sa.id = saa.activity_id " +
            "JOIN product p ON p.id = saa.product_id " +
            "WHERE saa.audit_status = 1 " +
            "AND sa.publish_status = 1 " +
            "AND sa.start_time <= #{now} " +
            "AND sa.end_time >= #{now} " +
            "AND p.status = 1 " +
            "AND p.deleted = 0 " +
            "ORDER BY sa.sort_order ASC, sa.start_time ASC, saa.id DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectActiveProducts(@Param("now") LocalDateTime now, @Param("limit") Integer limit);

    @Select("SELECT " +
            "p.id AS productId, p.name AS productName, p.image AS productImage, p.price AS productPrice, " +
            "p.sales_count AS salesCount, p.rating AS rating, " +
            "saa.id AS applyId, saa.activity_id AS activityId, saa.seckill_price AS seckillPrice, " +
            "saa.seckill_stock AS seckillStock, saa.sold_count AS soldCount, " +
            "saa.limit_per_user AS limitPerUser, " +
            "sa.name AS activityName, sa.cover_image AS activityCoverImage, sa.description AS activityDescription, " +
            "sa.start_time AS startTime, sa.end_time AS endTime, " +
            "CASE " +
            "WHEN sa.start_time <= #{now} AND sa.end_time >= #{now} THEN 1 " +
            "WHEN sa.start_time > #{now} THEN 0 " +
            "ELSE 2 END AS runtimeStatus " +
            "FROM seckill_activity_apply saa " +
            "JOIN seckill_activity sa ON sa.id = saa.activity_id " +
            "JOIN product p ON p.id = saa.product_id " +
            "WHERE saa.audit_status = 1 " +
            "AND sa.publish_status = 1 " +
            "AND sa.end_time >= #{now} " +
            "AND p.status = 1 " +
            "AND p.deleted = 0 " +
            "ORDER BY " +
            "CASE WHEN sa.start_time <= #{now} AND sa.end_time >= #{now} THEN 0 ELSE 1 END ASC, " +
            "sa.sort_order ASC, sa.start_time ASC, saa.id DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectDisplayProducts(@Param("now") LocalDateTime now, @Param("limit") Integer limit);

    @Select("SELECT " +
            "p.id AS productId, p.name AS productName, p.image AS productImage, p.price AS productPrice, " +
            "p.sales_count AS salesCount, p.rating AS rating, " +
            "saa.id AS applyId, saa.activity_id AS activityId, saa.seckill_price AS seckillPrice, " +
            "saa.seckill_stock AS seckillStock, saa.sold_count AS soldCount, " +
            "saa.limit_per_user AS limitPerUser, " +
            "sa.name AS activityName, sa.cover_image AS activityCoverImage, sa.description AS activityDescription, " +
            "sa.start_time AS startTime, sa.end_time AS endTime, " +
            "CASE " +
            "WHEN sa.start_time <= #{now} AND sa.end_time >= #{now} THEN 1 " +
            "WHEN sa.start_time > #{now} THEN 0 " +
            "ELSE 2 END AS runtimeStatus " +
            "FROM seckill_activity_apply saa " +
            "JOIN seckill_activity sa ON sa.id = saa.activity_id " +
            "JOIN product p ON p.id = saa.product_id " +
            "WHERE saa.audit_status = 1 " +
            "AND sa.publish_status = 1 " +
            "AND p.status = 1 " +
            "AND p.deleted = 0 " +
            "ORDER BY " +
            "CASE " +
            "WHEN sa.start_time <= #{now} AND sa.end_time >= #{now} THEN 0 " +
            "WHEN sa.start_time > #{now} THEN 1 " +
            "ELSE 2 END ASC, " +
            "CASE WHEN sa.end_time < #{now} THEN sa.end_time END DESC, " +
            "CASE WHEN sa.end_time >= #{now} THEN sa.start_time END ASC, " +
            "sa.sort_order ASC, saa.id DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectHallDisplayProducts(@Param("now") LocalDateTime now, @Param("limit") Integer limit);
}
