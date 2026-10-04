package com.fc.v2.service;

import java.util.Date;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhRegTaskMapper;
import com.fc.v2.model.auto.TMhRegTask;

/**
 * TMhRegTask 业务层处理接口
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhRegTaskService {

    /** selectTMhRegTaskById */
    TMhRegTask selectTMhRegTaskById(Long id);

    /** listDue */
    List<TMhRegTask> listDue(Date at);

    /** runOnce */
    int runOnce(Date at);

}
