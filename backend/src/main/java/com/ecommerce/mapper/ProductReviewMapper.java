package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.ProductReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    @Select("SELECT COALESCE(AVG(rating), 5.0) FROM product_review WHERE product_id = #{productId} AND status = 1")
    BigDecimal selectAvgRating(Long productId);

    @Select("SELECT COUNT(*) FROM product_review WHERE product_id = #{productId} AND status = 1")
    int selectReviewCount(Long productId);

    @Update("UPDATE product_review SET helpful_count = COALESCE(helpful_count, 0) + 1 WHERE id = #{reviewId}")
    int increaseHelpfulCount(Long reviewId);

    @Update("UPDATE product_review SET helpful_count = GREATEST(COALESCE(helpful_count, 0) - 1, 0) WHERE id = #{reviewId}")
    int decreaseHelpfulCount(Long reviewId);

    @Select("SELECT COUNT(*) FROM product_review WHERE status = 1 AND helpful_count > 0")
    long countHelpfulReviews();

    @Select("SELECT COALESCE(SUM(helpful_count), 0) FROM product_review WHERE status = 1")
    long sumHelpfulCount();
}
