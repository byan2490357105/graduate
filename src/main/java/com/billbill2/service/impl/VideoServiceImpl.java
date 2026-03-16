package com.billbill2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.VideoMapper;
import com.billbill2.entity.Video;
import com.billbill2.service.VideoService;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {
    @Resource
    private SqlSession sqlSession;

    private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);

    @Override
    public boolean saveOrUpdateVideoByBvNum(Video video) {
        // MyBatis-Plus自动根据BV号（唯一索引）判断：
        // - 不存在：新增数据
        // - 已存在：更新数据
        LambdaQueryWrapper<Video> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(Video::getBvNum,video.getBvNum());
        Video existVideo=this.getOne(queryWrapper,false); //设置为false,不抛出多条结果异常

        if(existVideo!=null){
            //存在执行增量更新
            LambdaUpdateWrapper<Video> updateWrapper=new LambdaUpdateWrapper<>();
            updateWrapper.eq(Video::getBvNum,video.getBvNum())
            //只更新需要点赞，收藏等其他有修改的字段
                .set(Video::getLikeCount,video.getLikeCount())//点赞数
                .set(Video::getCoinCount,video.getCoinCount())//硬币数
                .set(Video::getFavoriteCount,video.getFavoriteCount())//收藏数
                .set(Video::getDanmakuCount,video.getDanmakuCount())//弹幕数
                .set(Video::getPlayCount,video.getPlayCount())//播放量
                .set(Video::getSavePath,video.getSavePath()); //保存路径，我认为应该不会变的这个

            return this.update(updateWrapper);
        }else{
            //不存在，直接插入
            return this.save(video);
        }
    }

    /**
     * 清空视频表所有数据，并重置自增主键ID为1
     * @Transactional：保证删除和重置主键的原子性（要么都成功，要么都回滚）
     * @return 操作是否成功
     */
    @Transactional(rollbackFor = Exception.class) // 捕获所有异常并回滚
    public boolean clearVideoTableAndResetId() {
        try {
            // 步骤1：删除表中所有数据（TRUNCATE会自动重置自增ID，但兼容DELETE方式）
            // 方式1：TRUNCATE TABLE（推荐，效率更高，自动重置自增ID）
            this.baseMapper.truncateVideoTable();

            // 方式2：若TRUNCATE有权限问题，用DELETE + ALTER TABLE（备用方案）
            // this.remove(null); // 删除所有数据
            // this.baseMapper.resetVideoTableAutoIncrement(); // 重置自增ID为1

            return true;
        } catch (Exception e) {
            log.error("清空视频表并重置主键失败", e); // 需添加日志依赖
            return false;
        }
    }

    @Override
    public Map<String, Integer> getVideoTagsStatistics(int limit) {
        log.info("开始统计视频标签，限制返回数量：{}", limit);
        
        // 存储标签统计结果
        Map<String, Integer> tagCountMap = new HashMap<>();
        
        // 分批查询，每次查询1000条，避免一次性加载过多数据到内存
        int batchSize = 1000;
        int current = 0;
        
        try {
            while (true) {
                // 构建查询条件，只查询tags字段，提高查询效率
                LambdaQueryWrapper<Video> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.select(Video::getTags)
                           .last("LIMIT " + current + ", " + batchSize);
                
                List<Video> videos = this.list(queryWrapper);
                
                // 如果没有更多数据，退出循环
                if (videos == null || videos.isEmpty()) {
                    break;
                }
                
                // 处理当前批次的标签数据
                for (Video video : videos) {
                    String tags = video.getTags();
                    if (tags != null && !tags.isEmpty()) {
                        // 分割标签并统计
                        String[] tagArray = tags.split(",");
                        for (String tag : tagArray) {
                            tag = tag.trim(); // 去除空格
                            if (!tag.isEmpty()) {
                                tagCountMap.put(tag, tagCountMap.getOrDefault(tag, 0) + 1);
                            }
                        }
                    }
                }
                
                // 增加偏移量
                current += batchSize;
                
                // 每处理一批数据后，手动触发垃圾回收，减少内存占用
                if (current % (batchSize * 10) == 0) {
                    System.gc();
                }
            }
            
            // 对标签进行排序，取出现次数最多的前limit个标签
            Map<String, Integer> sortedTagMap = tagCountMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1, // 处理键冲突，保留第一个
                    LinkedHashMap::new // 保持排序顺序
                ));
            
            log.info("标签统计完成，共统计到{}个标签，返回前{}个", tagCountMap.size(), sortedTagMap.size());
            return sortedTagMap;
            
        } catch (Exception e) {
            log.error("统计视频标签失败", e);
            return Collections.emptyMap();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSaveOrUpdateVideoByBvNum(List<Video> videoList) {
        if (videoList == null || videoList.isEmpty()) {
            log.info("视频数据列表为空，无需保存");
            return true;
        }
        
        log.info("开始批量保存视频数据，总数量：{}", videoList.size());
        
        // 分批处理，每批处理100条，避免内存溢出
        int batchSize = 100;
        int total = videoList.size();
        int processed = 0;
        int successCount = 0;
        int failCount = 0;
        
        try {
            for (int i = 0; i < total; i += batchSize) {
                int endIndex = Math.min(i + batchSize, total);
                List<Video> batchList = videoList.subList(i, endIndex);
                
                log.info("处理批次：{}-{}/{}，数量：{}", i + 1, endIndex, total, batchList.size());
                
                for (Video video : batchList) {
                    try {
                        // 检查BV号是否已存在
                        LambdaQueryWrapper<Video> queryWrapper = new LambdaQueryWrapper<>();
                        queryWrapper.eq(Video::getBvNum, video.getBvNum());
                        Video existVideo = this.getOne(queryWrapper, false);
                        
                        if (existVideo != null) {
                            // 存在则更新
                            LambdaUpdateWrapper<Video> updateWrapper = new LambdaUpdateWrapper<>();
                            updateWrapper.eq(Video::getBvNum, video.getBvNum())
                                .set(Video::getLikeCount, video.getLikeCount())
                                .set(Video::getCoinCount, video.getCoinCount())
                                .set(Video::getFavoriteCount, video.getFavoriteCount())
                                .set(Video::getDanmakuCount, video.getDanmakuCount())
                                .set(Video::getPlayCount, video.getPlayCount())
                                .set(Video::getSavePath, video.getSavePath());
                            
                            boolean updateSuccess = this.update(updateWrapper);
                            if (updateSuccess) {
                                successCount++;
                            } else {
                                failCount++;
                                log.warn("更新视频失败：{}", video.getBvNum());
                            }
                        } else {
                            // 不存在则插入
                            boolean saveSuccess = this.save(video);
                            if (saveSuccess) {
                                successCount++;
                            } else {
                                failCount++;
                                log.warn("插入视频失败：{}", video.getBvNum());
                            }
                        }
                    } catch (Exception e) {
                        failCount++;
                        log.warn("处理视频失败：{} - {}", video.getBvNum(), e.getMessage());
                    }
                }
                
                processed += batchList.size();
                
                // 每处理一批数据后，手动触发垃圾回收，减少内存占用
                if (processed % (batchSize * 10) == 0) {
                    System.gc();
                }
            }
            
            log.info("批量保存视频数据完成，成功：{}/{}，失败：{}", successCount, total, failCount);
            return failCount == 0;
            
        } catch (Exception e) {
            log.error("批量保存视频数据失败", e);
            return false;
        }
    }

    @Override
    public List<Video> getVideosByBvNums(List<String> bvNums) {
        if (bvNums == null || bvNums.isEmpty()) {
            return Collections.emptyList();
        }
        
        log.info("根据BV号列表获取视频信息，数量：{}", bvNums.size());
        
        try {
            LambdaQueryWrapper<Video> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(Video::getBvNum, bvNums);
            List<Video> videos = this.list(queryWrapper);
            log.info("获取到视频信息数量：{}", videos.size());
            return videos;
        } catch (Exception e) {
            log.error("根据BV号列表获取视频信息失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean packageVideosToZip(List<String> bvNums, OutputStream outputStream) {
        if (bvNums == null || bvNums.isEmpty() || outputStream == null) {
            return false;
        }
        
        log.info("开始打包视频文件（版本一），BV号数量：{}", bvNums.size());
        
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            byte[] buffer = new byte[8192]; // 8KB 缓冲区
            
            // 获取视频信息
            List<Video> videos = getVideosByBvNums(bvNums);
            int successCount = 0;
            
            for (Video video : videos) {
                if (video.getIsDownload() != 1 || video.getSavePath() == null || video.getSavePath().isEmpty()) {
                    log.warn("视频未下载，跳过打包：{}", video.getBvNum());
                    continue;
                }
                
                // 拼接文件路径：savePath + 文件名(格式：【文件名】-BV号.mp4)
                String fileName = "【" + video.getName() + "】-" + video.getBvNum() + ".mp4";
                String filePath = video.getSavePath() + File.separator + fileName;
                File videoFile = new File(filePath);
                
                if (!videoFile.exists() || !videoFile.isFile()) {
                    log.warn("视频文件不存在，跳过打包：{}", filePath);
                    continue;
                }
                
                // 添加文件到压缩包
                ZipEntry entry = new ZipEntry(fileName);
                zos.putNextEntry(entry);
                
                // 流式写入文件内容
                try (FileInputStream fis = new FileInputStream(videoFile)) {
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, bytesRead);
                    }
                }
                
                zos.closeEntry();
                successCount++;
                log.info("打包视频文件成功：{}", fileName);
            }
            
            log.info("视频文件打包完成（版本一），成功打包 {} 个文件", successCount);
            // 当没有成功打包的文件时，返回 false
            return successCount > 0;
            
        } catch (Exception e) {
            log.error("打包视频文件失败（版本一）", e);
            return false;
        }
    }

    @Override
    public boolean packageVideosToZip(List<String> bvNums, OutputStream outputStream, Long mid) {
        if (bvNums == null || bvNums.isEmpty() || outputStream == null) {
            return false;
        }
        
        log.info("开始打包视频文件（版本二），BV号数量：{}，UP主ID：{}", bvNums.size(), mid);
        
        // 分批打包，每批40条数据
        int batchSize = 40;
        int total = bvNums.size();
        int batchCount = (total + batchSize - 1) / batchSize;
        int successCount = 0;
        
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            byte[] buffer = new byte[8192]; // 8KB 缓冲区
            
            // 分批处理
            for (int batch = 0; batch < batchCount; batch++) {
                int startIndex = batch * batchSize;
                int endIndex = Math.min(startIndex + batchSize, total);
                List<String> batchBvNums = bvNums.subList(startIndex, endIndex);
                
                log.info("处理批次 {}/{}，BV号数量：{}", batch + 1, batchCount, batchBvNums.size());
                
                // 获取当前批次的视频信息
                List<Video> videos = getVideosByBvNums(batchBvNums);
                
                for (Video video : videos) {
                    if (video.getIsDownload() != 1 || video.getSavePath() == null || video.getSavePath().isEmpty()) {
                        log.warn("视频未下载，跳过打包：{}", video.getBvNum());
                        continue;
                    }
                    
                    // 拼接文件路径：savePath + 文件名(格式：【文件名】-BV号.mp4)
                    String fileName = "【" + video.getName() + "】-" + video.getBvNum() + ".mp4";
                    String filePath = video.getSavePath() + File.separator + fileName;
                    File videoFile = new File(filePath);
                    
                    if (!videoFile.exists() || !videoFile.isFile()) {
                        log.warn("视频文件不存在，跳过打包：{}", filePath);
                        continue;
                    }
                    
                    // 添加文件到压缩包
                    ZipEntry entry = new ZipEntry(fileName);
                    zos.putNextEntry(entry);
                    
                    // 流式写入文件内容
                    try (FileInputStream fis = new FileInputStream(videoFile)) {
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            zos.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    zos.closeEntry();
                    successCount++;
                    log.info("打包视频文件成功：{}", fileName);
                }
            }
            
            log.info("视频文件打包完成（版本二），成功打包 {} 个文件", successCount);
            // 当没有成功打包的文件时，返回 false
            return successCount > 0;
            
        } catch (Exception e) {
            log.error("打包视频文件失败（版本二）", e);
            return false;
        }
    }
}
