package com.billbill2.controller;

import com.billbill2.service.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/tag")
public class TagCloudController {

    @Autowired
    private VideoService videoService;

    /**
     * 跳转到标签词云页面
     * @return 标签词云页面
     */
    @GetMapping("/toWorldCloud")
    public String toWorldCloud() {
        return "worldcloud";
    }

    /**
     * 获取标签统计数据
     * @param limit 限制返回的标签数量，默认100
     * @return 标签统计数据，格式：{"标签名": 出现次数}
     */
    @GetMapping("/getTagsStatistics")
    @ResponseBody
    public Map<String, Integer> getTagsStatistics(@RequestParam(defaultValue = "100") int limit) {
        return videoService.getVideoTagsStatistics(limit);
    }
}
