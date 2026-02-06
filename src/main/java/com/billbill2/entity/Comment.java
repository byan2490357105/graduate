package com.billbill2.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
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
    @TableId(value = "BvNum", type = IdType.INPUT) // 逻辑主键，绑定数据库列名：bv_num
    private String BvNum;
    /**评论rpid号，有unique约束*/
    private String rpid;
}
