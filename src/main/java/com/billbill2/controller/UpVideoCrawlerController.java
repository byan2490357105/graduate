package com.billbill2.controller;

import com.billbill2.service.CrawlerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/bilibili/up-video-crawler")
public class UpVideoCrawlerController {

    @Autowired
    private CrawlerService crawlerService;

    // 存储正在执行的任务状态
    private static final Map<String, CrawlerTaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    // 爬虫任务状态类
    public static class CrawlerTaskStatus {
        private String taskId;
        private Long mid;
        private boolean running;
        private int currentPage;
        private int totalPages;
        private int successCount;
        private int duplicateCount;
        private String message;
        private java.util.List<Integer> failedPages;

        public CrawlerTaskStatus(String taskId, Long mid) {
            this.taskId = taskId;
            this.mid = mid;
            this.running = true;
            this.currentPage = 0;
            this.totalPages = 0;
            this.successCount = 0;
            this.duplicateCount = 0;
            this.message = "正在爬取中...";
            this.failedPages = new java.util.ArrayList<>();
        }

        public String getTaskId() { return taskId; }
        public Long getMid() { return mid; }
        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }
        public int getCurrentPage() { return currentPage; }
        public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
        public int getSuccessCount() { return successCount; }
        public void setSuccessCount(int successCount) { this.successCount = successCount; }
        public int getDuplicateCount() { return duplicateCount; }
        public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public java.util.List<Integer> getFailedPages() { return failedPages; }
        public void setFailedPages(java.util.List<Integer> failedPages) { this.failedPages = failedPages; }
    }

    /**
     * 启动UP主视频爬虫任务
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startCrawler(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long mid = Long.valueOf(request.get("mid").toString());
            Boolean usePageRange = request.containsKey("usePageRange") ? Boolean.valueOf(request.get("usePageRange").toString()) : false;
            Integer startPage = usePageRange && request.containsKey("startPage") ? Integer.valueOf(request.get("startPage").toString()) : null;
            Integer endPage = usePageRange && request.containsKey("endPage") ? Integer.valueOf(request.get("endPage").toString()) : null;
            Boolean continueOnDuplicate = request.containsKey("continueOnDuplicate") ? Boolean.valueOf(request.get("continueOnDuplicate").toString()) : false;

            log.info("接收到UP主[{}]的爬虫请求，页码范围：{}", mid, usePageRange ? startPage + "-" + endPage : "全部");

            // 生成任务ID
            String taskId = mid + "_" + System.currentTimeMillis();

            // 检查是否已有正在运行的任务
            for (CrawlerTaskStatus status : taskStatusMap.values()) {
                if (status.isRunning() && status.getMid().equals(mid)) {
                    result.put("code", 400);
                    result.put("msg", "该UP主的爬虫任务正在进行中，请勿重复提交");
                    return ResponseEntity.ok(result);
                }
            }

            // 创建任务状态
            CrawlerTaskStatus taskStatus = new CrawlerTaskStatus(taskId, mid);
            taskStatusMap.put(taskId, taskStatus);

            // 异步执行爬虫
            CompletableFuture.runAsync(() -> {
                try {
                    Map<String, Object> crawlResult;
                    if (usePageRange) {
                        crawlResult = crawlerService.crawlUpVideoData(mid, usePageRange, startPage, endPage, continueOnDuplicate);
                    } else {
                        crawlResult = crawlerService.crawlUpVideoData(mid, continueOnDuplicate);
                    }

                    // 更新任务状态
                    taskStatus.setRunning(false);
                    taskStatus.setSuccessCount(crawlResult.containsKey("successCount") ? (Integer) crawlResult.get("successCount") : 0);
                    taskStatus.setDuplicateCount(crawlResult.containsKey("duplicateCount") ? (Integer) crawlResult.get("duplicateCount") : 0);
                    taskStatus.setTotalPages(crawlResult.containsKey("totalPages") ? (Integer) crawlResult.get("totalPages") : 0);
                    
                    if (crawlResult.containsKey("failedPages")) {
                        taskStatus.setFailedPages((java.util.List<Integer>) crawlResult.get("failedPages"));
                    }
                    
                    if (crawlResult.containsKey("stopReason")) {
                        taskStatus.setMessage("爬取完成：" + crawlResult.get("stopReason"));
                    } else {
                        taskStatus.setMessage("爬取完成");
                    }

                    log.info("UP主[{}]的爬虫任务完成，成功：{}，重复：{}", mid, taskStatus.getSuccessCount(), taskStatus.getDuplicateCount());

                } catch (Exception e) {
                    log.error("UP主[{}]的爬虫任务失败: {}", mid, e.getMessage(), e);
                    taskStatus.setRunning(false);
                    taskStatus.setMessage("爬取失败：" + e.getMessage());
                }
            });

            result.put("code", 200);
            result.put("msg", "爬虫任务已启动");
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("taskId", taskId);
            dataMap.put("mid", mid);
            dataMap.put("status", "running");
            result.put("data", dataMap);

        } catch (Exception e) {
            log.error("启动爬虫任务失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "启动任务失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 查询任务状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@RequestParam String taskId) {
        Map<String, Object> result = new HashMap<>();

        try {
            CrawlerTaskStatus taskStatus = taskStatusMap.get(taskId);
            
            if (taskStatus == null) {
                result.put("code", 404);
                result.put("msg", "未找到该任务");
                return ResponseEntity.ok(result);
            }

            result.put("code", 200);
            result.put("msg", "获取任务状态成功");
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("taskId", taskStatus.getTaskId());
            dataMap.put("mid", taskStatus.getMid());
            dataMap.put("running", taskStatus.isRunning());
            dataMap.put("currentPage", taskStatus.getCurrentPage());
            dataMap.put("totalPages", taskStatus.getTotalPages());
            dataMap.put("successCount", taskStatus.getSuccessCount());
            dataMap.put("duplicateCount", taskStatus.getDuplicateCount());
            dataMap.put("failedPages", taskStatus.getFailedPages());
            dataMap.put("message", taskStatus.getMessage());
            result.put("data", dataMap);

        } catch (Exception e) {
            log.error("获取任务状态失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "获取任务状态失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 停止爬虫任务
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopCrawler(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            String taskId = request.get("taskId").toString();
            log.info("接收到停止爬虫任务请求，任务ID：{}", taskId);

            CrawlerTaskStatus taskStatus = taskStatusMap.get(taskId);
            
            if (taskStatus == null || !taskStatus.isRunning()) {
                result.put("code", 400);
                result.put("msg", "该任务没有正在运行");
                return ResponseEntity.ok(result);
            }

            // 调用服务停止爬虫
            boolean stopped = crawlerService.stopCrawler();

            if (stopped) {
                taskStatus.setRunning(false);
                taskStatus.setMessage("任务已停止");
                log.info("爬虫任务已停止，任务ID：{}", taskId);
            }

            result.put("code", 200);
            result.put("msg", "任务已成功停止");
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("taskId", taskId);
            dataMap.put("status", "stopped");
            result.put("data", dataMap);

        } catch (Exception e) {
            log.error("停止爬虫任务失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "停止任务失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}
