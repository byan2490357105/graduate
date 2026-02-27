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
import java.util.*;
import java.util.stream.Collectors;

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
    public boolean batchSaveOrUpdateVideoByBvNum(List<Video> videoList) {
        if (videoList == null || videoList.isEmpty()) {
            log.info("视频数据列表为空，无需保存");
            return true;
        }
        
        log.info("开始批量保存视频数据，总数量：{}", videoList.size());
        
        // 设置默认值：是否下载为0，文件大小为0，保存路径为空
        for (Video video : videoList) {
            video.setIsDownload(0);
            video.setFileSize(0L);
            video.setSavePath("");
        }
        
        // 分批处理，每批处理100条，避免内存溢出
        int batchSize = 100;
        int total = videoList.size();
        int processed = 0;
        
        try {
            for (int i = 0; i < total; i += batchSize) {
                int endIndex = Math.min(i + batchSize, total);
                List<Video> batchList = videoList.subList(i, endIndex);
                
                log.info("处理批次：{}-{}/{}，数量：{}", i + 1, endIndex, total, batchList.size());
                
                // 使用MyBatis-Plus的批量保存或更新方法
                boolean success = this.saveOrUpdateBatch(batchList);
                if (!success) {
                    log.error("批次保存失败：{}-{}", i + 1, endIndex);
                    return false;
                }
                
                processed += batchList.size();
                
                // 每处理一批数据后，手动触发垃圾回收，减少内存占用
                if (processed % (batchSize * 10) == 0) {
                    System.gc();
                }
            }
            
            log.info("批量保存视频数据完成，成功处理：{}/{}", processed, total);
            return true;
            
        } catch (Exception e) {
            log.error("批量保存视频数据失败", e);
            return false;
        }
    }
}
