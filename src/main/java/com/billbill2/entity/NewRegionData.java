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

    private String name;

    @TableId(value = "bv_num", type = IdType.INPUT)
    private String bvNum;

    private String upName;

    private Long upId;

    private Long playCount;

    private Long likeCount;

    private Long danmukuCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishTime;

    private Integer duration;

    private Integer pidV2;
}
