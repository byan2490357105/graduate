package com.billbill2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.UpVideoDataMapper;
import com.billbill2.dao.UpVideoMapper;
import com.billbill2.entity.UpVideo;
import com.billbill2.entity.UpVideoData;
import com.billbill2.service.CrawlerService;
import com.billbill2.service.UpVideoDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UpVideoDataServiceImpl extends ServiceImpl<UpVideoDataMapper, UpVideoData> implements UpVideoDataService {
    private static final Logger log = LoggerFactory.getLogger(UpVideoDataServiceImpl.class);

    @Resource
    private UpVideoMapper upVideoMapper;

    @Resource
    private CrawlerService crawlerService;

    @Override
    public boolean saveOrUpdateVideoData(UpVideoData videoData) {
        LambdaQueryWrapper<UpVideoData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UpVideoData::getBv_num, videoData.getBv_num());
        UpVideoData existVideo = this.getOne(queryWrapper, false);

        if (existVideo != null) {
            // 使用条件更新，根据bv_num字段更新数据
            UpVideoData updateVideo = new UpVideoData();
            updateVideo.setTitle(videoData.getTitle());
            updateVideo.setAuthor(videoData.getAuthor());
            updateVideo.setMid(videoData.getMid());
            updateVideo.setPlayCount(videoData.getPlayCount());
            updateVideo.setDanmakuCount(videoData.getDanmakuCount());
            updateVideo.setLikeCount(videoData.getLikeCount());
            updateVideo.setCoinCount(videoData.getCoinCount());
            updateVideo.setFavoriteCount(videoData.getFavoriteCount());
            updateVideo.setShareCount(videoData.getShareCount());
            updateVideo.setPublishTime(videoData.getPublishTime());
            updateVideo.setDuration(videoData.getDuration());
            updateVideo.setVideoDesc(videoData.getVideoDesc());
            updateVideo.setTags(videoData.getTags());
            updateVideo.setCoverUrl(videoData.getCoverUrl());
            return this.update(updateVideo, queryWrapper);
        } else {
            return this.save(videoData);
        }
    }

    @Override
    public List<UpVideoData> getVideosByMid(Long mid) {
        LambdaQueryWrapper<UpVideoData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UpVideoData::getMid, mid);
        return this.list(queryWrapper);
    }

    @Override
    public Map<String, Object> getUpStatistics(Long mid) {
        List<UpVideoData> videos = getVideosByMid(mid);
        Map<String, Object> statistics = new HashMap<>();

        if (videos == null || videos.isEmpty()) {
            statistics.put("videoCount", 0);
            statistics.put("avgPlayCount", 0.0);
            statistics.put("avgDanmakuCount", 0.0);
            statistics.put("avgLikeCount", 0.0);
            statistics.put("avgCoinCount", 0.0);
            statistics.put("avgFavoriteCount", 0.0);
            statistics.put("avgShareCount", 0.0);
            return statistics;
        }

        int count = videos.size();
        long totalPlayCount = 0;
        long totalDanmakuCount = 0;
        long totalLikeCount = 0;
        long totalCoinCount = 0;
        long totalFavoriteCount = 0;
        long totalShareCount = 0;

        for (UpVideoData video : videos) {
            totalPlayCount += video.getPlayCount() != null ? video.getPlayCount() : 0;
            totalDanmakuCount += video.getDanmakuCount() != null ? video.getDanmakuCount() : 0;
            totalLikeCount += video.getLikeCount() != null ? video.getLikeCount() : 0;
            totalCoinCount += video.getCoinCount() != null ? video.getCoinCount() : 0;
            totalFavoriteCount += video.getFavoriteCount() != null ? video.getFavoriteCount() : 0;
            totalShareCount += video.getShareCount() != null ? video.getShareCount() : 0;
        }

        statistics.put("videoCount", count);
        statistics.put("avgPlayCount", (double) totalPlayCount / count);
        statistics.put("avgDanmakuCount", (double) totalDanmakuCount / count);
        statistics.put("avgLikeCount", (double) totalLikeCount / count);
        statistics.put("avgCoinCount", (double) totalCoinCount / count);
        statistics.put("avgFavoriteCount", (double) totalFavoriteCount / count);
        statistics.put("avgShareCount", (double) totalShareCount / count);

        return statistics;
    }

    @Override
    public Map<Integer, Long> getMonthlyVideoCountByYear(Long mid, Integer year) {
        List<UpVideoData> videos = getVideosByMid(mid);
        Map<Integer, Long> monthlyCount = new HashMap<>();

        for (int i = 1; i <= 12; i++) {
            monthlyCount.put(i, 0L);
        }

        for (UpVideoData video : videos) {
            if (video.getPublishTime() != null) {
                int videoYear = video.getPublishTime().getYear();
                if (videoYear == year) {
                    int month = video.getPublishTime().getMonthValue();
                    monthlyCount.put(month, monthlyCount.get(month) + 1);
                }
            }
        }

        return monthlyCount;
    }

    @Override
    public List<Map<String, Object>> getYearlyTrend(Long mid, Integer year) {
        List<UpVideoData> videos = getVideosByMid(mid);
        Map<Integer, Map<String, Object>> monthlyData = new HashMap<>();

        for (int i = 1; i <= 12; i++) {
            Map<String, Object> data = new HashMap<>();
            data.put("month", i);
            data.put("videoCount", 0L);
            data.put("totalPlayCount", 0L);
            data.put("totalLikeCount", 0L);
            data.put("totalCoinCount", 0L);
            data.put("totalFavoriteCount", 0L);
            data.put("totalDanmakuCount", 0L);
            data.put("totalShareCount", 0L);
            monthlyData.put(i, data);
        }

        for (UpVideoData video : videos) {
            if (video.getPublishTime() != null) {
                int videoYear = video.getPublishTime().getYear();
                if (videoYear == year) {
                    int month = video.getPublishTime().getMonthValue();
                    Map<String, Object> data = monthlyData.get(month);
                    data.put("videoCount", (long) data.get("videoCount") + 1);
                    data.put("totalPlayCount", (long) data.get("totalPlayCount") + (video.getPlayCount() != null ? video.getPlayCount() : 0));
                    data.put("totalLikeCount", (long) data.get("totalLikeCount") + (video.getLikeCount() != null ? video.getLikeCount() : 0));
                    data.put("totalCoinCount", (long) data.get("totalCoinCount") + (video.getCoinCount() != null ? video.getCoinCount() : 0));
                    data.put("totalFavoriteCount", (long) data.get("totalFavoriteCount") + (video.getFavoriteCount() != null ? video.getFavoriteCount() : 0));
                    data.put("totalDanmakuCount", (long) data.get("totalDanmakuCount") + (video.getDanmakuCount() != null ? video.getDanmakuCount() : 0));
                    data.put("totalShareCount", (long) data.get("totalShareCount") + (video.getShareCount() != null ? video.getShareCount() : 0));
                }
            }
        }

        return new ArrayList<>(monthlyData.values());
    }
}
