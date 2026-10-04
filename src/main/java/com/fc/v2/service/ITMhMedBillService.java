package com.fc.v2.service;

import com.fc.v2.mapper.auto.TMhMedBillMapper;
import com.fc.v2.model.auto.TMhMedBill;

/**
 * TMhMedBill 业务层处理接口
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITMhMedBillService {

    /** selectTMhMedBillById */
    TMhMedBill selectTMhMedBillById(Long id);

    /** approve */
    TMhMedBill approve(Long id, String approver, String comment);

    /** reject */
    TMhMedBill reject(Long id, String approver, String comment);

    /** rollback */
    TMhMedBill rollback(Long id, String comment);

}
