package com.cmbchina.codereview.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ReviewIssueAppServiceTest {

    @Test
    void publicRepoUrlShouldConvertGiteeSshUrlToHttpsUrl() throws Exception {
        ReviewIssueAppService service = new ReviewIssueAppService(null, null, null, null);

        assertThat(publicRepoUrl(service, "git@gitee.com:zhijiantianya/ruoyi-vue-pro.git"))
            .isEqualTo("https://gitee.com/zhijiantianya/ruoyi-vue-pro");
        assertThat(publicRepoUrl(service, "ssh://git@gitee.com/zhijiantianya/ruoyi-vue-pro.git"))
            .isEqualTo("https://gitee.com/zhijiantianya/ruoyi-vue-pro");
        assertThat(publicRepoUrl(service, "https://oauth-token@gitee.com/zhijiantianya/ruoyi-vue-pro.git"))
            .isEqualTo("https://gitee.com/zhijiantianya/ruoyi-vue-pro");
    }

    private String publicRepoUrl(ReviewIssueAppService service, String repoUrl) throws Exception {
        Method method = ReviewIssueAppService.class.getDeclaredMethod("publicRepoUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, repoUrl);
    }
}
