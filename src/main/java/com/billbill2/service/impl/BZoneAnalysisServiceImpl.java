package com.billbill2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.BZoneAnalysisMapper;
import com.billbill2.entity.RegionData;
import com.billbill2.service.BZoneAnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BZoneAnalysisServiceImpl extends ServiceImpl<BZoneAnalysisMapper, RegionData> implements BZoneAnalysisService {
    @Override
    @Transactional(rollbackFor = Exception.class) // 批量操作加事务，确保原子性
    public int batchUpsertRegionData(List<RegionData> regionDataList) {
        // 1. 前置校验：列表为空直接返回0
        if (CollectionUtils.isEmpty(regionDataList)) {
            return 0;
        }

        // 2. 提取待批量处理的所有 BVNum（去重，避免无效查询）
        List<String> bvNumList = regionDataList.stream()
                .map(RegionData::getBvNum)
                .filter(bvNum -> bvNum != null && !bvNum.trim().isEmpty()) // 过滤空 BVNum
                .distinct()
                .collect(Collectors.toList());
        System.out.println("bvNumListSize:"+bvNumList.size());

        if (CollectionUtils.isEmpty(bvNumList)) {
            throw new IllegalArgumentException("批量数据中有 BV 号为空");
        }

        //3.进行存在则更新，不存在则增加操作,如果执行成功返回影响的条数，失败返回0
        if(this.saveOrUpdateBatch(regionDataList))
            return regionDataList.size();
        else
            return 0;
    }
    
    @Override
    public Map<String, Object> getZoneStatistics(Integer pidV2) {
        Map<String, Object> statistics = new HashMap<>();
        
        // 构建查询条件
        LambdaQueryWrapper<RegionData> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(RegionData::getPidV2, pidV2);
        
        // 查询该分区的所有数据
        List<RegionData> regionDataList = this.list(queryWrapper);
        
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
        LambdaQueryWrapper<RegionData> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(RegionData::getPidV2, pidV2);
        
        // 查询该分区的数据条数
        return Math.toIntExact(this.count(queryWrapper));
    }
}
