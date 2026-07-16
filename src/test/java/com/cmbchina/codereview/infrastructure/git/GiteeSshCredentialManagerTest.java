package com.cmbchina.codereview.infrastructure.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cmbchina.codereview.application.service.SystemConfigAppService;
import org.junit.jupiter.api.Test;

class GiteeSshCredentialManagerTest {

    @Test
    void shouldNormalizePrivateKeyToLfOnEveryOperatingSystem() {
        String normalized = SshPrivateKeyFileSupport.normalizeContent("line1\r\nline2\r\n");

        assertEquals("line1\nline2\n", normalized);
        assertFalse(normalized.contains("\r"));
    }

    @Test
    void shouldNormalizeRelativeAndHttpsRepositoryUrlsToSsh() {
        SystemConfigAppService configService = mock(SystemConfigAppService.class);
        when(configService.getGiteeBaseUrl()).thenReturn("https://gitee.internal.example");
        GiteeSshCredentialManager manager = new GiteeSshCredentialManager(configService);

        assertEquals("git@gitee.internal.example:team/service.git", manager.normalizeRepositoryUrl("team/service"));
        assertEquals("git@gitee.com:team/service.git", manager.normalizeRepositoryUrl("https://gitee.com/team/service.git"));
        assertEquals("git@gitee.com:team/service.git", manager.normalizeRepositoryUrl("git@gitee.com:team/service.git"));
    }
}
