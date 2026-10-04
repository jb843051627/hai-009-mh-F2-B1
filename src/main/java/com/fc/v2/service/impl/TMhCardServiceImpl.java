package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TMhCardMapper;
import com.fc.v2.model.auto.TMhCard;
import com.fc.v2.service.ITMhCardService;

/**
 * 就诊档案卡 Service 业务层处理（crud 形状）。
 * <p>本环节逻辑尚未实现，档案卡建档与导出过滤待后续迭代补齐。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhCardServiceImpl implements ITMhCardService {

    @javax.annotation.Resource
    private TMhCardMapper mhCardMapper;

    @Override
    public TMhCard selectTMhCardById(Long id) {
        return this.mhCardMapper.selectById(id);
    }

    @Override
    public List<TMhCard> selectTMhCardList(Wrapper<TMhCard> queryWrapper) {
        return this.mhCardMapper.selectList(queryWrapper);
    }

    @Override
    public int insertTMhCard(TMhCard record) {
        // 基线：建档尚未实现
        return 0;
    }

    @Override
    public int updateTMhCard(TMhCard record) {
        // 基线：改档尚未实现
        return 0;
    }

    @Override
    public int advanceTMhCard(Long id) {
        // 基线：环节推进尚未实现
        return 0;
    }

    @Override
    public int revertTMhCard(Long id) {
        // 基线：环节回退尚未实现
        return 0;
    }

    @Override
    public int deleteTMhCardByIds(String ids) {
        // 基线：删除尚未实现
        return 0;
    }

    @Override
    public int deleteTMhCardById(Long id) {
        // 基线：删除尚未实现
        return 0;
    }
}