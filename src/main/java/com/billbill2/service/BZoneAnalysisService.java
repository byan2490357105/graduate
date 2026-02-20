package com.billbill2.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.billbill2.entity.RegionData;

import java.util.List;
import java.util.Map;

public interface BZoneAnalysisService extends IService<RegionData> {
    /**
     * 批量获取B站指定分区的视频播放数据
     * @param regionDataList 视频数据列表
     * @return 成功插入的数量
     * 实现增量更新 有则不变 无则插入 避免触发唯一索引报错
     */
    int batchUpsertRegionData(List<RegionData> regionDataList);
    
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
}
