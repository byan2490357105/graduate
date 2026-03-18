package com.billbill2.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.billbill2.entity.UpVideo;
import com.billbill2.entity.UpVideoData;
import com.billbill2.service.CrawlerService;
import com.billbill2.service.UpVideoDataService;
import com.billbill2.service.UpVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/bilibili/up-video-analysis")
public class UpVideoAnalysisController {

    @Autowired
    private UpVideoService upVideoService;

    @Autowired
    private UpVideoDataService upVideoDataService;

    @Autowired
    private CrawlerService crawlerService;

    // 存储正在执行的任务状态
    private static final Map<Long, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    // 任务状态类
    public static class TaskStatus {
        private boolean running;
        private int totalBvNum;
        private int successCount;
        private int failCount;
        private String message;
        private Long mid;

        public TaskStatus(Long mid) {
            this.mid = mid;
            this.running = true;
            this.totalBvNum = 0;
            this.successCount = 0;
            this.failCount = 0;
            this.message = "正在处理中...";
        }

        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }
        public int getTotalBvNum() { return totalBvNum; }
        public void setTotalBvNum(int totalBvNum) { this.totalBvNum = totalBvNum; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getFailCount() { return failCount; }
        public void setFailCount(int failCount) { this.failCount = failCount; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Long getMid() { return mid; }
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importUpVideoData(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long mid = Long.valueOf(request.get("mid").toString());
            log.info("接收到UP主[{}]的详细视频数据录入请求", mid);

            // 检查是否已有正在运行的任务
            if (taskStatusMap.containsKey(mid) && taskStatusMap.get(mid).isRunning()) {
                result.put("code", 400);
                result.put("msg", "该UP主的数据录入任务正在进行中，请勿重复提交");
                return ResponseEntity.ok(result);
            }

            LambdaQueryWrapper<UpVideo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(UpVideo::getMid, mid);
            List<UpVideo> upVideos = upVideoService.list(queryWrapper);

            if (upVideos == null || upVideos.isEmpty()) {
                result.put("code", 400);
                result.put("msg", "未找到该UP主的视频数据，请先爬取UP主视频列表");
                return ResponseEntity.ok(result);
            }

            List<String> bvNumList = upVideos.stream()
                    .map(UpVideo::getBvNum)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

            log.info("找到UP主[{}]的{}个BV号，开始异步处理", mid, bvNumList.size());

            // 创建任务状态
            TaskStatus taskStatus = new TaskStatus(mid);
            taskStatus.setTotalBvNum(bvNumList.size());
            taskStatusMap.put(mid, taskStatus);

            // 异步执行数据录入
            CompletableFuture.runAsync(() -> {
                try {
                    processUpVideoData(mid, bvNumList, taskStatus);
                } catch (Exception e) {
                    log.error("异步处理UP主[{}]视频数据失败: {}", mid, e.getMessage(), e);
                    taskStatus.setMessage("处理失败：" + e.getMessage());
                    taskStatus.setRunning(false);
                }
            });

            result.put("code", 200);
            result.put("msg", "数据录入任务已启动");
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("mid", mid);
            dataMap.put("totalBvNum", bvNumList.size());
            dataMap.put("status", "processing");
            result.put("data", dataMap);

        } catch (Exception e) {
            log.error("启动UP主视频数据录入任务失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "启动任务失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 异步处理UP主视频数据（分批处理，支持提前终止）
     */
    private void processUpVideoData(Long mid, List<String> bvNumList, TaskStatus taskStatus) {
        log.info("开始异步处理UP主[{}]的视频数据，共{}个BV号", mid, bvNumList.size());
        
        try {
            int batchSize = 50;
            int total = bvNumList.size();
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < total; i += batchSize) {
                int endIndex = Math.min(i + batchSize, total);
                List<String> batchBvNumList = bvNumList.subList(i, endIndex);

                log.info("处理批次：{}-{}/{}，数量：{}", i + 1, endIndex, total, batchBvNumList.size());

                try {
                    // 调用爬虫获取当前批次数据
                    List<UpVideoData> upVideoDataList = crawlerService.batchGetUpVideoDataByBvNum(batchBvNumList, mid);

                    if (upVideoDataList == null || upVideoDataList.isEmpty()) {
                        log.warn("批次{}-{}未获取到数据", i + 1, endIndex);
                        failCount += batchBvNumList.size();
                        continue;
                    }

                    // 处理数据（无论是否重复都会更新）
                    int processedCount = 0;

                    for (UpVideoData upVideoData : upVideoDataList) {
                        try {
                            // 直接保存或更新数据
                            if (upVideoDataService.saveOrUpdateVideoData(upVideoData)) {
                                successCount++;
                                processedCount++;
                            } else {
                                failCount++;
                            }
                        } catch (Exception e) {
                            failCount++;
                            log.error("保存视频数据失败: {}", e.getMessage());
                        }
                    }

                    // 更新任务状态
                    taskStatus.setSuccessCount(successCount);
                    taskStatus.setFailCount(failCount);
                    taskStatus.setMessage(String.format("正在处理... 已完成 %d/%d", i + batchBvNumList.size(), total));

                    log.info("批次{}-{}处理完成，成功处理：{}", i + 1, endIndex, processedCount);

                } catch (Exception e) {
                    log.error("处理批次{}-{}失败: {}", i + 1, endIndex, e.getMessage(), e);
                    failCount += batchBvNumList.size();
                }

                // 定期执行垃圾回收
                if ((i + batchSize) % (batchSize * 10) == 0) {
                    System.gc();
                }
            }

            // 设置最终状态
            taskStatus.setSuccessCount(successCount);
            taskStatus.setFailCount(failCount);
            taskStatus.setMessage(String.format("处理完成：总BV号数: %d，成功: %d，失败: %d", total, successCount, failCount));
            taskStatus.setRunning(false);

            log.info("UP主[{}]视频数据录入完成，成功: {}，失败: {}", mid, successCount, failCount);

        } catch (Exception e) {
            log.error("处理UP主[{}]视频数据失败: {}", mid, e.getMessage(), e);
            taskStatus.setMessage("处理失败：" + e.getMessage());
            taskStatus.setRunning(false);
        }
    }

    /**
     * 查询任务状态
     */
    @GetMapping("/import-status")
    public ResponseEntity<Map<String, Object>> getImportStatus(@RequestParam Long mid) {
        Map<String, Object> result = new HashMap<>();

        try {
            TaskStatus taskStatus = taskStatusMap.get(mid);
            
            if (taskStatus == null) {
                result.put("code", 404);
                result.put("msg", "未找到该UP主的任务");
                return ResponseEntity.ok(result);
            }

            result.put("code", 200);
            result.put("msg", "获取任务状态成功");
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("mid", taskStatus.getMid());
            dataMap.put("running", taskStatus.isRunning());
            dataMap.put("totalBvNum", taskStatus.getTotalBvNum());
            dataMap.put("successCount", taskStatus.getSuccessCount());
            dataMap.put("failCount", taskStatus.getFailCount());
            dataMap.put("message", taskStatus.getMessage());
            result.put("data", dataMap);

        } catch (Exception e) {
            log.error("获取任务状态失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "获取任务状态失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getUpStatistics(@RequestParam Long mid) {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, Object> statistics = upVideoDataService.getUpStatistics(mid);

            result.put("code", 200);
            result.put("msg", "获取统计成功");
            result.put("data", statistics);

        } catch (Exception e) {
            log.error("获取UP主统计数据失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "获取统计失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/monthly-count")
    public ResponseEntity<Map<String, Object>> getMonthlyVideoCount(@RequestParam Long mid, @RequestParam Integer year) {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<Integer, Long> monthlyCount = upVideoDataService.getMonthlyVideoCountByYear(mid, year);

            result.put("code", 200);
            result.put("msg", "获取月度视频数量成功");
            result.put("data", monthlyCount);

        } catch (Exception e) {
            log.error("获取月度视频数量失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "获取失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/trend")
    public ResponseEntity<Map<String, Object>> getYearlyTrend(@RequestParam Long mid, @RequestParam Integer year) {
        Map<String, Object> result = new HashMap<>();

        try {
            List<Map<String, Object>> trend = upVideoDataService.getYearlyTrend(mid, year);

            result.put("code", 200);
            result.put("msg", "获取年度趋势成功");
            result.put("data", trend);

        } catch (Exception e) {
            log.error("获取年度趋势失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "获取失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 提前结束任务
     */
    @PostMapping("/terminate")
    public ResponseEntity<Map<String, Object>> terminateTask(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long mid = Long.valueOf(request.get("mid").toString());
            log.info("接收到提前结束UP主[{}]视频数据录入任务的请求", mid);

            TaskStatus taskStatus = taskStatusMap.get(mid);
            
            if (taskStatus == null || !taskStatus.isRunning()) {
                result.put("code", 400);
                result.put("msg", "该UP主没有正在运行的任务");
                return ResponseEntity.ok(result);
            }

            // 标记任务为已完成
            taskStatus.setRunning(false);
            taskStatus.setMessage("任务已提前结束：用户主动终止");

            log.info("UP主[{}]的视频数据录入任务已提前结束", mid);

            result.put("code", 200);
            result.put("msg", "任务已成功提前结束");
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("mid", mid);
            dataMap.put("status", "terminated");
            result.put("data", dataMap);

        } catch (Exception e) {
            log.error("提前结束任务失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "提前结束任务失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}
