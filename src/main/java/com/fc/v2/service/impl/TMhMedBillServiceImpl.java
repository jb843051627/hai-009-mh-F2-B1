package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TMhMedBillMapper;
import com.fc.v2.model.auto.TMhMedBill;
import com.fc.v2.service.ITMhMedBillService;

/**
 * 特殊用药签核单 Service 业务层处理（sign-flow 形状）。
 * <p>本环节逻辑尚未实现，多级签核流转待后续迭代补齐。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMhMedBillServiceImpl implements ITMhMedBillService {

    @javax.annotation.Resource
    private TMhMedBillMapper mhMedBillMapper;

    @Override
    public TMhMedBill selectTMhMedBillById(Long id) {
        return this.mhMedBillMapper.selectById(id);
    }

    @Override
    public TMhMedBill approve(Long id, String approver, String comment) {
        // 基线：签核流转尚未实现
        return null;
    }

    @Override
    public TMhMedBill reject(Long id, String approver, String comment) {
        // 基线：签核流转尚未实现
        return null;
    }

    @Override
    public TMhMedBill rollback(Long id, String comment) {
        // 基线：签核流转尚未实现
        return null;
    }
}