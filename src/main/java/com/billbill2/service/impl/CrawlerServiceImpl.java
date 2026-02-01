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
}
