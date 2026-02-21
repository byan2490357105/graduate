package com.billbill2.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.billbill2.entity.NewRegionData;
import com.billbill2.service.BZoneAnalysisService;
import com.billbill2.service.BZoneGetDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
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

    @Autowired
    private BZoneGetDataService bZoneGetDataService;

    @PostMapping("/batch-save")
    public ResponseEntity<Map<String, Object>> batchSave(@RequestBody List<NewRegionData> newregionDataList) {
        // 新建 HashMap 存储返回结果（替换 Map.of()）
        Map<String, Object> resultMap = new HashMap<>();
        System.out.println(newregionDataList.size());
        try {

            // 调用Service批量入库
            int successCount = bZoneGetDataService.batchUpsertNewRegionData(newregionDataList);

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

    @GetMapping("/query-bvnums")
    public ResponseEntity<Map<String, Object>> queryBvnums() {
        // 新建 HashMap 存储返回结果
        Map<String, Object> resultMap = new HashMap<>();
        try {
            // 调用Service查询所有BV号
            List<NewRegionData> allRegionData = bZoneAnalysisService.list();
            
            // 提取所有BV号
            List<String> bvnums = allRegionData.stream()
                    .map(NewRegionData::getBvNum)
                    .filter(bvNum -> bvNum != null && !bvNum.trim().isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
            
            // 返回成功结果
            resultMap.put("code", 200);
            resultMap.put("msg", "查询成功");
            resultMap.put("bvnums", bvnums);

        } catch (Exception e) {
            // 异常返回
            resultMap.put("code", 500);
            resultMap.put("msg", "查询失败：" + e.getMessage());
            resultMap.put("bvnums", new ArrayList<>());
        }
        return ResponseEntity.ok(resultMap);
    }

    @PostMapping("/query-max")
    public ResponseEntity<Map<String, Object>> queryMaxData(@RequestBody Map<String, Object> request) {
        // 新建 HashMap 存储返回结果
        Map<String, Object> resultMap = new HashMap<>();
        try {
            // 获取请求参数
            Integer pidV2 = (Integer) request.get("pidV2");
            String sortField = (String) request.get("sortField");
            
            if (pidV2 == null || sortField == null || sortField.isEmpty()) {
                resultMap.put("code", 400);
                resultMap.put("msg", "参数错误：pidV2和sortField不能为空");
                resultMap.put("data", null);
                return ResponseEntity.ok(resultMap);
            }
            
            // 构建查询条件
            LambdaQueryWrapper<NewRegionData> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(NewRegionData::getPidV2, pidV2);
            
            // 根据sortField字段降序排序
            switch (sortField) {
                case "playCount":
                    queryWrapper.orderByDesc(NewRegionData::getPlayCount);
                    break;
                case "likeCount":
                    queryWrapper.orderByDesc(NewRegionData::getLikeCount);
                    break;
                case "danmukuCount":
                    queryWrapper.orderByDesc(NewRegionData::getDanmukuCount);
                    break;
//                case "replyCount":
//                    queryWrapper.orderByDesc(NewRegionData::getReplyCount);
//                    break;
//                case "favoriteCount":
//                    queryWrapper.orderByDesc(NewRegionData::getFavoriteCount);
//                    break;
//                case "coinCount":
//                    queryWrapper.orderByDesc(NewRegionData::getCoinCount);
//                    break;
//                case "shareCount":
//                    queryWrapper.orderByDesc(NewRegionData::getShareCount);
//                    break;
                default:
                    resultMap.put("code", 400);
                    resultMap.put("msg", "参数错误：不支持的sortField字段");
                    resultMap.put("data", null);
                    return ResponseEntity.ok(resultMap);
            }
            
            // 查询第一条记录（最大值）
            queryWrapper.last("LIMIT 1");
            NewRegionData regionData = bZoneAnalysisService.getOne(queryWrapper);
            
            // 返回成功结果
            resultMap.put("code", 200);
            resultMap.put("msg", "查询成功");
            resultMap.put("data", regionData);

        } catch (Exception e) {
            // 异常返回
            resultMap.put("code", 500);
            resultMap.put("msg", "查询失败：" + e.getMessage());
            resultMap.put("data", null);
        }
        return ResponseEntity.ok(resultMap);
    }

    @PostMapping("/query-statistics")
    public ResponseEntity<Map<String, Object>> queryStatistics(@RequestBody Map<String, Object> request) {
        // 新建 HashMap 存储返回结果
        Map<String, Object> resultMap = new HashMap<>();
        try {
            // 获取请求参数
            Integer pidV2 = (Integer) request.get("pidV2");
            
            if (pidV2 == null) {
                resultMap.put("code", 400);
                resultMap.put("msg", "参数错误：pidV2不能为空");
                resultMap.put("data", null);
                return ResponseEntity.ok(resultMap);
            }
            
            // 调用Service查询统计信息
            Map<String, Object> statistics = bZoneAnalysisService.getZoneStatistics(pidV2);
            
            // 返回成功结果
            resultMap.put("code", 200);
            resultMap.put("msg", "查询成功");
            resultMap.put("data", statistics);

        } catch (Exception e) {
            // 异常返回
            resultMap.put("code", 500);
            resultMap.put("msg", "查询失败：" + e.getMessage());
            resultMap.put("data", null);
        }
        return ResponseEntity.ok(resultMap);
    }

    @PostMapping("/query-all-statistics")
    public ResponseEntity<Map<String, Object>> queryAllStatistics(@RequestBody Map<String, Object> request) {
        // 新建 HashMap 存储返回结果
        Map<String, Object> resultMap = new HashMap<>();
        try {
            // 获取请求参数
            List<Integer> pidV2List = (List<Integer>) request.get("pidV2List");
            
            if (pidV2List == null || pidV2List.isEmpty()) {
                resultMap.put("code", 400);
                resultMap.put("msg", "参数错误：pidV2List不能为空");
                resultMap.put("data", null);
                return ResponseEntity.ok(resultMap);
            }
            
            // 调用Service查询所有分区的统计信息
            List<Map<String, Object>> statisticsList = bZoneAnalysisService.getAllZonesStatistics(pidV2List);
            
            // 返回成功结果
            resultMap.put("code", 200);
            resultMap.put("msg", "查询成功");
            resultMap.put("data", statisticsList);

        } catch (Exception e) {
            // 异常返回
            resultMap.put("code", 500);
            resultMap.put("msg", "查询失败：" + e.getMessage());
            resultMap.put("data", null);
        }
        return ResponseEntity.ok(resultMap);
    }

    @PostMapping("/query-data-count")
    public ResponseEntity<Map<String, Object>> queryDataCount(@RequestBody Map<String, Object> request) {
        // 新建 HashMap 存储返回结果
        Map<String, Object> resultMap = new HashMap<>();
        try {
            // 获取请求参数
            List<Integer> pidV2List = (List<Integer>) request.get("pidV2List");
            
            if (pidV2List == null || pidV2List.isEmpty()) {
                resultMap.put("code", 400);
                resultMap.put("msg", "参数错误：pidV2List不能为空");
                resultMap.put("data", null);
                return ResponseEntity.ok(resultMap);
            }
            
            // 查询每个分区的数据条数
            List<Map<String, Object>> countList = new ArrayList<>();
            for (Integer pidV2 : pidV2List) {
                int count = bZoneAnalysisService.getZoneDataCount(pidV2);
                Map<String, Object> countMap = new HashMap<>();
                countMap.put("pidV2", pidV2);
                countMap.put("count", count);
                countList.add(countMap);
            }
            
            // 返回成功结果
            resultMap.put("code", 200);
            resultMap.put("msg", "查询成功");
            resultMap.put("data", countList);

        } catch (Exception e) {
            // 异常返回
            resultMap.put("code", 500);
            resultMap.put("msg", "查询失败：" + e.getMessage());
            resultMap.put("data", null);
        }
        return ResponseEntity.ok(resultMap);
    }
}
