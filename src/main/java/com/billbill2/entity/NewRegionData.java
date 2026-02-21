package com.billbill2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "newregiondata")
public class NewRegionData {

    //视频名
    private String name;

    //Bv号
    @TableId(value = "bv_num", type = IdType.INPUT)
    private String bvNum;

    //up主名称
    private String upName;

    //upID（也即Mid)
    private Long upId;

    //获取视频评论用到的aid（也即oid)
    private Long aid;

    //播放量
    private Long playCount;

    //点赞数
    private Long likeCount;

    //弹幕数
    private Long danmukuCount;

    //发布时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    //视频时长
    private Integer duration;

    //视频分区号
    private Integer pidV2;
}
