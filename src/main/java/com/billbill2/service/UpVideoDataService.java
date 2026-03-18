package com.billbill2.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.billbill2.entity.UpVideoData;

import java.util.List;
import java.util.Map;

public interface UpVideoDataService extends IService<UpVideoData> {

    boolean saveOrUpdateVideoData(UpVideoData videoData);

    List<UpVideoData> getVideosByMid(Long mid);

    Map<String, Object> getUpStatistics(Long mid);

    Map<Integer, Long> getMonthlyVideoCountByYear(Long mid, Integer year);

    List<Map<String, Object>> getYearlyTrend(Long mid, Integer year);
}
