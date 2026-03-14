package com.billbill2.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.billbill2.entity.NewRegionData;


import java.util.List;
import java.util.Map;

public interface BZoneAnalysisService extends IService<NewRegionData> {
    /**
     * 查询指定分区的统计信息
     * @param pidV2 分区ID
     * @return 统计信息（总条数、平均点赞数、平均播放量、平均弹幕数）
     */
    Map<String, Object> getZoneStatistics(Integer pidV2);
    
    /**
     * 查询所有分区的统计信息
     * @param pidV2List 分区ID列表
     * @return 所有分区的统计信息列表
     */
    List<Map<String, Object>> getAllZonesStatistics(List<Integer> pidV2List);
    
    /**
     * 查询指定分区的数据条数
     * @param pidV2 分区ID
     * @return 数据条数
     */
    int getZoneDataCount(Integer pidV2);
    
    /**
     * 查询指定分区的TOP N视频
     * @param pidV2 分区ID
     * @param sortField 排序字段（playCount/likeCount/danmukuCount）
     * @param limit 返回数量
     * @return TOP N视频列表
     */
    List<NewRegionData> getZoneTopVideos(Integer pidV2, String sortField, int limit);
    
    /**
     * 计算视频质量评分
     * @param pidV2 分区ID
     * @param limit 返回数量
     * @return 视频质量评分列表
     */
    List<Map<String, Object>> getZoneVideoQualityScore(Integer pidV2, int limit);
    
    /**
     * 查询点赞率高的视频（点赞数/播放量百分比靠前）
     * @param pidV2 分区ID
     * @param limit 返回数量
     * @return 点赞率高的视频列表
     */
    List<Map<String, Object>> getZoneHighLikeRateVideos(Integer pidV2, int limit);
    
    /**
     * 查询弹幕活跃度高的视频（弹幕数/播放量百分比靠前）
     * @param pidV2 分区ID
     * @param limit 返回数量
     * @return 弹幕活跃度高的视频列表
     */
    List<Map<String, Object>> getZoneHighDanmakuRateVideos(Integer pidV2, int limit);
    
    /**
     * 查询分区视频发布时间分布
     * @param pidV2 分区ID
     * @return 发布时间分布数据
     */
    Map<String, Integer> getZonePublishTimeDistribution(Integer pidV2);
    
    /**
     * 查询多个分区视频发布时间分布
     * @param pidV2List 分区ID列表
     * @return 多个分区的发布时间分布数据
     */
    List<Map<String, Object>> getAllZonesPublishTimeDistribution(List<Integer> pidV2List);
    
    /**
     * 查询分区视频数量分布
     * @param pidV2 分区ID
     * @param metric 指标（playCount, likeCount, danmukuCount）
     * @param interval 梯度间隔
     * @return 视频数量分布数据
     */
    Map<String, Integer> getVideoDistribution(Integer pidV2, String metric, int interval);
}
