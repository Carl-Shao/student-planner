-- 从第一版升级（已有 assessment_record 表）：补列 + 新建三张表（新表见 schema.sql）
USE student_planner;
ALTER TABLE assessment_record
  ADD COLUMN mbti         VARCHAR(16)  NULL AFTER stage,
  ADD COLUMN profile_json MEDIUMTEXT   NULL AFTER profile_path,
  ADD COLUMN summary      TEXT         NULL AFTER profile_json,
  ADD COLUMN updated_at   DATETIME     NULL AFTER created_at;
-- 然后执行 schema.sql 中的 user_profile / advice_record / chat_session 建表语句。
-- 第一版保存在 data/profiles/{userId}/profile.json 的旧画像不会自动导入，旧用户重新测评一次即可。

-- 已建好 advice_record 的库，补充去重查询（按 user_id 过滤、id 倒序）用的索引：
-- ALTER TABLE advice_record ADD INDEX idx_advice_user (user_id);
