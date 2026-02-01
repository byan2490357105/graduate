package com.billbill2.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.CommentDao;
import com.billbill2.entity.Comment;
import com.billbill2.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentDao, Comment> implements CommentService {
    private Integer commentListNum=100; // MyBatis-Plus批量插入，每次100条，因为不使用baseMapper的功能，所以目前被弃用

    @Autowired
    private CommentDao commentDao;
    @Override
    @Transactional(rollbackFor = Exception.class) // 所有异常都触发回滚
    public int batchUpsertComments(List<Comment> commentList) {
        if (commentList == null || commentList.isEmpty()) {
            return 0;
        }
        boolean isSuccess = commentDao.batchUpsertComments(commentList);
        return isSuccess ? commentList.size() : 0;
    }

    @Override
    public List<Comment> getCommentByBvNum(String BvNum) {
        return commentDao.selectByBvNum(BvNum);
    }
}
