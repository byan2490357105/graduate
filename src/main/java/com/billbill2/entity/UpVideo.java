package com.billbill2.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("upvideo")
public class UpVideo {
    private Integer comment; // 评论数

    private Integer typeid; // 视频类型

    private Long play; // 播放量

    private String title; // 视频标题

    private String author; // 作者名称

    private Long mid; // 作者ID

    private Long aid; // 视频AID

    @TableField("bv_num")
    private String bvNum; // BV号

    private LocalDateTime created; // 发布时间

    private String length; // 视频时长（格式：分:秒）
}
