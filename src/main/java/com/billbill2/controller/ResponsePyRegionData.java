package com.billbill2.controller;

import com.billbill2.entity.Comment;
import com.billbill2.entity.RegionData;
import com.billbill2.service.BZoneAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/api/bilibili/regiondata")
public class ResponsePyRegionData {
    @Autowired
    private BZoneAnalysisService bZoneAnalysisService;

    @PostMapping("/batch-save")
    public ResponseEntity<Map<String, Object>> batchSave(@RequestBody List<RegionData> regionDataList) {
        // 新建 HashMap 存储返回结果（替换 Map.of()）
        Map<String, Object> resultMap = new HashMap<>();
        System.out.println(regionDataList.size());
        try {

            // 调用Service批量入库
            int successCount = bZoneAnalysisService.batchUpsertRegionData(regionDataList);

            // 返回成功结果（和Python端的result.get("code")匹配）
            // 成功结果：用 HashMap.put() 替代 Map.of()
            resultMap.put("code", 200);
            resultMap.put("msg", "批量入库成功");
            resultMap.put("successCount", successCount);

        } catch (Exception e) {
            // 异常返回（Python端会捕获此错误信息）
            // 失败结果：用 HashMap.put() 替代 Map.of()
            resultMap.put("code", 500);
            resultMap.put("msg", "批量入库失败：" + e.getMessage());
            resultMap.put("successCount", 0);
        }
        return ResponseEntity.ok(resultMap);
    }

}
