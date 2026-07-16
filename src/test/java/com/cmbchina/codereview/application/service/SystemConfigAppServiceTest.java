package com.cmbchina.codereview.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.cmbchina.codereview.domain.config.SystemConfigRepository;
import com.cmbchina.codereview.infrastructure.git.GiteeSshConfigValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class SystemConfigAppServiceTest {

    @Test
    void buildsCmbInternalOpenAiCompatibleEndpointFromModelName() {
        SystemConfigAppService service = new SystemConfigAppService(
            mock(SystemConfigRepository.class), new ObjectMapper(), mock(JdbcTemplate.class),
            mock(GiteeSshConfigValidator.class));
        ReflectionTestUtils.setField(service, "cmbInternalBaseUrl", "http://open-llm.uat.cmbchina.cn/llm/");

        String url = service.buildCmbInternalUrl("cmb-code-model");

        assertEquals("http://open-llm.uat.cmbchina.cn/llm/cmb-code-model/v1/chat/completions", url);
    }
}
