package com.billbill2.DTO;

import lombok.Data;

// 若未使用lombok，需手动添加getter/setter方法
@Data
public class CommentRequestDTO {
    // 对应前端 bvNum ，并且符合驼峰命名规则，如果不符合驼峰命名，lombok可能不会生成正确的get/set方法
    private String bvNum;

    private Integer startPage;

    private Integer endPage;
}