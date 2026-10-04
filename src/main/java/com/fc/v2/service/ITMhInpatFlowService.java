package com.fc.v2.service;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhInpatFlowMapper;
import com.fc.v2.model.auto.TMhInpatFlow;

/**
 * TMhInpatFlow 业务层处理接口
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhInpatFlowService {

    /** selectTMhInpatFlowById */
    TMhInpatFlow selectTMhInpatFlowById(Long id);

    /** selectTMhInpatFlowList */
    List<TMhInpatFlow> selectTMhInpatFlowList(QueryWrapper<TMhInpatFlow> queryWrapper);

    /** advance */
    TMhInpatFlow advance(Long id, String remark);

    /** rollback */
    TMhInpatFlow rollback(Long id, String remark);

    /** updateContent */
    boolean updateContent(Long id, String remark);

    /** remove */
    boolean remove(Long id);

}
