package com.billbill2.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.billbill2.entity.NewRegionData;

import java.util.List;
import java.util.Map;

public interface BZoneGetDataService extends IService<NewRegionData> {
    /**
     * 批量获取B站指定分区的视频播放数据
     * @param regionDataList 视频数据列表
     * @return 成功插入的数量
     * 实现增量更新 有则不变 无则插入 避免触发唯一索引报错
     */
    int batchUpsertNewRegionData(List<NewRegionData> DataList);

    /**
     * 根据分区ID（pid_v2），获取该分区每条视频的{bv号,aid}
     * @param pidV2 分区ID
     * @return {bv号,aid}列表
     */
    List<Map<String, String>> getBZoneAllAidAndBvNum(Integer pidV2);
}
