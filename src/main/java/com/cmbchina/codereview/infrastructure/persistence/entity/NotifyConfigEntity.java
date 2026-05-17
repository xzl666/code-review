package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_notify_config")
public class NotifyConfigEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configName;

    private String channelType;

    private String webhookUrl;

    private String secretEncrypt;

    private Integer enabled;
}
