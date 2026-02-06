package com.billbill2.controller;

import com.billbill2.Util.FileOperate;
import com.billbill2.entity.Comment;
import com.billbill2.entity.Video;
import com.billbill2.service.CrawlerService;
import com.billbill2.service.VideoService;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequestMapping("/api/bilibili/video")
@RequiredArgsConstructor
public class ResponsePyVideo {
    private final CrawlerService crawlerService;

    @Value("${python.save-path}")
    private String defaultSavePath;

    @Autowired
    private VideoService videoService;

    /**
     * 支持单条/批量获取视频（兼容单个BV号和多个BV号数组）
     * @param request 请求体：{ "bvNums": ["BV1xxxxx", "BV2xxxxx"], "savePath": "xxx" } 或 { "bvNum": "BV1xxxxx", "savePath": "xxx" }
     * @return 批量处理结果
     */
    @PostMapping("/getvideo")
    @ResponseBody
    public Map<String, Object> getVideo(@RequestBody Map<String, Object> request) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Map<String, Object>> detailResults = new ArrayList<>();

        try {
            // 1. 解析参数：兼容单个BV号和批量BV号
            List<String> bvNums = new ArrayList<>();
            String savePath = request.get("savePath").toString();
            if(savePath==null || savePath=="")
                savePath=defaultSavePath;

            // 处理批量BV号（数组）
            if (request.containsKey("bvNums") && request.get("bvNums") instanceof List) {
                bvNums = (List<String>) request.get("bvNums");
            }

            // 2. 参数校验
            if (bvNums.isEmpty()) {
                resultMap.put("code", 400);
                resultMap.put("msg", "BV号不能为空");
                resultMap.put("data", null);
                resultMap.put("detail", detailResults);
                return resultMap;
            }

            // 3. 循环处理每个BV号
            int successCount = 0;
            int failCount = 0;
            for (String bvNum : bvNums) {
                Map<String, Object> singleResult = new HashMap<>();
                singleResult.put("bvNum", bvNum);
                System.out.println("bvNums-bvNum:"+bvNum);

                try {
                    Video videoData = crawlerService.getVideoData(bvNum, savePath);
                    // 下载视频
                    Long fileSize = crawlerService.donwloadVideoLocal(bvNum, videoData.getName(), savePath);
                    videoData.setFileSize(fileSize);

                    if (fileSize > 0L) {
                        successCount++;
                        singleResult.put("code", 200);
                        singleResult.put("msg", "下载成功");
                        singleResult.put("data", videoData);
                        // 入库
                        videoData.setIsDownload(1);
                        videoService.saveOrUpdateVideoByBvNum(videoData);
                    } else {
                        failCount++;
                        singleResult.put("code", 500);
                        singleResult.put("msg", "下载失败（文件大小为0）");
                        singleResult.put("data", null);
                        videoData.setIsDownload(0);
                        videoData.setFileSize(0L);
                    }
                } catch (Exception e) {
                    failCount++;
                    singleResult.put("code", 500);
                    singleResult.put("msg", "处理失败：" + e.getMessage());
                    singleResult.put("data", null);
                }
                detailResults.add(singleResult);
            }

            // 4. 组装整体结果
            resultMap.put("code", 200);
            resultMap.put("msg", String.format("批量处理完成：成功%d条，失败%d条", successCount, failCount));
            resultMap.put("successCount", successCount);
            resultMap.put("failCount", failCount);
            resultMap.put("detail", detailResults);

        } catch (Exception e) {
            resultMap.put("code", 500);
            resultMap.put("msg", "批量处理异常：" + e.getMessage());
            resultMap.put("successCount", 0);
            resultMap.put("failCount", 0);
            resultMap.put("detail", detailResults);
        }
        return resultMap;
    }


    /**
     * Map转Video实体（适配JSON自动转换）
     */
    private Video mapToVideo(Map<String, Object> videoMap) {
        Video video = new Video();
        video.setBvNum(videoMap.get("bvNum") != null ? videoMap.get("bvNum").toString() : null);
        video.setName(videoMap.get("name") != null ? videoMap.get("name").toString() : null);
        video.setFileSize(videoMap.get("fileSize") != null ? Long.parseLong(videoMap.get("fileSize").toString()) : 0L);
        video.setIsDownload(videoMap.get("isDownload") != null ? Integer.parseInt(videoMap.get("isDownload").toString()) : 0);
        // 补充其他Video字段的映射...
        return video;
    }

    // 清空视频数据库接口（保持不变）
    @PostMapping("/clear")
    @ResponseBody
    public Map<String, Object> clearVideoDatabase(){
        Map<String,Object> resultMap=new HashMap<>();
        try{
            System.out.println("开始清空视频数据库...");

            // 调用 service 方法清空视频数据库
            boolean is_clear=videoService.clearVideoTableAndResetId();
            System.out.println("清空数据库结果：" + is_clear);

            // 删除视频保存路径里的所有文件
            File saveDir = new File(defaultSavePath);
            if (saveDir.exists() && saveDir.isDirectory()) {
                deleteDirectory(saveDir);
                System.out.println("删除视频文件成功：" + defaultSavePath);
            }

            resultMap.put("code",200);
            resultMap.put("msg", "视频数据库清空成功");
            resultMap.put("success", is_clear);
        }catch(Exception e){
            System.out.println("清空视频数据库失败，错误信息：" + e.getMessage());
            e.printStackTrace();
            resultMap.put("code",500);
            resultMap.put("msg", "执行失败：" + e.getMessage());
            resultMap.put("data", null);
        }
        return resultMap;
    }

    // 递归删除目录及其所有文件（保持不变）
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

}

