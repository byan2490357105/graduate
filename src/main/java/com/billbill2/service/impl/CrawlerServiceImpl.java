package com.billbill2.service.impl;

import com.billbill2.DTO.CommentRequestDTO;
import com.billbill2.entity.Video;
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
        try {
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return output.toString().trim();
            } else {
                log.error("脚本执行失败（退出码：{}）：{}", exitCode, output);
                return "脚本执行失败（退出码：" + exitCode + "）：\n" + output;
            }
        } catch (IOException | InterruptedException e) {
            log.error("调用脚本异常：{}", e.getMessage(), e);
            return "调用脚本异常：" + e.getMessage();
        }
    }

    @Override
    public String crawlAndSave(CommentRequestDTO request) {
        List<String> args = new ArrayList<>();
        args.add(request.getBvNum());
        args.add(request.getStartPage().toString());
        args.add(request.getEndPage().toString());
        
        String result = executePythonScript("getComment.py", args);
        return "爬取并入库结果：\n" + result;
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
                        "-o", savePath,
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
        
        CompletableFuture<Boolean> downloadFuture = null;
        if (needDownload) {
            downloadFuture = downloadVideoToLocal(bvNum, savePath);
        }
        
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
            
            if (needDownload && downloadFuture != null) {
                boolean isDownloadSuccess = downloadFuture.get();
                video.setIsDownload(isDownloadSuccess ? 1 : 0);
                
                if (isDownloadSuccess) {
                    video.setSavePath(savePath);
                    File videoFile = new File(savePath);
                    if (videoFile.exists()) {
                        video.setFileSize(videoFile.length());
                        log.info("视频下载成功：BV={}, 文件大小={}字节", bvNum, videoFile.length());
                    }
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
    public String getZoneIDData(String zoneID,String startPage,String endPage) {
        return getZoneIDDataWithDuration(zoneID, startPage, endPage, 60); // 默认60分钟
    }

    @Override
    public String getZoneIDDataWithDuration(String zoneID, String startPage, String endPage, int duration) {
        // 标记爬虫正在执行
        isCrawlerRunning = true;
        log.info("设置爬虫状态为运行中");
        
        // 执行异步脚本，循环爬取直到时间结束
        CompletableFuture.runAsync(() -> {
            // 再次标记爬虫正在执行，确保状态正确
            isCrawlerRunning = true;
            log.info("异步任务开始，再次设置爬虫状态为运行中");
            
            // 保存当前线程引用
            crawlerThread = Thread.currentThread();
            
            long startTime = System.currentTimeMillis();
            long durationMs = duration * 60 * 1000;
            long endTime = startTime + durationMs;
            
            log.info("开始循环爬取分区数据，持续时间：{}分钟，结束时间：{}", duration, new java.util.Date(endTime));
            
            // 循环执行爬虫，直到时间结束
            while (System.currentTimeMillis() < endTime) {
                // 检查是否有停止信号
                if (Thread.currentThread().isInterrupted()) {
                    log.info("爬虫被中断，结束循环爬取");
                    break;
                }
                
                try {
                    List<String> args = new ArrayList<>();
                    args.add(zoneID);
                    args.add("1"); // 固定从第1页开始
                    args.add("10"); // 固定到第10页结束
                    
                    log.info("开始执行爬虫，页码范围：1-10");
                    String result = executePythonScript("getregiondata.py", args);
                    log.info("爬虫执行完成，结果：{}", result);
                    
                    // 短暂休眠，避免过于频繁的请求
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    log.error("爬虫线程被中断：{}", e.getMessage(), e);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("执行爬虫失败：{}", e.getMessage(), e);
                    // 遇到异常后短暂休眠，避免无限循环报错
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            log.info("爬虫执行时间已到，结束循环爬取");
            // 清除线程引用
            crawlerThread = null;
            // 标记爬虫执行完成
            isCrawlerRunning = false;
            log.info("设置爬虫状态为停止");
        });
        
        // 返回一个提示信息，实际结果会在后台执行
        return "爬取任务已启动，请等待执行完成。\n分区ID：" + zoneID + "，页码范围：1-10，持续时间：" + duration + "分钟";
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
}
