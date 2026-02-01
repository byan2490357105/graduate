package com.billbill2.controller;

import com.billbill2.DTO.CommentRequestDTO;
import com.billbill2.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GetCommentController {
    private final CrawlerService crawlerService;

    @GetMapping("toGetComment") //无@ResponseBody，返回视图，跳转页面
    public String toGetComment(){
        return "GetComment";
    }

    @PostMapping("getComment")
    @ResponseBody // 关键：加此注解，返回值转为JSON
    public ResponseEntity<?> getComment(@RequestBody CommentRequestDTO commentRequestDTO) throws Exception{
        //新建 HashMap 存储返回结果（替换 Map.of()）
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String result = crawlerService.crawlAndSave(commentRequestDTO);

            // 成功时设置返回值
            resultMap.put("code", 200);
            resultMap.put("msg", "执行成功");
            resultMap.put("data", result);
        } catch (Exception e) {
            log.error("调用Python脚本失败", e);
            // 失败时设置返回值
            resultMap.put("code", 500);
            resultMap.put("msg", "执行失败：" + e.getMessage());
            resultMap.put("data", null);
        }
        // 返回构建好的 Map
        return ResponseEntity.ok(resultMap);
    }
}



