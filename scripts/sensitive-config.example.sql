USE code_review;

-- Copy this file to sensitive-config.local.sql and replace every placeholder.
-- sensitive-config.local.sql is ignored by Git and can be executed after init.sql.
START TRANSACTION;

INSERT INTO cr_system_config (config_key, config_value, config_desc)
VALUES
  ('GITEE_BASE_URL', 'https://gitee.com', 'Gitee service URL'),
  ('GITEE_SSH_PRIVATE_KEY', '<paste-private-key-with-\\n-line-breaks>', 'Gitee SSH private key'),
  ('ZHAOHU_ENABLED', 'true', 'Enable Zhaohu robot'),
  ('ZHAOHU_API_HOST', 'http://gatewayoazh.cmbchina.cn', 'Zhaohu API host'),
  ('ZHAOHU_CLIENT_ID', '<zhaohu-client-id>', 'Zhaohu client ID'),
  ('ZHAOHU_CLIENT_SECRET', '<zhaohu-client-secret>', 'Zhaohu client secret'),
  ('ZHAOHU_ROBOT_ID', '<zhaohu-robot-id>', 'Zhaohu robot ID'),
  ('ZHAOHU_APP_BASE_URL', 'http://localhost:5173', 'Code review platform URL'),
  ('ZHAOHU_TOKEN_EXPIRE_SECONDS', '86400', 'Zhaohu token lifetime'),
  ('ZHAOHU_TOKEN_BUFFER_SECONDS', '300', 'Zhaohu token refresh buffer'),
  ('ZHAOHU_TIMEOUT_SECONDS', '10', 'Zhaohu request timeout')
ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value),
  config_desc = VALUES(config_desc),
  deleted = 0;

SET @model_config_name = '内部代码检视模型';
SET @model_provider_type = 'OPENAI_COMPATIBLE';
SET @model_base_url = 'https://example.internal/v1';
SET @model_name = '<model-name>';
SET @model_api_key = '<model-api-key>';

INSERT INTO cr_model_config (
  config_name, provider_type, base_url, model_name, api_key, enabled, remark
)
SELECT
  @model_config_name, @model_provider_type, @model_base_url, @model_name, @model_api_key, 1, 'Sensitive migration config'
WHERE NOT EXISTS (
  SELECT 1 FROM cr_model_config WHERE config_name = @model_config_name AND deleted = 0
);

UPDATE cr_model_config
SET provider_type = @model_provider_type,
    base_url = @model_base_url,
    model_name = @model_name,
    api_key = @model_api_key,
    enabled = 1,
    deleted = 0
WHERE config_name = @model_config_name;

UPDATE cr_model_config
SET enabled = 0
WHERE config_name <> @model_config_name AND deleted = 0;

COMMIT;
