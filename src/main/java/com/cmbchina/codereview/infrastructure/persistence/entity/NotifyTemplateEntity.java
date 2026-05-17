package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_notify_template")
public class NotifyTemplateEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String templateName;

    private String templateCode;

    private String channelType;

    private String eventType;

    private String templateContent;

    private Integer enabled;
}
