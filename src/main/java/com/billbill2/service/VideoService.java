package com.billbill2.service;

import com.billbill2.entity.Video;

import java.util.List;
import java.util.Map;

public interface VideoService {

    /**
     * 保存或更新视频数据（基于BV号唯一索引）
     * @param video 视频数据
     * @return 执行成功与否
     */
    boolean saveOrUpdateVideoByBvNum(Video video);

    /**
     * 清空视频表所有数据，并重置自增主键ID为1
     * @return 操作是否成功
     */
    boolean clearVideoTableAndResetId();

    /**
     * 获取视频标签统计数据
     * @param limit 限制返回的标签数量
     * @return 标签统计数据，格式：{"标签名": 出现次数}
     */
    Map<String, Integer> getVideoTagsStatistics(int limit);

    /**
     * 批量保存或更新视频数据（基于BV号唯一索引）
     * @param videoList 视频数据列表
     * @return 执行成功与否
     */
    boolean batchSaveOrUpdateVideoByBvNum(List<Video> videoList);

}
