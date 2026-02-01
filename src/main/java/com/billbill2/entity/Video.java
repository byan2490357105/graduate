package com.billbill2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 视频表实体类
 * 强制绑定数据库表每个字段，字段名1:1精准映射（无视驼峰配置）
 */
@Data
@TableName(value = "video") // 强制指定数据库表名：video
public class Video implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 视频主键ID
     * 强制绑定数据库字段：id
     */
    @TableId(value = "id", type = IdType.AUTO) // 强制绑定主键字段id，自增策略
    private Long id;

    /**
     * B站视频名
     * 强制绑定数据库字段：name
     */
    @TableField(value = "name")
    private String name;

    /**
     * BV号
     * 强制绑定数据库字段：BvNum
     */
    @JsonProperty("BvNum")
    @TableField(value = "BvNum")
    private String BvNum;

    /**
     * UP名
     * 强制绑定数据库字段：upName
     */
    @TableField(value = "upName")
    private String upName;

    /**
     * UP主ID
     * 强制绑定数据库字段：upId
     */
    @TableField(value = "upId")
    private Long upId;

    /**
     * 保存路径
     * 强制绑定数据库字段：savePath
     */
    @TableField(value = "savePath")
    private String savePath;

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

    /**
     * 视频文件大小（字节）
     * 强制绑定数据库字段：fileSize
     */
    @TableField(value = "fileSize")
    private Long fileSize;

    /**
     * 是否下载（建议：0=未下载，1=已下载，与你的下载状态对应）
     */
    @TableField(value = "isDownload")
    private Integer isDownload;

    /**
     * 入库时间
     */
    @TableField(value = "createTime")
    private Date createTime;
}
