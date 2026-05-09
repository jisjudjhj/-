package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.ProductReviewVote;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface ProductReviewVoteMapper extends BaseMapper<ProductReviewVote> {

    @Insert("INSERT IGNORE INTO product_review_vote (review_id, user_id, device_fingerprint) " +
            "VALUES (#{reviewId}, #{userId}, #{deviceFingerprint})")
    int insertIgnore(@Param("reviewId") Long reviewId,
                     @Param("userId") Long userId,
                     @Param("deviceFingerprint") String deviceFingerprint);

    @Select("SELECT COUNT(*) FROM product_review_vote WHERE review_id = #{reviewId} AND user_id = #{userId}")
    int countByReviewAndUser(@Param("reviewId") Long reviewId, @Param("userId") Long userId);

    @Delete("DELETE FROM product_review_vote WHERE review_id = #{reviewId} AND user_id = #{userId}")
    int deleteByReviewAndUser(@Param("reviewId") Long reviewId, @Param("userId") Long userId);
}
