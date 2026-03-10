package com.billbill2.controller;

import com.billbill2.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/comment")
public class CommentWordCloudController {

    @Autowired
    private CommentService commentService;

    @GetMapping("/getCommentWordStatistics")
    public Map<String, Integer> getCommentWordStatistics(@RequestParam(defaultValue = "100") int limit) {
        return commentService.getCommentWordStatistics(limit);
    }
}
