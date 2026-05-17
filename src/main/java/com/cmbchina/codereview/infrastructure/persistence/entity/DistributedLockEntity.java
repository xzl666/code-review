package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_distributed_lock")
public class DistributedLockEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String lockKey;

    private String lockOwner;

    private LocalDateTime expireTime;
}
