package com.billbill2.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.billbill2.entity.UpVideo;
import com.billbill2.service.CrawlerService;
import com.billbill2.service.UpVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bilibili/upvideo")
public class UpVideoController {

    @Autowired
    private CrawlerService crawlerService;

    @Autowired
    private UpVideoService upVideoService;

    /**
     * 爬取UP主视频信息
     * @param request 包含mid的请求体
     * @return 爬取结果
     */
    @PostMapping("/crawl")
    public ResponseEntity<Map<String, Object>> crawlUpVideo(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long mid = Long.valueOf(request.get("mid").toString());

            if (mid == null || mid <= 0) {
                result.put("code", 400);
                result.put("msg", "参数错误：mid不能为空或必须大于0");
                return ResponseEntity.ok(result);
            }

            log.info("收到爬取UP主[{}]视频信息的请求", mid);

            // 调用CrawlerService爬取数据
            Map<String, Object> crawlResult = crawlerService.crawlUpVideoData(mid);

            if ((boolean) crawlResult.get("success")) {
                result.put("code", 200);
                result.put("msg", "爬取成功");
                result.put("data", crawlResult);
            } else {
                result.put("code", 500);
                result.put("msg", "爬取失败，未获取到数据");
            }

        } catch (NumberFormatException e) {
            result.put("code", 400);
            result.put("msg", "参数格式错误：mid必须是数字");
        } catch (Exception e) {
            log.error("爬取UP主视频信息失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "爬取失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 根据MID获取UP主视频列表
     * @param mid UP主ID
     * @param limit 限制条数（默认10条）
     * @return 视频列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getUpVideoList(
            @RequestParam("mid") Long mid,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (mid == null || mid <= 0) {
                result.put("code", 400);
                result.put("msg", "参数错误：mid不能为空或必须大于0");
                return ResponseEntity.ok(result);
            }

            LambdaQueryWrapper<UpVideo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UpVideo::getMid, mid)
                    .orderByDesc(UpVideo::getCreated)
                    .last("LIMIT " + limit);
            List<UpVideo> videoList = upVideoService.list(wrapper);

            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", videoList);

        } catch (Exception e) {
            log.error("查询UP主视频列表失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "查询失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 检查BV号是否存在
     * @param bvNum BV号
     * @return 是否存在
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Object>> checkBvExists(@RequestParam("bvNum") String bvNum) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (bvNum == null || bvNum.isEmpty()) {
                result.put("code", 400);
                result.put("msg", "参数错误：bvNum不能为空");
                return ResponseEntity.ok(result);
            }

            LambdaQueryWrapper<UpVideo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UpVideo::getBvNum, bvNum);
            boolean exists = upVideoService.count(wrapper) > 0;

            result.put("code", 200);
            result.put("msg", "查询成功");
            result.put("data", exists);

        } catch (Exception e) {
            log.error("检查BV号是否存在失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "查询失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }
}
