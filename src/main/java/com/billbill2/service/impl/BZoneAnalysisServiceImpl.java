package com.billbill2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.BZoneDataMapper;
import com.billbill2.entity.NewRegionData;
import com.billbill2.entity.Video;
import com.billbill2.service.BZoneAnalysisService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BZoneAnalysisServiceImpl extends ServiceImpl<BZoneDataMapper, NewRegionData> implements BZoneAnalysisService {
    @Override
    public Map<String, Object> getZoneStatistics(Integer pidV2) {
        Map<String, Object> statistics = new HashMap<>();
        
        // 构建查询条件
        LambdaQueryWrapper<NewRegionData> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(NewRegionData::getPidV2, pidV2);
        
        // 查询该分区的所有数据
        List<NewRegionData> regionDataList = this.list(queryWrapper);
        
        if (CollectionUtils.isEmpty(regionDataList)) {
            statistics.put("totalCount", 0);
            statistics.put("avgLikeCount", 0);
            statistics.put("avgPlayCount", 0);
            statistics.put("avgDanmukuCount", 0);
            return statistics;
        }
        
        // 计算总条数
        int totalCount = regionDataList.size();
        
        // 计算平均点赞数
        double avgLikeCount = regionDataList.stream()
                .mapToLong(data -> data.getLikeCount() != null ? data.getLikeCount() : 0)
                .average()
                .orElse(0.0);
        
        // 计算平均播放量
        double avgPlayCount = regionDataList.stream()
                .mapToLong(data -> data.getPlayCount() != null ? data.getPlayCount() : 0)
                .average()
                .orElse(0.0);
        
        // 计算平均弹幕数
        double avgDanmukuCount = regionDataList.stream()
                .mapToLong(data -> data.getDanmukuCount() != null ? data.getDanmukuCount() : 0)
                .average()
                .orElse(0.0);
        
        // 设置统计结果
        statistics.put("totalCount", totalCount);
        statistics.put("avgLikeCount", Math.round(avgLikeCount));
        statistics.put("avgPlayCount", Math.round(avgPlayCount));
        statistics.put("avgDanmukuCount", Math.round(avgDanmukuCount));
        
        return statistics;
    }
    
    @Override
    public List<Map<String, Object>> getAllZonesStatistics(List<Integer> pidV2List) {
        List<Map<String, Object>> statisticsList = new ArrayList<>();
        
        if (CollectionUtils.isEmpty(pidV2List)) {
            return statisticsList;
        }
        
        // 遍历每个分区ID，查询统计信息
        for (Integer pidV2 : pidV2List) {
            Map<String, Object> statistics = getZoneStatistics(pidV2);
            statistics.put("pidV2", pidV2);
            statisticsList.add(statistics);
        }
        
        return statisticsList;
    }
    
    @Override
    public int getZoneDataCount(Integer pidV2) {
        // 构建查询条件
        LambdaQueryWrapper<NewRegionData> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(NewRegionData::getPidV2, pidV2);
        
        // 查询该分区的数据条数
        return Math.toIntExact(this.count(queryWrapper));
    }
}
