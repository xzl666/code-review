package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_ai_skill")
public class AiSkillEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String skillName;

    private String skillCode;

    private String functionName;

    private String functionDescription;

    private String parametersSchema;

    private String version;

    private Integer status;
}
