package com.cmbchina.codereview.interfaces.dto.request;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class ZhaohuTestSendRequest {
    @NotEmpty(message = "请选择接收人员")
    private List<String> userIds;
    @NotBlank(message = "标题不能为空")
    private String title;
    @NotBlank(message = "内容不能为空")
    private String content;
    private String summary;
}
