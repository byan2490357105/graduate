package com.billbill2.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.billbill2.entity.UpVideo;
import com.billbill2.entity.Video;
import com.billbill2.service.CrawlerService;
import com.billbill2.service.UpVideoService;
import com.billbill2.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    @Autowired
    private VideoService videoService;

    @Value("${python.save-upbatch-path}")
    private String upVideoDefaultSavePath;

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

            // 获取页码范围参数
            Boolean usePageRange = request.get("usePageRange") != null ? Boolean.valueOf(request.get("usePageRange").toString()) : false;
            Integer startPage = request.get("startPage") != null ? Integer.valueOf(request.get("startPage").toString()) : null;
            Integer endPage = request.get("endPage") != null ? Integer.valueOf(request.get("endPage").toString()) : null;

            log.info("收到爬取UP主[{}]视频信息的请求，页码范围：{}", mid, usePageRange ? (startPage + "-" + endPage) : "全部");

            // 获取continueOnDuplicate参数
            Boolean continueOnDuplicate = request.get("continueOnDuplicate") != null ? Boolean.valueOf(request.get("continueOnDuplicate").toString()) : false;
            
            // 调用CrawlerService爬取数据
            Map<String, Object> crawlResult = crawlerService.crawlUpVideoData(mid, usePageRange, startPage, endPage, continueOnDuplicate);

            if ((boolean) crawlResult.get("success")) {
                // 查询数据库中该up主的视频数据
                LambdaQueryWrapper<UpVideo> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(UpVideo::getMid, mid)
                        .orderByDesc(UpVideo::getCreated)
                        .last("LIMIT 10");
                List<UpVideo> videoList = upVideoService.list(wrapper);
                
                // 将视频数据添加到返回结果中
                crawlResult.put("videoList", videoList);
                
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


    /**
     * 下载选中的视频为ZIP文件
     * @param bvNums BV号列表，逗号分隔
     * @param mid UP主ID
     * @param response HTTP响应对象
     * @return ZIP文件
     */
    @GetMapping("/download-selected")
    public void downloadSelectedVideos(
            @RequestParam("bvNums") String bvNums,
            @RequestParam("mid") Long mid,
            HttpServletResponse response) {
        try {
            if (bvNums == null || bvNums.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("参数错误：bvNums不能为空");
                return;
            }

            if (mid == null || mid <= 0) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("参数错误：mid不能为空或必须大于0");
                return;
            }

            // 解析BV号列表
            List<String> bvNumList = java.util.Arrays.asList(bvNums.split(","));
            log.info("收到下载选中视频的请求，BV号数量：{}，UP主ID：{}", bvNumList.size(), mid);

            // 调用通用下载方法
            downloadVideosByBvNums(bvNumList, mid, response);

        } catch (Exception e) {
            log.error("下载选中视频失败: {}", e.getMessage(), e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("下载失败：" + e.getMessage());
            } catch (Exception ex) {
                log.error("写入错误响应失败", ex);
            }
        }
    }

    /**
     * 通用下载视频方法
     * @param bvNums BV号列表
     * @param mid UP主ID
     * @param response HTTP响应对象
     */
    private void downloadVideosByBvNums(List<String> bvNums, Long mid, HttpServletResponse response) {
        try {
            log.info("找到{}个视频，开始下载视频", bvNums.size());

            // 下载视频并保存到数据库
            int downloadSuccessCount = 0;
            int downloadFailCount = 0;
            List<Video> downloadedVideos = new ArrayList<>();
            
            for (String bvNum : bvNums) {
                try {
                    Video video = crawlerService.downloadVideoAndGetData(bvNum, upVideoDefaultSavePath, true);
                    if (video != null && video.getIsDownload() == 1) {
                        // 保存视频信息到数据库
                        boolean saved = videoService.saveOrUpdateVideoByBvNum(video);
                        if (saved) {
                            downloadedVideos.add(video);
                            downloadSuccessCount++;
                            log.info("下载并保存视频成功：{}，进度：{}/{}", bvNum, downloadSuccessCount, bvNums.size());
                        } else {
                            log.warn("视频下载成功但保存到数据库失败：{}", bvNum);
                            downloadFailCount++;
                        }
                    } else {
                        downloadFailCount++;
                        log.warn("下载视频失败：{}", bvNum);
                    }
                } catch (Exception e) {
                    downloadFailCount++;
                    log.error("下载视频异常：{}，错误：{}", bvNum, e.getMessage());
                }
            }

            log.info("视频下载完成，成功：{}，失败：{}", downloadSuccessCount, downloadFailCount);

            if (downloadSuccessCount == 0) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("所有视频下载失败，无法打包");
                return;
            }

            log.info("开始打包下载，共{}个视频", downloadSuccessCount);

            // 设置响应头
            String currentDate = java.time.LocalDate.now().toString();
            String fileName = mid + "_" + currentDate + ".zip";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());

            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFileName + "\"");

            // 获取输出流并打包视频
            try (OutputStream outputStream = response.getOutputStream()) {
                boolean success = videoService.packageVideosToZip(bvNums, outputStream, mid);
                if (!success) {
                    log.warn("打包视频文件失败，没有可下载的视频文件");
                    // 注意：此时不能再调用getWriter()，因为outputStream已经被获取
                } else {
                    log.info("视频文件打包下载完成");
                }
            }

        } catch (Exception e) {
            log.error("下载视频失败: {}", e.getMessage(), e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("下载失败：" + e.getMessage());
            } catch (Exception ex) {
                log.error("写入错误响应失败", ex);
            }
        }
    }
}
