package com.billbill2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.BZoneAnalysisMapper;
import com.billbill2.entity.RegionData;
import com.billbill2.service.BZoneAnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BZoneAnalysisServiceImpl extends ServiceImpl<BZoneAnalysisMapper, RegionData> implements BZoneAnalysisService {
    @Override
    @Transactional(rollbackFor = Exception.class) // 批量操作加事务，确保原子性
    public int batchUpsertRegionData(List<RegionData> regionDataList) {
        // 1. 前置校验：列表为空直接返回0
        if (CollectionUtils.isEmpty(regionDataList)) {
            return 0;
        }

        // 2. 提取待批量处理的所有 BVNum（去重，避免无效查询）
        List<String> bvNumList = regionDataList.stream()
                .map(RegionData::getBvNum)
                .filter(bvNum -> bvNum != null && !bvNum.trim().isEmpty()) // 过滤空 BVNum
                .distinct()
                .collect(Collectors.toList());
        System.out.println("bvNumListSize:"+bvNumList.size());

        if (CollectionUtils.isEmpty(bvNumList)) {
            throw new IllegalArgumentException("批量数据中有 BV 号为空");
        }

        //3.进行存在则更新，不存在则增加操作,如果执行成功返回影响的条数，失败返回0
        if(this.saveOrUpdateBatch(regionDataList))
            return regionDataList.size();
        else
            return 0;
    }
}
