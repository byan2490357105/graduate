package com.billbill2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * B站分区视频数据实体类（对应数据库表 regiondata）
 * @author （可补充作者信息）
 */
@Data  // 自动生成 Getter/Setter、toString、equals、hashCode 等方法
@TableName(value = "regiondata")
public class RegionData {

    /**
     * B站视频名
     */
    private String name;

    /**
     * BV号
     */
    @TableId(value = "bv_num", type = IdType.INPUT) // 逻辑主键，绑定数据库列名：bv_num
    private String bvNum;

    /**
     * UP名
     */
    private String upName;

    /**
     * UP主ID(mid)
     */
    private Long upId;

    /**
     * 播放量
     */
    private Long playCount;

    /**
     * 点赞数
     */
    private Long likeCount;

    /**
     * 弹幕数
     */
    private Long danmukuCount;

    /**
     * 评论数
     */
    private Long replyCount;

    /**
     * 收藏数
     */
    private Long favoriteCount;

    /**
     * 投币数
     */
    private Long coinCount;

    /**
     * 分享数
     */
    private Long shareCount;

    /**
     * 视频发布时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    /**
     * 视频时长
     */
    private Integer duration;

    /**
     * 分区名称
     */
    private String pidNameV2;

    /**
     * 分区编号
     */
    private Integer pidV2;

    /**
     * 二级分区
     */
    private String tname;

    /**
     * 二级分区编号
     */
    private Integer tidV2;
}