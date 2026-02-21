package com.billbill2.controller;

import com.billbill2.DTO.CommentRequestDTO;
import com.billbill2.service.BZoneGetDataService;
import com.billbill2.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GetCommentController {
    private final CrawlerService crawlerService;
    private final BZoneGetDataService bZoneGetDataService;

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
            String result = crawlerService.getCommendData(commentRequestDTO);

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

    @PostMapping("batchGetZoneComment")
    @ResponseBody
    public ResponseEntity<?> batchGetZoneComment(@RequestBody Map<String, String> request) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            log.info("接收到批量爬取分区评论请求：{}", request);

            // 提取分区信息
            String zoneName = request.get("分区名称");
            String zoneId = request.get("分区ID");

            if (zoneId == null) {
                resultMap.put("code", 400);
                resultMap.put("msg", "缺少必要参数（分区ID）");
                return ResponseEntity.badRequest().body(resultMap);
            }

            // 验证分区ID
            int pidV2;
            try {
                pidV2 = Integer.parseInt(zoneId);
            } catch (NumberFormatException e) {
                resultMap.put("code", 400);
                resultMap.put("msg", "分区ID格式错误");
                return ResponseEntity.badRequest().body(resultMap);
            }

            // 调用服务获取分区的{bv号,aid}列表
            List<Map<String, String>> bvAidList = bZoneGetDataService.getBZoneAllAidAndBvNum(pidV2);

            if (bvAidList == null || bvAidList.isEmpty()) {
                resultMap.put("code", 200);
                resultMap.put("msg", "该分区暂无视频数据");
                resultMap.put("data", null);
                return ResponseEntity.ok(resultMap);
            }

            // 构建Python脚本参数
            List<String> args = new ArrayList<>();
            for (Map<String, String> bvAid : bvAidList) {
                args.add(bvAid.get("bvNum"));
                args.add(bvAid.get("aid"));
            }

            // 调用Python脚本进行批量爬取
            String result = crawlerService.batchGetCommentByAid(args);
            log.info("Python脚本执行结果：{}", result);

            // 构建成功响应
            resultMap.put("code", 200);
            resultMap.put("msg", "批量爬取任务已启动");
            resultMap.put("分区名称", zoneName);
            resultMap.put("分区ID", zoneId);
            resultMap.put("视频数量", bvAidList.size());
            resultMap.put("data", result);

            return ResponseEntity.ok(resultMap);

        } catch (Exception e) {
            log.error("批量爬取分区评论失败：{}", e.getMessage(), e);

            resultMap.put("code", 500);
            resultMap.put("msg", "服务器内部错误：" + e.getMessage());
            resultMap.put("data", null);

            return ResponseEntity.internalServerError().body(resultMap);
        }
    }
}



