CREATE DATABASE IF NOT EXISTS student_planner DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE student_planner;

-- 用户账号（验证码不入库，只放 Redis）
CREATE TABLE IF NOT EXISTS sys_user (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  phone           VARCHAR(20)  NOT NULL,
  password_hash   VARCHAR(100) NOT NULL,
  assessment_done TINYINT(1)   NOT NULL DEFAULT 0,
  created_at      DATETIME,
  last_login_at   DATETIME,
  UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 每次测评一条：原始答案 + 当次画像快照（历史可追溯）
CREATE TABLE IF NOT EXISTS assessment_record (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT NOT NULL,
  answers_json MEDIUMTEXT,
  stage        VARCHAR(32),
  mbti         VARCHAR(16),
  profile_path VARCHAR(255),
  profile_json MEDIUMTEXT,
  summary      TEXT,
  created_at   DATETIME,
  updated_at   DATETIME,
  KEY idx_ar_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 最新画像（每个用户一行）：常用字段拆列便于查询，完整画像在 profile_json
CREATE TABLE IF NOT EXISTS user_profile (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id        BIGINT NOT NULL,
  assessment_id  BIGINT,
  nickname       VARCHAR(50),
  age            INT,
  gender         VARCHAR(16),
  grade          VARCHAR(64),
  city           VARCHAR(64),
  stage          VARCHAR(32),
  mbti           VARCHAR(16),
  holland_code   VARCHAR(8),
  completeness   INT,
  summary        TEXT,
  summary_status VARCHAR(16),
  profile_json   MEDIUMTEXT,
  created_at     DATETIME,
  updated_at     DATETIME,
  UNIQUE KEY uk_up_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 从对话中提取的建议：status = ACTIVE / DELETED(用户删除) / ARCHIVED(超过上限被挤出)
CREATE TABLE IF NOT EXISTS advice_record (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  session_id VARCHAR(64),
  message_id BIGINT,
  category   VARCHAR(16),
  content    VARCHAR(255),
  stage      VARCHAR(32),
  source     VARCHAR(16),
  status     VARCHAR(16) NOT NULL,
  created_at DATETIME,
  KEY idx_advice_user_status (user_id, status),
  KEY idx_advice_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 会话列表
CREATE TABLE IF NOT EXISTS chat_session (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  session_id      VARCHAR(64) NOT NULL,
  title           VARCHAR(100),
  message_count   INT NOT NULL DEFAULT 0,
  last_message_at DATETIME,
  created_at      DATETIME,
  updated_at      DATETIME,
  UNIQUE KEY uk_cs_user_session (user_id, session_id),
  KEY idx_cs_user_last (user_id, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  session_id VARCHAR(64) NOT NULL,
  role       VARCHAR(16) NOT NULL,
  content    TEXT,
  created_at DATETIME,
  KEY idx_user_session (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
