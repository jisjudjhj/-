package com.ecommerce.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecommerce.entity.SearchHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface SearchHistoryMapper extends BaseMapper<SearchHistory> {

    @Update("UPDATE search_history SET search_count = search_count + 1, update_time = NOW() " +
            "WHERE user_id = #{userId} AND keyword = #{keyword}")
    int incrementCount(@Param("userId") Long userId, @Param("keyword") String keyword);

    @Select("SELECT keyword, SUM(search_count) AS total_count " +
            "FROM search_history " +
            "GROUP BY keyword " +
            "ORDER BY total_count DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectHotKeywords(@Param("limit") int limit);

    @Select("SELECT DISTINCT keyword FROM search_history " +
            "WHERE keyword LIKE CONCAT(#{prefix}, '%') " +
            "GROUP BY keyword " +
            "ORDER BY SUM(search_count) DESC " +
            "LIMIT #{limit}")
    List<String> selectSuggestions(@Param("prefix") String prefix, @Param("limit") int limit);

    @Select("SELECT DISTINCT keyword FROM search_history " +
            "WHERE keyword LIKE CONCAT('%', #{keyword}, '%') " +
            "GROUP BY keyword " +
            "ORDER BY SUM(search_count) DESC " +
            "LIMIT #{limit}")
    List<String> selectSuggestionsContaining(@Param("keyword") String keyword, @Param("limit") int limit);

    @Select("SELECT keyword FROM search_history " +
            "GROUP BY keyword " +
            "ORDER BY SUM(search_count) DESC " +
            "LIMIT #{limit}")
    List<String> selectTopKeywordsForCorrection(@Param("limit") int limit);
}
