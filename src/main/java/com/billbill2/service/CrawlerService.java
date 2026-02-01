package com.billbill2.service;

import com.billbill2.DTO.CommentRequestDTO;
import com.billbill2.entity.Video;

import java.util.concurrent.CompletableFuture;

public interface CrawlerService {
    /**
     * 调用Python爬虫并入库
     * @param request 爬取参数
     * @return 爬取/入库结果
     */
    String crawlAndSave(CommentRequestDTO request);


    /**
     * 下载B站视频(yt-dlp不调用Python版）
     * @param bvNum BV号
     * @param name 视频标题
     * @param savePath 保存路径，如果为空则使用默认值defaultSavePath
     * @return 下载文件大小
     */
    Long donwloadVideoLocal(String bvNum,String name,String savePath);

    /**
     * 调用python脚本getVideoData，获取视频有关数据，点赞数播放量
     * @param bvNum BV号
     * @param savePath 保存路径，如果为空则使用默认值defaultSavePath
     * @return 获取到的视频播放量等有关数据，视频大小等需要稍后填写;
     *
     */
    Video getVideoData(String bvNum,String savePath) throws Exception;

    /**
     * 下载B站视频到本地
     * @param bvNum BV号
     * @param savePath 保存路径
     * @return 下载结果
     */
    CompletableFuture<Boolean> downloadVideoToLocal(String bvNum, String savePath);

    /**
     * 下载视频并获取视频数据（点赞数、播放量等）
     * @param bvNum BV号
     * @param savePath 保存路径
     * @param needDownload 是否需要下载视频
     * @return 视频数据对象
     */
    Video downloadVideoAndGetData(String bvNum, String savePath, boolean needDownload);
}
