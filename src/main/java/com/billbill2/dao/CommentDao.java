package com.billbill2.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.billbill2.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentDao extends BaseMapper<Comment> {

    boolean batchUpsertComments(@Param("commentList") List<Comment> commentList);
    // 根据BV号查询所有评论（新增核心方法）
    List<Comment> selectByBvNum(@Param("bvNum") String bvNum);
}
