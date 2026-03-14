package com.billbill2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.BZoneDataMapper;
import com.billbill2.entity.NewRegionData;
import com.billbill2.service.BZoneAnalysisService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    
    @Override
    public List<NewRegionData> getZoneTopVideos(Integer pidV2, String sortField, int limit) {
        // 构建查询条件
        LambdaQueryWrapper<NewRegionData> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(NewRegionData::getPidV2, pidV2);
        
        // 根据排序字段进行排序
        switch (sortField) {
            case "playCount":
                queryWrapper.orderByDesc(NewRegionData::getPlayCount);
                break;
            case "likeCount":
                queryWrapper.orderByDesc(NewRegionData::getLikeCount);
                break;
            case "danmukuCount":
                queryWrapper.orderByDesc(NewRegionData::getDanmukuCount);
                break;
            default:
                queryWrapper.orderByDesc(NewRegionData::getPlayCount);
                break;
        }
        
        // 限制返回数量
        queryWrapper.last("LIMIT " + limit);
        
        // 查询TOP N视频
        return this.list(queryWrapper);
    }
    
    @Override
    public List<Map<String, Object>> getZoneVideoQualityScore(Integer pidV2, int limit) {
        List<Map<String, Object>> qualityScoreList = new ArrayList<>();
        
        // 构建查询条件
        LambdaQueryWrapper<NewRegionData> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(NewRegionData::getPidV2, pidV2);
        
        // 查询该分区的所有数据
        List<NewRegionData> regionDataList = this.list(queryWrapper);
        
        if (CollectionUtils.isEmpty(regionDataList)) {
            return qualityScoreList;
        }
        
        // 计算每个视频的质量评分
        // 权重：播放量40%，点赞数35%，弹幕数25%
        for (NewRegionData data : regionDataList) {
            Map<String, Object> qualityScore = new HashMap<>();
            
            Long playCount = data.getPlayCount() != null ? data.getPlayCount() : 0L;
            Long likeCount = data.getLikeCount() != null ? data.getLikeCount() : 0L;
            Long danmukuCount = data.getDanmukuCount() != null ? data.getDanmukuCount() : 0L;
            
            // 计算质量评分（归一化处理）
            double score = playCount * 0.4 + likeCount * 0.35 + danmukuCount * 0.25;
            
            qualityScore.put("bvNum", data.getBvNum());
            qualityScore.put("name", data.getName());
            qualityScore.put("upName", data.getUpName());
            qualityScore.put("playCount", playCount);
            qualityScore.put("likeCount", likeCount);
            qualityScore.put("danmukuCount", danmukuCount);
            qualityScore.put("score", Math.round(score));
            qualityScore.put("duration", data.getDuration());
            qualityScore.put("publishTime", data.getPublishTime());
            
            qualityScoreList.add(qualityScore);
        }
        
        // 按质量评分降序排序
        qualityScoreList.sort((a, b) -> {
            Double scoreA = (Double) a.get("score");
            Double scoreB = (Double) b.get("score");
            return scoreB.compareTo(scoreA);
        });
        
        // 返回TOP N
        return qualityScoreList.stream().limit(limit).collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public List<Map<String, Object>> getZoneHighLikeRateVideos(Integer pidV2, int limit) {
        List<Map<String, Object>> highLikeRateList = new ArrayList<>();
        
        // 构建查询条件
        LambdaQueryWrapper<NewRegionData> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(NewRegionData::getPidV2, pidV2);
        
        // 查询该分区的所有数据
        List<NewRegionData> regionDataList = this.list(queryWrapper);
        
        if (CollectionUtils.isEmpty(regionDataList)) {
            return highLikeRateList;
        }
        
        // 计算每个视频的点赞率
        for (NewRegionData data : regionDataList) {
            Long playCount = data.getPlayCount() != null ? data.getPlayCount() : 0L;
            Long likeCount = data.getLikeCount() != null ? data.getLikeCount() : 0L;
            
            // 计算点赞率（点赞数/播放量）
            double likeRate = playCount > 0 ? (likeCount * 100.0) / playCount : 0.0;
            
            Map<String, Object> videoInfo = new HashMap<>();
            videoInfo.put("bvNum", data.getBvNum());
            videoInfo.put("name", data.getName());
            videoInfo.put("upName", data.getUpName());
            videoInfo.put("playCount", playCount);
            videoInfo.put("likeCount", likeCount);
            videoInfo.put("likeRate", Math.round(likeRate * 100) / 100.0);
            videoInfo.put("duration", data.getDuration());
            videoInfo.put("publishTime", data.getPublishTime());
            
            highLikeRateList.add(videoInfo);
        }
        
        // 按点赞率降序排序
        highLikeRateList.sort((a, b) -> {
            Double rateA = (Double) a.get("likeRate");
            Double rateB = (Double) b.get("likeRate");
            return rateB.compareTo(rateA);
        });
        
        // 返回TOP N
        return highLikeRateList.stream().limit(limit).collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public List<Map<String, Object>> getZoneHighDanmakuRateVideos(Integer pidV2, int limit) {
        List<Map<String, Object>> highDanmakuRateList = new ArrayList<>();
        
        // 构建查询条件
        LambdaQueryWrapper<NewRegionData> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(NewRegionData::getPidV2, pidV2);
        
        // 查询该分区的所有数据
        List<NewRegionData> regionDataList = this.list(queryWrapper);
        
        if (CollectionUtils.isEmpty(regionDataList)) {
            return highDanmakuRateList;
        }
        
        // 计算每个视频的弹幕活跃度
        for (NewRegionData data : regionDataList) {
            Long playCount = data.getPlayCount() != null ? data.getPlayCount() : 0L;
            Long danmukuCount = data.getDanmukuCount() != null ? data.getDanmukuCount() : 0L;
            
            // 计算弹幕活跃度（弹幕数/播放量）
            double danmakuRate = playCount > 0 ? (danmukuCount * 100.0) / playCount : 0.0;
            
            Map<String, Object> videoInfo = new HashMap<>();
            videoInfo.put("bvNum", data.getBvNum());
            videoInfo.put("name", data.getName());
            videoInfo.put("upName", data.getUpName());
            videoInfo.put("playCount", playCount);
            videoInfo.put("danmukuCount", danmukuCount);
            videoInfo.put("danmakuRate", Math.round(danmakuRate * 100) / 100.0);
            videoInfo.put("duration", data.getDuration());
            videoInfo.put("publishTime", data.getPublishTime());
            
            highDanmakuRateList.add(videoInfo);
        }
        
        // 按弹幕活跃度降序排序
        highDanmakuRateList.sort((a, b) -> {
            Double rateA = (Double) a.get("danmakuRate");
            Double rateB = (Double) b.get("danmakuRate");
            return rateB.compareTo(rateA);
        });
        
        // 返回TOP N
        return highDanmakuRateList.stream().limit(limit).collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public Map<String, Integer> getZonePublishTimeDistribution(Integer pidV2) {
        Map<String, Integer> distribution = new HashMap<>();
        
        // 构建查询条件
        LambdaQueryWrapper<NewRegionData> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(NewRegionData::getPidV2, pidV2);
        
        // 查询该分区的所有数据
        List<NewRegionData> regionDataList = this.list(queryWrapper);
        
        if (CollectionUtils.isEmpty(regionDataList)) {
            return distribution;
        }
        
        // 初始化时间区间（24小时）
        for (int i = 0; i < 24; i++) {
            String hour = String.format("%02d:00", i);
            distribution.put(hour, 0);
        }
        
        // 统计发布时间分布
        for (NewRegionData data : regionDataList) {
            LocalDateTime publishTime = data.getPublishTime();
            if (publishTime != null) {
                int hour = publishTime.getHour();
                String hourKey = String.format("%02d:00", hour);
                distribution.put(hourKey, distribution.get(hourKey) + 1);
            }
        }
        
        return distribution;
    }
    
    @Override
    public List<Map<String, Object>> getAllZonesPublishTimeDistribution(List<Integer> pidV2List) {
        List<Map<String, Object>> distributionList = new ArrayList<>();
        
        if (CollectionUtils.isEmpty(pidV2List)) {
            return distributionList;
        }
        
        // 遍历每个分区ID，查询发布时间分布
        for (Integer pidV2 : pidV2List) {
            Map<String, Integer> distribution = getZonePublishTimeDistribution(pidV2);
            Map<String, Object> result = new HashMap<>();
            result.put("pidV2", pidV2);
            result.put("distribution", distribution);
            
            // 分析发布时间分布，找出发布数量最多的时间段
            String peakHour = findPeakPublishHour(distribution);
            result.put("peakHour", peakHour);
            
            distributionList.add(result);
        }
        
        return distributionList;
    }
    
    /**
     * 找出发布数量最多的时间段
     * @param distribution 发布时间分布
     * @return 发布数量最多的时间段
     */
    private String findPeakPublishHour(Map<String, Integer> distribution) {
        if (distribution == null || distribution.isEmpty()) {
            return "无数据";
        }
        
        String peakHour = "";
        int maxCount = 0;
        
        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                peakHour = entry.getKey();
            }
        }
        
        return peakHour;
    }
    
    @Override
    public Map<String, Integer> getVideoDistribution(Integer pidV2, String metric, int interval) {
        Map<String, Integer> distribution = new HashMap<>();
        
        // 构建查询条件
        LambdaQueryWrapper<NewRegionData> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(NewRegionData::getPidV2, pidV2);
        
        // 查询该分区的所有数据
        List<NewRegionData> regionDataList = this.list(queryWrapper);
        
        if (CollectionUtils.isEmpty(regionDataList)) {
            return distribution;
        }
        
        // 统计不同梯度的视频数量
        for (NewRegionData data : regionDataList) {
            long value = 0;
            
            // 根据指标获取对应的值
            if ("playCount".equals(metric)) {
                value = data.getPlayCount() != null ? data.getPlayCount() : 0;
            } else if ("likeCount".equals(metric)) {
                value = data.getLikeCount() != null ? data.getLikeCount() : 0;
            } else if ("danmukuCount".equals(metric)) {
                value = data.getDanmukuCount() != null ? data.getDanmukuCount() : 0;
            }
            
            // 计算所属梯度
            long rangeStart = (value / interval) * interval;
            long rangeEnd = rangeStart + interval - 1;
            String rangeKey = rangeStart + "-" + rangeEnd;
            
            // 更新统计结果
            distribution.put(rangeKey, distribution.getOrDefault(rangeKey, 0) + 1);
        }
        
        return distribution;
    }
}
