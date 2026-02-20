package com.billbill2.controller;

import com.billbill2.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class BZoneAnalysisController {

    private final CrawlerService crawlerService;

    /**
     * 跳转到B站分区分析页面
     * @return 页面视图
     */
    @GetMapping("toBZoneAnalysis")
    public String toBZoneAnalysis() {
        return "BZoneAnalysis";
    }

    /**
     * 跳转到B站分区统计页面
     * @return 页面视图
     */
    @GetMapping("toZoneStatistics")
    public String toZoneStatistics() {
        return "ZoneStatistics";
    }

    /**
     * 调用爬虫执行获取分区数据
     * @param zoneData 分区数据，格式：{"分区名": "鬼畜", "ID": "119", "duration": "5"}
     * @return 处理结果
     */
    @PostMapping("getBZoneRegion")
    @ResponseBody
    public ResponseEntity<?> getBZoneRegionData(@RequestBody Map<String, String> zoneData) {
        try {
            log.info("接收到分区分析请求：{}", zoneData);
            
            // 提取分区信息
            String zoneName = zoneData.get("分区名");
            String zoneId = zoneData.get("ID");
            String duration = zoneData.get("duration");
            
            if (zoneName == null || zoneId == null) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("code", 400);
                errorMap.put("msg", "缺少分区信息");
                return ResponseEntity.badRequest().body(errorMap);
            }
            
            // 执行Python脚本进行指定分区爬取
            int durationInt = 1; // 默认1分钟
            if (duration != null) {
                try {
                    durationInt = Integer.parseInt(duration);
                } catch (NumberFormatException e) {
                    log.warn("无效的持续时间参数：{}，使用默认值1分钟", duration);
                }
            }
            
            String result = crawlerService.getZoneIDDataWithDuration(zoneId,"1","10", durationInt);
            log.info("Python脚本执行结果：{}", result);
            
            // 构建成功响应
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("code", 200);
            resultMap.put("msg", "分区数据提交成功，已开始爬取数据");
            resultMap.put("data", zoneData);
            resultMap.put("pythonResult", result);
            
            return ResponseEntity.ok(resultMap);
            
        } catch (Exception e) {
            log.error("分区分析失败：{}", e.getMessage(), e);
            
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("code", 500);
            errorMap.put("msg", "服务器内部错误：" + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorMap);
        }
    }

    /**
     * 提前结束爬虫
     * @return 处理结果
     */
    @PostMapping("stopCrawler")
    @ResponseBody
    public ResponseEntity<?> stopCrawler() {
        try {
            log.info("接收到停止爬虫请求");
            
            // 调用服务停止爬虫
            boolean stopped = crawlerService.stopCrawler();
            
            // 构建响应
            Map<String, Object> resultMap = new HashMap<>();
            if (stopped) {
                resultMap.put("code", 200);
                resultMap.put("msg", "爬虫已成功停止");
            } else {
                resultMap.put("code", 200);
                resultMap.put("msg", "当前没有运行中的爬虫");
            }
            
            return ResponseEntity.ok(resultMap);
            
        } catch (Exception e) {
            log.error("停止爬虫失败：{}", e.getMessage(), e);
            
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("code", 500);
            errorMap.put("msg", "服务器内部错误：" + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorMap);
        }
    }

    /**
     * 查询爬虫是否正在执行
     * @return 处理结果
     */
    @GetMapping("isCrawlerRunning")
    @ResponseBody
    public ResponseEntity<?> isCrawlerRunning() {
        try {
            // 调用服务查询爬虫状态
            boolean running = crawlerService.isCrawlerRunning();
            
            // 构建响应
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("code", 200);
            resultMap.put("running", running);
            
            return ResponseEntity.ok(resultMap);
            
        } catch (Exception e) {
            log.error("查询爬虫状态失败：{}", e.getMessage(), e);
            
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("code", 500);
            errorMap.put("msg", "服务器内部错误：" + e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorMap);
        }
    }

}
