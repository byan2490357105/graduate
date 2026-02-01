package com.billbill2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.VideoMapper;
import com.billbill2.entity.Video;
import com.billbill2.service.VideoService;
import org.apache.ibatis.session.SqlSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {
    @Resource
    private SqlSession sqlSession;

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
}
