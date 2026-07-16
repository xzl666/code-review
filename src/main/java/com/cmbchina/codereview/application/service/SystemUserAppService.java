package com.cmbchina.codereview.application.service;

import com.cmbchina.codereview.interfaces.dto.response.SystemUserResponse;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemUserAppService {

    private final JdbcTemplate jdbcTemplate;

    public SystemUserAppService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<SystemUserResponse> list() {
        return jdbcTemplate.query(
            "SELECT user_name, user_id, employee_id FROM cr_system_user WHERE status = 1 AND deleted = 0 ORDER BY CASE WHEN employee_id='ADMIN' THEN 0 ELSE 1 END, id",
            (rs, row) -> new SystemUserResponse(rs.getString("user_name"), rs.getString("user_id"), rs.getString("employee_id")));
    }

    public List<SystemUserResponse> findByUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyList();
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        return jdbcTemplate.query("SELECT user_name,user_id,employee_id FROM cr_system_user WHERE user_id IN (" + placeholders + ") ORDER BY id",
            (rs, row) -> new SystemUserResponse(rs.getString("user_name"), rs.getString("user_id"), rs.getString("employee_id")), userIds.toArray());
    }

    public Map<String, SystemUserResponse> findByNames(List<String> names) {
        if (names == null || names.isEmpty()) return Collections.emptyMap();
        String placeholders = names.stream().map(name -> "?").collect(Collectors.joining(","));
        List<SystemUserResponse> users = jdbcTemplate.query(
            "SELECT user_name,user_id,employee_id FROM cr_system_user WHERE status=1 AND deleted=0 AND user_name IN (" + placeholders + ")",
            (rs, row) -> new SystemUserResponse(rs.getString("user_name"), rs.getString("user_id"), rs.getString("employee_id")), names.toArray());
        return users.stream().collect(Collectors.toMap(SystemUserResponse::getUserName, user -> user, (left, right) -> left, LinkedHashMap::new));
    }
}
