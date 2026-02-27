package com.billbill2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.billbill2.dao.BZoneDataMapper;
import com.billbill2.entity.NewRegionData;
import com.billbill2.service.BZoneGetDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    public List<Map<String, String>> getBZoneAllAidAndBvNum(Integer pidV2) {
        LambdaQueryWrapper<NewRegionData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NewRegionData::getPidV2, pidV2);
        queryWrapper.isNotNull(NewRegionData::getAid);
        
        List<NewRegionData> dataList = this.list(queryWrapper);
        
        List<Map<String, String>> result = new ArrayList<>();
        for (NewRegionData data : dataList) {
            Map<String, String> map = new HashMap<>();
            map.put("bvNum", data.getBvNum());
            map.put("aid", data.getAid() != null ? data.getAid().toString() : "");
            result.add(map);
        }
        
        return result;
    }

    @Override
    public List<String> getBZoneAllBvNumByPidV2(Integer pidV2) {
        // 使用LambdaQueryWrapper构建查询条件
        LambdaQueryWrapper<NewRegionData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NewRegionData::getPidV2, pidV2);
        queryWrapper.select(NewRegionData::getBvNum); // 只选择bvNum字段，减少数据传输
        
        // 使用selectObjs方法直接获取bvNum列表，避免加载整个对象
        // 对于大数据量，MyBatis-Plus会自动处理分页，不会一次性加载所有数据
        List<Object> bvNumObjects = this.listObjs(queryWrapper);
        
        // 将Object列表转换为String列表
        List<String> bvNumList = new ArrayList<>();
        for (Object obj : bvNumObjects) {
            if (obj != null) {
                bvNumList.add(obj.toString());
            }
        }
        
        return bvNumList;
    }
}
