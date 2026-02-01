package com.billbill2.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.billbill2.entity.Comment;

import java.util.List;

public interface CommentService extends IService<Comment> {
    /**
     * 批量插入B站评论
     * @param commentList 评论列表
     * @return 成功插入的数量
     * 实现增量更新 有则不变 无则插入 避免触发唯一索引报错
     */
    int batchUpsertComments(List<Comment> commentList);

    public List<Comment> getCommentByBvNum(String BvNum);
}
