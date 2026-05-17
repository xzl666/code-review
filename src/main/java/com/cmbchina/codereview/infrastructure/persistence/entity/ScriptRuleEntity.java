package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_script_rule")
public class ScriptRuleEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scriptName;

    private String scriptCode;

    private String scriptLanguage;

    private String scriptContent;

    private String parameterTemplate;

    private Integer timeoutSeconds;

    private Integer generatedByAi;

    private Integer status;
}
