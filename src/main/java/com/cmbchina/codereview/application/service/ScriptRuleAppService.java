package com.cmbchina.codereview.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cmbchina.codereview.common.enums.BaseStatus;
import com.cmbchina.codereview.common.exception.BizException;
import com.cmbchina.codereview.common.exception.ErrorCode;
import com.cmbchina.codereview.common.response.PageResponse;
import com.cmbchina.codereview.infrastructure.persistence.entity.ScriptRuleEntity;
import com.cmbchina.codereview.infrastructure.persistence.mapper.ScriptRuleMapper;
import com.cmbchina.codereview.interfaces.dto.request.ScriptCreateRequest;
import com.cmbchina.codereview.interfaces.dto.request.ScriptPageRequest;
import com.cmbchina.codereview.interfaces.dto.request.ScriptTestRunRequest;
import com.cmbchina.codereview.interfaces.dto.request.ScriptUpdateRequest;
import com.cmbchina.codereview.interfaces.dto.response.ScriptResponse;
import com.cmbchina.codereview.interfaces.dto.response.ScriptTestRunResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ScriptRuleAppService {

    private final ScriptRuleMapper scriptRuleMapper;

    public ScriptRuleAppService(ScriptRuleMapper scriptRuleMapper) {
        this.scriptRuleMapper = scriptRuleMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ScriptCreateRequest request) {
        ScriptRuleEntity entity = new ScriptRuleEntity();
        entity.setScriptName(request.getScriptName());
        entity.setScriptCode(request.getScriptCode());
        entity.setScriptLanguage(normalizeLanguage(request.getScriptLanguage()));
        entity.setScriptContent(request.getScriptContent());
        entity.setParameterTemplate(request.getParameterTemplate());
        entity.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 30 : request.getTimeoutSeconds());
        entity.setGeneratedByAi(request.getGeneratedByAi() == null ? 0 : request.getGeneratedByAi());
        entity.setStatus(BaseStatus.ENABLED.getValue());
        scriptRuleMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ScriptUpdateRequest request) {
        ensureExists(request.getId());
        ScriptRuleEntity entity = new ScriptRuleEntity();
        entity.setId(request.getId());
        entity.setScriptName(request.getScriptName());
        entity.setScriptCode(request.getScriptCode());
        entity.setScriptLanguage(normalizeLanguage(request.getScriptLanguage()));
        entity.setScriptContent(request.getScriptContent());
        entity.setParameterTemplate(request.getParameterTemplate());
        entity.setTimeoutSeconds(request.getTimeoutSeconds() == null ? 30 : request.getTimeoutSeconds());
        entity.setGeneratedByAi(request.getGeneratedByAi() == null ? 0 : request.getGeneratedByAi());
        entity.setStatus(request.getStatus() == null ? BaseStatus.ENABLED.getValue() : request.getStatus());
        scriptRuleMapper.updateById(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensureExists(id);
        scriptRuleMapper.deleteById(id);
    }

    public ScriptResponse detail(Long id) {
        return toResponse(ensureExists(id));
    }

    public PageResponse<ScriptResponse> page(ScriptPageRequest request) {
        long pageNo = request.getPageNo() == null ? 1L : request.getPageNo();
        long pageSize = request.getPageSize() == null ? 10L : request.getPageSize();
        LambdaQueryWrapper<ScriptRuleEntity> wrapper = new LambdaQueryWrapper<ScriptRuleEntity>()
            .like(StringUtils.hasText(request.getScriptName()), ScriptRuleEntity::getScriptName, request.getScriptName())
            .eq(StringUtils.hasText(request.getScriptLanguage()), ScriptRuleEntity::getScriptLanguage, request.getScriptLanguage())
            .eq(request.getStatus() != null, ScriptRuleEntity::getStatus, request.getStatus())
            .orderByDesc(ScriptRuleEntity::getCreateTime);
        Page<ScriptRuleEntity> page = scriptRuleMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ScriptResponse> records = page.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), pageNo, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        updateStatus(id, BaseStatus.ENABLED.getValue());
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        updateStatus(id, BaseStatus.DISABLED.getValue());
    }

    public ScriptTestRunResponse testRun(ScriptTestRunRequest request) {
        Integer timeoutSeconds = request.getTimeoutSeconds() == null ? 10 : request.getTimeoutSeconds();
        String language;
        String content;
        if (request.getScriptId() != null) {
            ScriptRuleEntity entity = ensureExists(request.getScriptId());
            language = entity.getScriptLanguage();
            content = entity.getScriptContent();
            timeoutSeconds = entity.getTimeoutSeconds();
        } else {
            language = normalizeLanguage(request.getScriptLanguage());
            content = request.getScriptContent();
        }
        try {
            Path workDir = Files.createTempDirectory("code-review-script-");
            Path scriptPath = writeScript(workDir, language, content);
            ProcessBuilder builder = new ProcessBuilder(command(language, scriptPath));
            builder.directory(workDir.toFile());
            builder.redirectErrorStream(false);
            Process process = builder.start();
            if (StringUtils.hasText(request.getInputJson())) {
                process.getOutputStream().write(request.getInputJson().getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            ScriptTestRunResponse response = new ScriptTestRunResponse();
            response.setTimeout(!finished);
            if (!finished) {
                process.destroyForcibly();
                response.setSuccess(false);
                response.setExitCode(null);
                response.setStdout("");
                response.setStderr("脚本执行超时");
                return response;
            }
            response.setExitCode(process.exitValue());
            response.setStdout(read(process.getInputStream()));
            response.setStderr(read(process.getErrorStream()));
            response.setSuccess(process.exitValue() == 0);
            return response;
        } catch (Exception exception) {
            ScriptTestRunResponse response = new ScriptTestRunResponse();
            response.setSuccess(false);
            response.setTimeout(false);
            response.setExitCode(null);
            response.setStdout("");
            response.setStderr(exception.getMessage());
            return response;
        }
    }

    private void updateStatus(Long id, Integer status) {
        ensureExists(id);
        LambdaUpdateWrapper<ScriptRuleEntity> wrapper = new LambdaUpdateWrapper<ScriptRuleEntity>()
            .eq(ScriptRuleEntity::getId, id)
            .set(ScriptRuleEntity::getStatus, status);
        scriptRuleMapper.update(null, wrapper);
    }

    private ScriptRuleEntity ensureExists(Long id) {
        ScriptRuleEntity entity = scriptRuleMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "脚本不存在");
        }
        return entity;
    }

    private ScriptResponse toResponse(ScriptRuleEntity entity) {
        ScriptResponse response = new ScriptResponse();
        response.setId(entity.getId());
        response.setScriptName(entity.getScriptName());
        response.setScriptCode(entity.getScriptCode());
        response.setScriptLanguage(entity.getScriptLanguage());
        response.setScriptContent(entity.getScriptContent());
        response.setParameterTemplate(entity.getParameterTemplate());
        response.setTimeoutSeconds(entity.getTimeoutSeconds());
        response.setGeneratedByAi(entity.getGeneratedByAi());
        response.setStatus(entity.getStatus());
        return response;
    }

    private String normalizeLanguage(String language) {
        if (!StringUtils.hasText(language)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "脚本语言不能为空");
        }
        String upper = language.toUpperCase();
        if (!Arrays.asList("SHELL", "PYTHON", "NODE").contains(upper)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "脚本语言仅支持 SHELL/PYTHON/NODE");
        }
        return upper;
    }

    private Path writeScript(Path workDir, String language, String content) throws Exception {
        String suffix = "SHELL".equals(language) ? ".sh" : ("PYTHON".equals(language) ? ".py" : ".js");
        Path scriptPath = workDir.resolve("script" + suffix);
        Files.write(scriptPath, content.getBytes(StandardCharsets.UTF_8));
        return scriptPath;
    }

    private List<String> command(String language, Path scriptPath) {
        if ("PYTHON".equals(language)) {
            return Arrays.asList("python", scriptPath.toAbsolutePath().toString());
        }
        if ("NODE".equals(language)) {
            return Arrays.asList("node", scriptPath.toAbsolutePath().toString());
        }
        if (File.separatorChar == '\\') {
            return Arrays.asList("cmd", "/c", scriptPath.toAbsolutePath().toString());
        }
        return Arrays.asList("sh", scriptPath.toAbsolutePath().toString());
    }

    private String read(java.io.InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
