package com.billbill2.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.billbill2.entity.UpVideo;
import com.billbill2.service.UpVideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/bilibili/upvideo-data")
public class UpVideoDataController {

    @Autowired
    private UpVideoService upVideoService;

    /**
     * 接收Python脚本发送的视频数据
     * @param request 包含视频数据的请求体
     * @return 保存结果
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveUpVideoData(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long mid = Long.valueOf(request.get("mid").toString());
            Integer page = Integer.valueOf(request.get("page").toString());
            List<Map<String, Object>> videoDataList = (List<Map<String, Object>>) request.get("videos");

            if (videoDataList == null || videoDataList.isEmpty()) {
                result.put("code", 400);
                result.put("msg", "视频数据不能为空");
                return ResponseEntity.ok(result);
            }

            log.info("接收到UP主[{}]第{}页的{}条视频数据", mid, page, videoDataList.size());

            int successCount = 0;
            int duplicateCount = 0;
            List<String> duplicateBvNums = new ArrayList<>();

            for (Map<String, Object> videoData : videoDataList) {
                String bvNum = (String) videoData.get("bvNum");

                // 检查BV号是否已存在
                LambdaQueryWrapper<UpVideo> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(UpVideo::getBvNum, bvNum);
                UpVideo existingVideo = upVideoService.getOne(wrapper);

                if (existingVideo != null) {
                    log.info("BV号[{}]已存在，跳过保存", bvNum);
                    duplicateCount++;
                    duplicateBvNums.add(bvNum);
                    continue;
                }

                // 转换为UpVideo实体
                UpVideo upVideo = convertToUpVideo(videoData);

                // 保存到数据库
                if (upVideoService.save(upVideo)) {
                    successCount++;
                }
            }

            result.put("code", 200);
            result.put("msg", "保存成功");
            Map<String, Object> data = new HashMap<>();
            data.put("successCount", successCount);
            data.put("duplicateCount", duplicateCount);
            data.put("duplicateBvNums", duplicateBvNums);
            result.put("data", data);

            log.info("保存完成：成功{}条，重复{}条", successCount, duplicateCount);

        } catch (Exception e) {
            log.error("保存视频数据失败: {}", e.getMessage(), e);
            result.put("code", 500);
            result.put("msg", "保存失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 将Map转换为UpVideo实体
     */
    private UpVideo convertToUpVideo(Map<String, Object> videoData) {
        UpVideo upVideo = new UpVideo();

        upVideo.setTypeid(getIntValue(videoData.get("typeid")));
        upVideo.setTitle(getStringValue(videoData.get("title")));
        upVideo.setAuthor(getStringValue(videoData.get("author")));
        upVideo.setMid(getLongValue(videoData.get("mid")));
        upVideo.setAid(getLongValue(videoData.get("aid")));
        upVideo.setBvNum(getStringValue(videoData.get("bvNum")));

        // 处理时间戳
        Object createdObj = videoData.get("created");
        if (createdObj != null) {
            long timestamp = getLongValue(createdObj);
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamp),
                    ZoneId.systemDefault()
            );
            upVideo.setCreated(dateTime);
        }

        upVideo.setLength(getStringValue(videoData.get("length")));

        return upVideo;
    }

    private Integer getIntValue(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private Long getLongValue(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private String getStringValue(Object obj) {
        if (obj == null) return "";
        return obj.toString();
    }
}
