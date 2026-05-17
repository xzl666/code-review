package com.cmbchina.codereview.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cr_review_rule")
public class ReviewRuleEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;

    private String ruleCode;

    private String ruleKind;

    private String ruleType;

    private String severity;

    private String projectType;

    private String promptTemplate;

    private Long skillId;

    private Long scriptId;

    private Integer status;

    private Integer sortOrder;
}
