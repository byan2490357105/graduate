package com.billbill2.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * UP主视频数据表实体类
 */
@Data
@TableName(value = "upvideodata") // 强制指定数据库表名：upvideodata
public class UpVideoData implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * B站视频名
     */
    @TableField(value = "title")
    private String title;

    /**
     * BV号
     */
    @TableField(value = "bv_num")
    private String bv_num;

    /**
     * UP名
     */
    @TableField(value = "author")
    private String author;

    /**
     * UP主ID
     */
    @TableField(value = "mid")
    private Long mid;

    /**
     * 播放量
     * 强制绑定数据库字段：playCount
     */
    @TableField(value = "playCount")
    private Long playCount;

    /**
     * 弹幕数
     * 强制绑定数据库字段：danmakuCount
     */
    @TableField(value = "danmakuCount")
    private Long danmakuCount;

    /**
     * 点赞数
     * 强制绑定数据库字段：likeCount
     */
    @TableField(value = "likeCount")
    private Long likeCount;

    /**
     * 投币数
     * 强制绑定数据库字段：coinCount
     */
    @TableField(value = "coinCount")
    private Long coinCount;

    /**
     * 收藏数
     * 强制绑定数据库字段：favoriteCount
     */
    @TableField(value = "favoriteCount")
    private Long favoriteCount;

    /**
     * 分享数
     * 强制绑定数据库字段：shareCount
     */
    @TableField(value = "shareCount")
    private Long shareCount;

    /**
     * 视频发布时间
     * 强制绑定数据库字段：publishTime
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(value = "publishTime")
    private LocalDateTime publishTime;

    /**
     * 视频时长（秒）
     * 强制绑定数据库字段：duration
     */
    @TableField(value = "duration")
    private Integer duration;

    /**
     * 视频描述
     * 强制绑定数据库字段：videoDesc
     */
    @TableField(value = "videoDesc")
    private String videoDesc;

    /**
     * 视频标签
     * 强制绑定数据库字段：tags
     */
    @TableField(value = "tags")
    private String tags;

    /**
     * 视频封面图URL
     * 强制绑定数据库字段：coverUrl
     */
    @TableField(value = "coverUrl")
    private String coverUrl;
}
