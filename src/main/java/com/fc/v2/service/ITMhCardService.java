package com.fc.v2.service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMhCardMapper;
import com.fc.v2.mapper.auto.TMhPatientMapper;
import com.fc.v2.model.auto.TMhCard;
import com.fc.v2.model.auto.TMhPatient;

/**
 * TMhCard 业务层处理接口
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhCardService {

    /** selectTMhCardById */
    TMhCard selectTMhCardById(Long id);

    /** selectTMhCardList */
    List<TMhCard> selectTMhCardList(Wrapper<TMhCard> queryWrapper);

    /** insertTMhCard */
    int insertTMhCard(TMhCard record);

    /** updateTMhCard */
    int updateTMhCard(TMhCard record);

    /** advanceTMhCard */
    int advanceTMhCard(Long id);

    /** revertTMhCard */
    int revertTMhCard(Long id);

    /** deleteTMhCardByIds */
    int deleteTMhCardByIds(String ids);

    /** deleteTMhCardById */
    int deleteTMhCardById(Long id);

}
