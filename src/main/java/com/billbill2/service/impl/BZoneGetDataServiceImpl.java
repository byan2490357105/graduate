package com.billbill2.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.BZoneDataMapper;
import com.billbill2.entity.NewRegionData;
import com.billbill2.service.BZoneGetDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BZoneGetDataServiceImpl extends ServiceImpl<BZoneDataMapper,NewRegionData> implements BZoneGetDataService {

    @Override
    @Transactional(rollbackFor = Exception.class) // 批量操作加事务，确保原子性
    public int batchUpsertNewRegionData(List<NewRegionData> DataList) {
        // 1. 前置校验：列表为空直接返回0
        if (CollectionUtils.isEmpty(DataList)) {
            return 0;
        }

        // 2. 提取待批量处理的所有 BVNum（去重，避免无效查询）
        List<String> bvNumList = DataList.stream()
                .map(NewRegionData::getBvNum)
                .filter(bvNum -> bvNum != null && !bvNum.trim().isEmpty()) // 过滤空 BVNum
                .distinct()
                .collect(Collectors.toList());
        System.out.println("bvNumListSize:"+bvNumList.size());

        if (CollectionUtils.isEmpty(bvNumList)) {
            throw new IllegalArgumentException("批量数据中有 BV 号为空");
        }

        //3.进行存在则更新，不存在则增加操作,如果执行成功返回影响的条数，失败返回0
        if(this.saveOrUpdateBatch(DataList))
            return DataList.size();
        else
            return 0;
    }
}
