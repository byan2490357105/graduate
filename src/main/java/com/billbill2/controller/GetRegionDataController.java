package com.billbill2.controller;

import com.billbill2.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GetRegionDataController {

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
     * @param zoneData 分区数据，格式：{"爬取分区ID": "119", "爬取起始页": "1", "爬取结束页": "10"}
     * @return 处理结果
     */
    @PostMapping("getBZoneRegion")
    @ResponseBody
    public ResponseEntity<?> getBZoneRegionData(@RequestBody Map<String, String> zoneData) {
        try {
            log.info("接收到分区分析请求：{}", zoneData);
            
            // 提取分区信息
            String zoneId = zoneData.get("爬取分区ID");
            String startPage = zoneData.get("爬取起始页");
            String endPage = zoneData.get("爬取结束页");
            
            if (zoneId == null || startPage == null || endPage == null) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("code", 400);
                errorMap.put("msg", "缺少必要参数（爬取分区ID、爬取起始页、爬取结束页）");
                return ResponseEntity.badRequest().body(errorMap);
            }
            
            // 验证页码参数
            try {
                int startPageNum = Integer.parseInt(startPage);
                int endPageNum = Integer.parseInt(endPage);
                
                if (startPageNum < 1 || endPageNum < 1) {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("code", 400);
                    errorMap.put("msg", "页码必须大于0");
                    return ResponseEntity.badRequest().body(errorMap);
                }
                
                if (startPageNum > endPageNum) {
                    Map<String, Object> errorMap = new HashMap<>();
                    errorMap.put("code", 400);
                    errorMap.put("msg", "起始页不能大于结束页");
                    return ResponseEntity.badRequest().body(errorMap);
                }
            } catch (NumberFormatException e) {
                Map<String, Object> errorMap = new HashMap<>();
                errorMap.put("code", 400);
                errorMap.put("msg", "页码参数格式错误");
                return ResponseEntity.badRequest().body(errorMap);
            }
            
            // 执行Python脚本进行指定分区爬取
            String result = crawlerService.getRegionData(zoneId, startPage, endPage);
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
