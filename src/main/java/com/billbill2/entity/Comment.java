package com.billbill2.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName(value = "comment")
public class Comment {
    /** 用户昵称 */
    private String name;
    /** 性别 */
    private String sex;
    /** 地区 */
    private String area;
    /** 评论内容 */
    private String comment;
    /** 视频BV号 */
    @TableField("BvNum")
    private String BvNum;
    /**评论rpid号，有unique约束*/
    private String rpid;
}
