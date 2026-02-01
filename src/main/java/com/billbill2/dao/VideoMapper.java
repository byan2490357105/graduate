package com.billbill2.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.billbill2.entity.Video;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

//实习学的，不编写mapper.xml文件不写SQL语句实现数据库操作
@Mapper
public interface VideoMapper extends BaseMapper<Video> {

    /**
     * 执行TRUNCATE TABLE清空表并重置自增ID（推荐）
     * TRUNCATE会直接删除表数据并重置AUTO_INCREMENT=1，效率远高于DELETE
     */
    @Update("TRUNCATE TABLE video")
    void truncateVideoTable();

    /**
     * 备用方案：重置视频表自增主键为1（需先执行DELETE删除数据）
     * 仅当TRUNCATE权限不足时使用
     */
    @Update("ALTER TABLE video AUTO_INCREMENT = 1")
    void resetVideoTableAutoIncrement();

}
