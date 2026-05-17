package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_operation_log")
public class OperationLogEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String operator;

    private String operationType;

    private String targetType;

    private Long targetId;

    private String requestContent;

    private String resultContent;
}
