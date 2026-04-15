package com.billbill2.service.impl;

import com.billbill2.DTO.CommentRequestDTO;
import com.billbill2.entity.Video;
import com.billbill2.entity.UpVideoData;
import com.billbill2.service.CrawlerService;
import com.billbill2.Util.FileOperate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


@Slf4j
@Service
public class CrawlerServiceImpl implements CrawlerService {
    @Value("${python.script-path}")
    private String pythonScriptPath;

    @Value("${python.exec-path}")
    private String pythonExecPath;
    
    @Value("${python.save-path}")
    private String defaultSavePath;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("downloadExecutor")
    private Executor downloadExecutor;

    @Autowired
    @Qualifier("crawlExecutor")
    private Executor crawlExecutor;
    
    /**
     * 存储运行中的Python进程，用于后续停止操作
     */
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    
    /**
     * 存储当前运行的爬虫线程，用于后续停止操作
     */
    private volatile Thread crawlerThread = null;
    
    /**
     * 标记爬虫是否正在执行
     */
    private volatile boolean isCrawlerRunning = false;

    private CompletableFuture<String> executePythonScriptAsync(String scriptName, List<String> args) {
        return CompletableFuture.supplyAsync(() -> {
            String fullScriptPath = new File(pythonScriptPath, scriptName).getAbsolutePath();
            
            List<String> command = new ArrayList<>();
            command.add(pythonExecPath);
            command.add(fullScriptPath);
            command.addAll(args);
            
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.environment().put("PYTHONIOENCODING", "UTF-8");
            processBuilder.redirectErrorStream(true);

            StringBuilder output = new StringBuilder();
            Process process = null;
            try {
                process = processBuilder.start();
                // 存储进程引用
                String processKey = scriptName + "_" + String.join("_", args);
                runningProcesses.put(processKey, process);
                
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                int exitCode = process.waitFor();
                // 进程结束后移除引用
                runningProcesses.remove(processKey);
                
                if (exitCode == 0) {
                    return output.toString().trim();
                } else {
                    log.error("脚本执行失败（退出码：{}）：{}", exitCode, output);
                    return "脚本执行失败（退出码：" + exitCode + "）：\n" + output;
                }
            } catch (IOException | InterruptedException e) {
                // 如果是被中断，返回中断信息
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    return "脚本执行被中断：" + e.getMessage();
                }
                log.error("调用脚本异常：{}", e.getMessage(), e);
                return "调用脚本异常：" + e.getMessage();
            } finally {
                // 确保进程引用被移除
                if (process != null) {
                    String processKey = scriptName + "_" + String.join("_", args);
                    runningProcesses.remove(processKey);
                }
            }
        }, crawlExecutor);
    }

    private String executePythonScript(String scriptName, List<String> args) {
        String fullScriptPath = new File(pythonScriptPath, scriptName).getAbsolutePath();
        
        List<String> command = new ArrayList<>();
        command.add(pythonExecPath);
        command.add(fullScriptPath);
        command.addAll(args);
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().put("PYTHONIOENCODING", "UTF-8");
        processBuilder.redirectErrorStream(true);

        StringBuilder output = new StringBuilder();
        Process process = null;
        try {
            process = processBuilder.start();
            // 存储进程引用
            String processKey = scriptName + "_" + String.join("_", args);
            runningProcesses.put(processKey, process);
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            // 进程结束后移除引用
            runningProcesses.remove(processKey);
            
            if (exitCode == 0) {
                return output.toString().trim();
            } else {
                log.error("脚本执行失败（退出码：{}）：{}", exitCode, output);
                return "脚本执行失败（退出码：" + exitCode + "）：\n" + output;
            }
        } catch (IOException | InterruptedException e) {
            log.error("调用脚本异常：{}", e.getMessage(), e);
            return "调用脚本异常：" + e.getMessage();
        } finally {
            // 确保进程引用被移除
            if (process != null) {
                String processKey = scriptName + "_" + String.join("_", args);
                runningProcesses.remove(processKey);
            }
        }
    }

    @Override
    public String getCommendData(CommentRequestDTO request) {
        List<String> args = new ArrayList<>();
        args.add(request.getBvNum());
        args.add(request.getStartPage().toString());
        args.add(request.getEndPage().toString());
        
        String result = executePythonScript("getComment.py", args);
        return "爬取并入库结果：\n" + result;
    }

    @Override
    public String batchGetCommentByAid(List<String> args) {
        log.info("开始批量爬取评论，参数数量：{}", args.size());
        
        // 标记爬虫正在执行
        isCrawlerRunning = true;
        log.info("设置爬虫状态为运行中");
        
        // 异步执行脚本
        CompletableFuture.runAsync(() -> {
            // 再次标记爬虫正在执行，确保状态正确
            isCrawlerRunning = true;
            log.info("异步任务开始，再次设置爬虫状态为运行中");
            
            // 保存当前线程引用
            crawlerThread = Thread.currentThread();
            
            try {
                log.info("开始执行批量爬虫，参数数量：{}", args.size());
                String result = executePythonScript("getCommentByAid.py", args);
                log.info("批量爬虫执行完成，结果：{}", result);
            } catch (Exception e) {
                log.error("执行批量爬虫失败：{}", e.getMessage(), e);
            } finally {
                // 清除线程引用
                crawlerThread = null;
                // 标记爬虫执行完成
                isCrawlerRunning = false;
                log.info("设置爬虫状态为停止");
            }
        }, crawlExecutor);
        
        // 返回一个提示信息，实际结果会在后台执行
        return "批量爬取任务已启动，请等待执行完成。\n参数数量：" + args.size();
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 2000))
    public Long donwloadVideoLocal(String bvNum, String name, String savePath) {
        String fileName = "【" + FileOperate.filterIllegalFileName(name) + "】-" + bvNum + ".mp4";
        if(savePath == null) {
            savePath = defaultSavePath;
        }
        String finalFilePath = new File(savePath, fileName).getAbsolutePath();

        try {
            String bvUrl = "https://www.bilibili.com/video/" + bvNum;
            log.info("开始下载视频：BV={}, URL={}", bvNum, bvUrl);
            
            String[] cmd = {
                    "yt-dlp",
                    "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]",
                    "--concurrent-fragments", "16",
                    "--no-progress",
                    "--quiet",
                    "--force-ipv4",
                    "-o", finalFilePath.replace(".mp4", ".%(ext)s"),
                    bvUrl
            };

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("PYTHONIOENCODING", "UTF-8");
            pb.redirectErrorStream(true); // 合并错误输出到标准输出
            Process process = pb.start();
            
            // 读取输出信息
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                long fileSize = FileOperate.getFileSize(finalFilePath);
                log.info("视频下载成功：BV={}, 文件大小={}字节", bvNum, fileSize);
                return fileSize;
            } else {
                log.error("下载BV{}失败：yt-dlp退出码={}, 输出：{}", bvNum, exitCode, output);
                return 0L;
            }
        } catch (Exception e) {
            log.error("下载BV{}失败：{}", bvNum, e.getMessage(), e);
            return 0L;
        }
    }

    @Override
    @Retryable(maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 2000))
    public Video getVideoData(String bvNum, String savePath) throws Exception {
        log.info("开始获取视频数据：BV={}", bvNum);
        
        List<String> args = new ArrayList<>();
        args.add(bvNum);
        
        String pythonOutput = executePythonScript("getVideoData.py", args);
        
        if(pythonOutput.contains("脚本执行失败") || pythonOutput.contains("未找到核心数据")) {
            log.error("获取视频数据失败：{}", pythonOutput);
            throw new RuntimeException("获取视频数据失败：" + pythonOutput);
        }
        
        Video video = objectMapper.readValue(pythonOutput, Video.class);
        
        if(savePath == null) {
            savePath = defaultSavePath;
        }

        video.setId(null);
        video.setSavePath(savePath);
        video.setFileSize(0L);
        video.setIsDownload(0);
        video.setCreateTime(new Date());

        log.info("视频数据获取成功：BV={}, 标题={}", bvNum, video.getName());
        return video;
    }

    @Override
    public CompletableFuture<Boolean> downloadVideoToLocal(String bvNum, String savePath) {
        final String finalSavePath = (savePath == null) ? defaultSavePath : savePath;
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String bvUrl = "https://www.bilibili.com/video/" + bvNum;
                log.info("开始下载视频到本地：BV={}", bvNum);
                
                String[] cmd = {
                        "yt-dlp",
                        "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]",
                        "--concurrent-fragments", "16",
                        "--no-progress",
                        "--quiet",
                        "--force-ipv4",
                        "-o", finalSavePath,
                        bvUrl
                };
                Process process = new ProcessBuilder(cmd)
                        .redirectError(ProcessBuilder.Redirect.to(new File("NUL")))
                        .start();
                int exitCode = process.waitFor();
                
                boolean success = exitCode == 0;
                log.info("视频下载{}：BV={}", success ? "成功" : "失败", bvNum);
                return success;
            } catch(Exception e) {
                log.error("下载BV{}失败：{}", bvNum, e.getMessage(), e);
                return false;
            }
        }, downloadExecutor);
    }

    @Override
    public Video downloadVideoAndGetData(String bvNum, String savePath, boolean needDownload) {
        log.info("开始处理视频：BV={}, needDownload={}", bvNum, needDownload);
        
        Video video = new Video();
        video.setBvNum(bvNum);
        
        try {
            Video videoData = getVideoData(bvNum, savePath);
            
            video.setName(videoData.getName());
            video.setUpName(videoData.getUpName());
            video.setUpId(videoData.getUpId());
            video.setPlayCount(videoData.getPlayCount());
            video.setDanmakuCount(videoData.getDanmakuCount());
            video.setLikeCount(videoData.getLikeCount());
            video.setCoinCount(videoData.getCoinCount());
            video.setFavoriteCount(videoData.getFavoriteCount());
            video.setShareCount(videoData.getShareCount());
            video.setDuration(videoData.getDuration());
            video.setVideoDesc(videoData.getVideoDesc());
            video.setTags(videoData.getTags());
            video.setCoverUrl(videoData.getCoverUrl());
            video.setPublishTime(videoData.getPublishTime());
            
            if (needDownload) {
                Long fileSize = donwloadVideoLocal(bvNum, videoData.getName(), savePath);
                video.setIsDownload(fileSize > 0 ? 1 : 0);
                
                if (fileSize > 0) {
                    video.setSavePath(savePath);
                    video.setFileSize(fileSize);
                    log.info("视频下载成功：BV={}, 文件大小={}字节", bvNum, fileSize);
                } else {
                    log.warn("视频下载失败：BV={}", bvNum);
                }
            } else {
                video.setIsDownload(0);
            }
            
        } catch (Exception e) {
            log.error("处理BV{}失败：{}", bvNum, e.getMessage(), e);
            video.setIsDownload(0);
        }
        
        return video;
    }

    @Override
    public String getRegionData(String regionId, String startPage, String endPage) {
        log.info("开始爬取分区数据，分区ID={}，页码范围：{}-{}", regionId, startPage, endPage);
        
        // 标记爬虫正在执行
        isCrawlerRunning = true;
        log.info("设置爬虫状态为运行中");
        
        // 异步执行脚本
        CompletableFuture.runAsync(() -> {
            // 再次标记爬虫正在执行，确保状态正确
            isCrawlerRunning = true;
            log.info("异步任务开始，再次设置爬虫状态为运行中");
            
            // 保存当前线程引用
            crawlerThread = Thread.currentThread();
            
            try {
                List<String> args = new ArrayList<>();
                args.add(regionId);
                args.add(startPage);
                args.add(endPage);

                log.info("开始执行爬虫，页码范围：{}-{}", startPage, endPage);
                String result = executePythonScript("getregiondata3.py", args);
                log.info("爬虫执行完成，结果：{}", result);
            } catch (Exception e) {
                log.error("执行爬虫失败：{}", e.getMessage(), e);
            } finally {
                // 清除线程引用
                crawlerThread = null;
                // 标记爬虫执行完成
                isCrawlerRunning = false;
                log.info("设置爬虫状态为停止");
            }
        }, crawlExecutor);
        
        // 返回一个提示信息，实际结果会在后台执行
        return "爬取任务已启动，请等待执行完成。\n分区ID：" + regionId + "，页码范围：" + startPage + "-" + endPage;
    }

    @Override
    public boolean stopCrawler() {
        boolean stopped = false;
        
        // 中断爬虫线程
        if (crawlerThread != null && crawlerThread.isAlive()) {
            log.info("正在中断爬虫线程：{}", crawlerThread.getId());
            crawlerThread.interrupt();
            stopped = true;
            log.info("爬虫线程已成功中断");
        }
        
        // 遍历并终止所有运行中的进程
        for (Map.Entry<String, Process> entry : runningProcesses.entrySet()) {
            String processKey = entry.getKey();
            Process process = entry.getValue();
            
            if (process != null && process.isAlive()) {
                log.info("正在终止爬虫进程：{}", processKey);
                process.destroy();
                try {
                    // 等待进程终止
                    process.waitFor(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (!process.isAlive()) {
                        log.info("爬虫进程已成功终止：{}", processKey);
                        stopped = true;
                    } else {
                        log.warn("爬虫进程未能在5秒内终止：{}", processKey);
                    }
                } catch (InterruptedException e) {
                    log.error("终止爬虫进程时发生异常：{}", e.getMessage(), e);
                    Thread.currentThread().interrupt();
                }
            }
            
            // 移除进程引用
            runningProcesses.remove(processKey);
        }
        
        // 清除线程引用
        crawlerThread = null;
        // 标记爬虫执行完成
        isCrawlerRunning = false;
        
        return stopped;
    }

    @Override
    public boolean isCrawlerRunning() {
        return isCrawlerRunning;
    }

    @Override
    public List<Video> batchGetVideoDataByBvNum(List<String> bvNumList) {
        log.info("开始根据bv号列表调用Python脚本获取视频数据，bv号数量：{}", bvNumList.size());
        
        // 标记爬虫正在执行
        isCrawlerRunning = true;
        log.info("设置爬虫状态为运行中");
        
        // 保存当前线程引用
        crawlerThread = Thread.currentThread();
        
        List<Video> allVideoList = new ArrayList<>();
        
        try {
            // 分批处理bv号，每批50个，提高处理效率
            int batchSize = 50;
            int total = bvNumList.size();
            
            for (int i = 0; i < total; i += batchSize) {
                int endIndex = Math.min(i + batchSize, total);
                List<String> batchBvNumList = bvNumList.subList(i, endIndex);
                
                log.info("处理批次：{}-{}/{}}，数量：{}", i + 1, endIndex, total, batchBvNumList.size());
                
                // 调用Python脚本获取视频数据
                String result = executePythonScript("getBatchVideoDataByBvNum.py", batchBvNumList);
                
                // 解析Python脚本返回的JSON数据
                try {
                    // 提取JSON部分
                    String jsonStart = "--- JSON结果开始 ---";
                    String jsonEnd = "--- JSON结果结束 ---";
                    int startIndex = result.indexOf(jsonStart);
                    int endIndexJson = result.indexOf(jsonEnd);
                    
                    if (startIndex != -1 && endIndexJson != -1) {
                        String jsonStr = result.substring(startIndex + jsonStart.length(), endIndexJson).trim();
                        
                        // 解析JSON为视频数据列表
                        List<Map<String, Object>> videoDataList = objectMapper.readValue(
                            jsonStr, 
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                        );
                        
                        log.info("成功解析到{}个视频数据", videoDataList.size());
                        
                        // 将数据转换为Video对象列表
                        for (Map<String, Object> videoData : videoDataList) {
                            Video video = convertToVideo(videoData);
                            allVideoList.add(video);
                        }
                    } else {
                        log.error("无法从Python脚本返回结果中提取JSON数据");
                    }
                } catch (Exception e) {
                    log.error("解析Python脚本返回的数据失败", e);
                }
                
                // 每处理一批数据后，手动触发垃圾回收，减少内存占用
                if ((i + batchSize) % (batchSize * 10) == 0) {
                    System.gc();
                }
            }
            
            log.info("Python脚本调用完成，共获取{}个视频数据", allVideoList.size());
            return allVideoList;
        } finally {
            // 清除线程引用
            crawlerThread = null;
            // 标记爬虫执行完成
            isCrawlerRunning = false;
            log.info("设置爬虫状态为停止");
        }
    }

    @Override
    public List<UpVideoData> batchGetUpVideoDataByBvNum(List<String> bvNumList, Long mid) {
        log.info("开始根据bv号列表调用Python脚本获取视频数据(UpVideoData)，bv号数量：{}", bvNumList.size());

        isCrawlerRunning = true;
        log.info("设置爬虫状态为运行中");

        crawlerThread = Thread.currentThread();

        List<UpVideoData> allUpVideoDataList = new ArrayList<>();

        try {
            int batchSize = 50;
            int total = bvNumList.size();

            for (int i = 0; i < total; i += batchSize) {
                int endIndex = Math.min(i + batchSize, total);
                List<String> batchBvNumList = bvNumList.subList(i, endIndex);

                log.info("处理批次：{}-{}/{}，数量：{}", i + 1, endIndex, total, batchBvNumList.size());

                String result = executePythonScript("getBatchVideoDataByBvNum.py", batchBvNumList);

                try {
                    String jsonStart = "--- JSON结果开始 ---";
                    String jsonEnd = "--- JSON结果结束 ---";
                    int startIndex = result.indexOf(jsonStart);
                    int endIndexJson = result.indexOf(jsonEnd);

                    if (startIndex != -1 && endIndexJson != -1) {
                        String jsonStr = result.substring(startIndex + jsonStart.length(), endIndexJson).trim();

                        List<Map<String, Object>> videoDataList = objectMapper.readValue(
                            jsonStr,
                            objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                        );

                        log.info("成功解析到{}个视频数据", videoDataList.size());
                        
                        // 转换为UpVideoData对象
                        for (Map<String, Object> videoData : videoDataList) {
                            UpVideoData upVideoData = convertToUpVideoData(videoData, mid);
                            allUpVideoDataList.add(upVideoData);
                        }
                    } else {
                        log.error("无法从Python脚本返回结果中提取JSON数据");
                    }
                } catch (Exception e) {
                    log.error("解析Python脚本返回的数据失败", e);
                }

                if ((i + batchSize) % (batchSize * 10) == 0) {
                    System.gc();
                }
            }

            log.info("Python脚本调用完成，共获取{}个视频数据(UpVideoData)", allUpVideoDataList.size());
            return allUpVideoDataList;
        } finally {
            crawlerThread = null;
            isCrawlerRunning = false;
            log.info("设置爬虫状态为停止");
        }
    }

    /**
     * 将Map数据转换为Video对象
     * @param videoData Map数据
     * @return Video对象
     */
    private Video convertToVideo(Map<String, Object> videoData) {
        Video video = new Video();
        
        // 设置视频数据
        video.setName(filterEmoji((String) videoData.get("name")));
        video.setBvNum((String) videoData.get("BvNum"));
        video.setUpName(filterEmoji((String) videoData.get("upName")));
        video.setUpId(toLong(videoData.get("upId")));
        video.setPlayCount(toLong(videoData.get("playCount")));
        video.setDanmakuCount(toLong(videoData.get("danmakuCount")));
        video.setLikeCount(toLong(videoData.get("likeCount")));
        video.setCoinCount(toLong(videoData.get("coinCount")));
        video.setFavoriteCount(toLong(videoData.get("favoriteCount")));
        video.setShareCount(toLong(videoData.get("shareCount")));
        video.setDuration(toInteger(videoData.get("duration")));
        video.setVideoDesc(filterEmoji((String) videoData.get("videoDesc")));
        video.setTags(filterEmoji((String) videoData.get("tags")));
        video.setCoverUrl((String) videoData.get("coverUrl"));
        
        // 设置视频发布时间
        Object publishTimeObj = videoData.get("publishTime");
        if (publishTimeObj != null) {
            try {
                // 尝试不同的时间格式转换
                if (publishTimeObj instanceof String) {
                    String publishTimeStr = (String) publishTimeObj;
                    // 解析时间字符串为LocalDateTime
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    video.setPublishTime(java.time.LocalDateTime.parse(publishTimeStr, formatter));
                } else if (publishTimeObj instanceof Long) {
                    // 如果是时间戳，转换为LocalDateTime
                    long timestamp = (Long) publishTimeObj;
                    video.setPublishTime(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(timestamp), java.time.ZoneId.systemDefault()));
                }
            } catch (Exception e) {
                log.warn("解析发布时间失败: {}", e.getMessage());
            }
        }
        
        return video;
    }
    
    /**
     * 将Map数据转换为UpVideoData对象
     * @param videoData Map数据
     * @param mid UP主ID
     * @return UpVideoData对象
     */
    private UpVideoData convertToUpVideoData(Map<String, Object> videoData, Long mid) {
        UpVideoData upVideoData = new UpVideoData();
        
        // 设置视频数据
        upVideoData.setTitle(filterEmoji((String) videoData.get("name")));
        upVideoData.setBv_num((String) videoData.get("BvNum"));
        upVideoData.setAuthor(filterEmoji((String) videoData.get("upName")));
        upVideoData.setMid(mid);
        upVideoData.setPlayCount(toLong(videoData.get("playCount")));
        upVideoData.setDanmakuCount(toLong(videoData.get("danmakuCount")));
        upVideoData.setLikeCount(toLong(videoData.get("likeCount")));
        upVideoData.setCoinCount(toLong(videoData.get("coinCount")));
        upVideoData.setFavoriteCount(toLong(videoData.get("favoriteCount")));
        upVideoData.setShareCount(toLong(videoData.get("shareCount")));
        upVideoData.setDuration(toInteger(videoData.get("duration")));
        upVideoData.setVideoDesc(filterEmoji((String) videoData.get("videoDesc")));
        upVideoData.setTags(filterEmoji((String) videoData.get("tags")));
        upVideoData.setCoverUrl((String) videoData.get("coverUrl"));
        
        // 设置视频发布时间
        Object publishTimeObj = videoData.get("publishTime");
        if (publishTimeObj != null) {
            try {
                // 尝试不同的时间格式转换
                if (publishTimeObj instanceof String) {
                    String publishTimeStr = (String) publishTimeObj;
                    // 解析时间字符串为LocalDateTime
                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    upVideoData.setPublishTime(java.time.LocalDateTime.parse(publishTimeStr, formatter));
                } else if (publishTimeObj instanceof Long) {
                    // 如果是时间戳，转换为LocalDateTime
                    long timestamp = (Long) publishTimeObj;
                    upVideoData.setPublishTime(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(timestamp), java.time.ZoneId.systemDefault()));
                }
            } catch (Exception e) {
                log.warn("解析发布时间失败: {}", e.getMessage());
            }
        }
        
        return upVideoData;
    }
    
    /**
     * 过滤字符串中的emoji等特殊字符
     */
    private String filterEmoji(String str) {
        if (str == null) {
            return null;
        }
        // 使用正则表达式过滤emoji
        return str.replaceAll("[\\ud800-\\udbff\\udc00-\\udfff]", "");
    }

    /**
     * 将Object转换为Long
     */
    private Long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 将Object转换为Integer
     */
    private Integer toInteger(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Long) return ((Long) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public Map<String, Object> crawlUpVideoData(Long mid, Boolean continueOnDuplicate) {
        Map<String, Object> result = new HashMap<>();
        
        log.info("开始爬取UP主[{}]的视频数据，continueOnDuplicate: {}", mid, continueOnDuplicate);
        
        // 标记爬虫正在执行
        isCrawlerRunning = true;
        log.info("设置爬虫状态为运行中");
        
        // 保存当前线程引用
        crawlerThread = Thread.currentThread();
        
        try {
            int totalSuccess = 0;
            int totalDuplicate = 0;
            int totalPages = 0;
            boolean shouldStop = false;
            List<Integer> failedPages = new ArrayList<>();
            
            // 先爬取第1页获取总页数
            log.info("开始爬取第1页，获取总页数");
            List<String> firstPageArgs = new ArrayList<>();
            firstPageArgs.add(String.valueOf(mid));
            firstPageArgs.add("1");
            
            String firstPageOutput = executePythonScript("getUpVideoData.py", firstPageArgs);
            log.info("第1页爬取完成，结果：{}", firstPageOutput);
            
            // 解析第1页结果，获取总页数和成功数
            int firstPageSuccess = 0;
            int firstPageDuplicate = 0;
            boolean firstPageFailed = false;
            
            // 解析第1页结果，获取总页数和成功数
            if (firstPageOutput.contains("TOTAL_PAGES:")) {
                String[] lines = firstPageOutput.split("\n");
                for (String line : lines) {
                    if (line.contains("TOTAL_PAGES:")) {
                        String numStr = line.substring(line.indexOf("TOTAL_PAGES:") + 12).trim();
                        try {
                            totalPages = Integer.parseInt(numStr);
                            log.info("解析总页数成功：{}", totalPages);
                        } catch (NumberFormatException e) {
                            log.warn("解析总页数失败：{}", e.getMessage());
                            totalPages = 1;
                        }
                    } else if (line.contains("成功数：")) {
                        String numStr = line.substring(line.indexOf("成功数：") + 4).trim();
                        try {
                            firstPageSuccess = Integer.parseInt(numStr);
                        } catch (NumberFormatException e) {
                            log.warn("解析成功数失败：{}", e.getMessage());
                        }
                    } else if (line.contains("重复数：")) {
                        String numStr = line.substring(line.indexOf("重复数：") + 4).trim();
                        try {
                            firstPageDuplicate = Integer.parseInt(numStr);
                        } catch (NumberFormatException e) {
                            log.warn("解析重复数失败：{}", e.getMessage());
                        }
                    } else if (line.contains("❌ 第1页获取失败")) {
                        firstPageFailed = true;
                    }
                }
            } else {
                // 尝试解析旧格式
                if (firstPageOutput.contains("总页数：")) {
                    String[] lines = firstPageOutput.split("\n");
                    for (String line : lines) {
                        if (line.contains("总页数：")) {
                            String numStr = line.substring(line.indexOf("总页数：") + 5).trim();
                            try {
                                totalPages = Integer.parseInt(numStr);
                                log.info("解析总页数成功（旧格式）：{}", totalPages);
                            } catch (NumberFormatException e) {
                                log.warn("解析总页数失败（旧格式）：{}", e.getMessage());
                                totalPages = 1;
                            }
                        }
                    }
                } else {
                    // 如果输出中不包含总页数，可能是爬取失败
                    firstPageFailed = true;
                }
            }
            
            if (firstPageFailed) {
                failedPages.add(1);
                log.warn("第1页爬取失败");
            }
            
            totalSuccess += firstPageSuccess;
            totalDuplicate += firstPageDuplicate;
            
            // 记录连续重复页数
            int consecutiveDuplicatePages = 0;
            int lastProcessedPage = 1;
            
            // 检查第1页是否全部重复（成功数为0且重复数>0）
            if (firstPageSuccess == 0 && firstPageDuplicate > 0) {
                consecutiveDuplicatePages = 1;
                log.warn("第1页全部重复（成功：{}，重复：{}）", firstPageSuccess, firstPageDuplicate);
            } else {
                consecutiveDuplicatePages = 0;
            }
            
            // 循环爬取剩余页面
            if (!shouldStop && totalPages > 1) {
                for (int page = 2; page <= totalPages; page++) {
                    if (!isCrawlerRunning) {
                        log.info("爬虫被手动停止");
                        break;
                    }
                    
                    log.info("开始爬取第{}页", page);
                    List<String> pageArgs = new ArrayList<>();
                    pageArgs.add(String.valueOf(mid));
                    pageArgs.add(String.valueOf(page));
                    
                    String pageOutput = executePythonScript("getUpVideoData.py", pageArgs);
                    log.info("第{}页爬取完成，结果：{}", page, pageOutput);
                    
                    // 解析页面结果
                    int pageSuccess = 0;
                    int pageDuplicate = 0;
                    boolean pageFailed = false;
                    
                    if (pageOutput.contains("成功数：")) {
                        String[] lines = pageOutput.split("\n");
                        for (String line : lines) {
                            if (line.contains("成功数：")) {
                                String numStr = line.substring(line.indexOf("成功数：") + 4).trim();
                                try {
                                    pageSuccess = Integer.parseInt(numStr);
                                } catch (NumberFormatException e) {
                                    log.warn("解析成功数失败：{}", e.getMessage());
                                }
                            } else if (line.contains("重复数：")) {
                                String numStr = line.substring(line.indexOf("重复数：") + 4).trim();
                                try {
                                    pageDuplicate = Integer.parseInt(numStr);
                                } catch (NumberFormatException e) {
                                    log.warn("解析重复数失败：{}", e.getMessage());
                                }
                            } else if (line.contains("❌ 第" + page + "页获取失败")) {
                                pageFailed = true;
                            }
                        }
                    } else {
                        // 如果输出中不包含成功数，可能是爬取失败
                        pageFailed = true;
                    }
                    
                    if (pageFailed) {
                        failedPages.add(page);
                        log.warn("第{}页爬取失败", page);
                    }
                    
                    totalSuccess += pageSuccess;
                    totalDuplicate += pageDuplicate;
                    lastProcessedPage = page;
                    
                    // 检查该页是否全部重复（成功数为0且重复数>0）
                    if (pageSuccess == 0 && pageDuplicate > 0) {
                        consecutiveDuplicatePages++;
                        log.warn("第{}页全部重复（成功：{}，重复：{}），连续重复页数：{}", page, pageSuccess, pageDuplicate, consecutiveDuplicatePages);
                    } else {
                        // 重置连续重复页数计数
                        consecutiveDuplicatePages = 0;
                    }
                    
                    // 检查连续重复页数是否达到3页，如有则停止（除非设置了continueOnDuplicate）
                    if (!continueOnDuplicate && consecutiveDuplicatePages >= 3) {
                        log.warn("连续{}页全部重复，停止爬取", consecutiveDuplicatePages);
                        shouldStop = true;
                        break;
                    }
                    
                    // 延迟一下，避免请求过快
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        log.warn("线程被中断：{}", e.getMessage());
                        break;
                    }
                }
            }
            
            // 只要执行了爬取操作（没有异常），就返回success=true
            // 即使遇到重复数据，也应该返回成功，因为可能已经有部分数据入库
            result.put("success", true);
            result.put("mid", mid);
            result.put("successCount", totalSuccess);
            result.put("duplicateCount", totalDuplicate);
            result.put("totalPages", totalPages);
            result.put("lastProcessedPage", lastProcessedPage);
            result.put("failedPages", failedPages);
            
            // 如果因为连续3页重复而停止，添加提示信息
            if (consecutiveDuplicatePages >= 3) {
                result.put("stopReason", String.format("获取该up主(%d)第%d页时，发现连续3页数据全部重复，估计以后的页面已经录入数据库，停止爬取", mid, lastProcessedPage));
            }
            
        } catch (Exception e) {
            log.error("执行爬虫失败：{}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        } finally {
            // 清除线程引用
            crawlerThread = null;
            // 标记爬虫执行完成
            isCrawlerRunning = false;
            log.info("设置爬虫状态为停止");
        }
        
        return result;
    }

    @Override
    public Map<String, Object> crawlUpVideoData(Long mid, Boolean usePageRange, Integer startPage, Integer endPage, Boolean continueOnDuplicate) {
        if (usePageRange == null || !usePageRange) {
            return crawlUpVideoData(mid, continueOnDuplicate);
        }
        
        Map<String, Object> result = new HashMap<>();
        
        log.info("开始爬取UP主[{}]的视频数据（页码范围：{}-{}）", mid, startPage, endPage);
        
        if (startPage == null || startPage < 1) {
            result.put("success", false);
            result.put("error", "起始页必须大于0");
            return result;
        }
        
        if (endPage == null || endPage < 1) {
            result.put("success", false);
            result.put("error", "结束页必须大于0");
            return result;
        }
        
        if (startPage > endPage) {
            result.put("success", false);
            result.put("error", "起始页不能大于结束页");
            return result;
        }
        
        // 标记爬虫正在执行
        isCrawlerRunning = true;
        log.info("设置爬虫状态为运行中");
        
        // 保存当前线程引用
        crawlerThread = Thread.currentThread();
        
        try {
            int totalSuccess = 0;
            int totalDuplicate = 0;
            boolean shouldStop = false;
            List<Integer> failedPages = new ArrayList<>();
            int totalPages = -1; // 总页数，-1表示未获取到
            
            // 循环爬取指定页码范围的页面
            for (int page = startPage; page <= endPage; page++) {
                // 如果已经获取到总页数，且当前页超过总页数，则退出循环
                if (totalPages > 0 && page > totalPages) {
                    log.info("当前页{}已超过总页数{}，退出爬虫", page, totalPages);
                    break;
                }
                
                if (!isCrawlerRunning) {
                    log.info("爬虫被手动停止");
                    break;
                }
                
                log.info("开始爬取第{}页", page);
                List<String> pageArgs = new ArrayList<>();
                pageArgs.add(String.valueOf(mid));
                pageArgs.add(String.valueOf(page));
                
                String pageOutput = executePythonScript("getUpVideoData.py", pageArgs);
                log.info("第{}页爬取完成，结果：{}", page, pageOutput);
                
                // 解析页面结果
                int pageSuccess = 0;
                int pageDuplicate = 0;
                boolean pageFailed = false;
                
                if (pageOutput.contains("成功数：")) {
                    String[] lines = pageOutput.split("\n");
                    for (String line : lines) {
                        if (line.contains("成功数：")) {
                            String numStr = line.substring(line.indexOf("成功数：") + 4).trim();
                            try {
                                pageSuccess = Integer.parseInt(numStr);
                            } catch (NumberFormatException e) {
                                log.warn("解析成功数失败：{}", e.getMessage());
                            }
                        } else if (line.contains("重复数：")) {
                            String numStr = line.substring(line.indexOf("重复数：") + 4).trim();
                            try {
                                pageDuplicate = Integer.parseInt(numStr);
                            } catch (NumberFormatException e) {
                                log.warn("解析重复数失败：{}", e.getMessage());
                            }
                        } else if (line.contains("TOTAL_PAGES:")) {
                            // 解析总页数
                            String numStr = line.substring(line.indexOf("TOTAL_PAGES:") + 12).trim();
                            try {
                                totalPages = Integer.parseInt(numStr);
                                log.info("解析总页数成功：{}", totalPages);
                            } catch (NumberFormatException e) {
                                log.warn("解析总页数失败：{}", e.getMessage());
                            }
                        } else if (line.contains("❌ 第" + page + "页获取失败")) {
                            pageFailed = true;
                        }
                    }
                } else {
                    // 如果输出中不包含成功数，可能是爬取失败
                    pageFailed = true;
                }
                
                if (pageFailed) {
                    failedPages.add(page);
                    log.warn("第{}页爬取失败", page);
                }
                
                totalSuccess += pageSuccess;
                totalDuplicate += pageDuplicate;
                
                // 延迟一下，避免请求过快
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    log.warn("线程被中断：{}", e.getMessage());
                    break;
                }
            }
            
            result.put("success", true);
            result.put("mid", mid);
            result.put("successCount", totalSuccess);
            result.put("duplicateCount", totalDuplicate);
            result.put("totalPages", totalPages > 0 ? totalPages : (endPage - startPage + 1));
            result.put("startPage", startPage);
            result.put("endPage", endPage);
            result.put("failedPages", failedPages);
            
        } catch (Exception e) {
            log.error("执行爬虫失败：{}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        } finally {
            // 清除线程引用
            crawlerThread = null;
            // 标记爬虫执行完成
            isCrawlerRunning = false;
            log.info("设置爬虫状态为停止");
        }
        
        return result;
    }

}
