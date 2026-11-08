# 学涯规划助手（后端）

Spring Boot 3.2 / Java 17 / MySQL 8 / Redis / Ollama(qwen 7B)

## 启动
1. 执行 `sql/schema.sql`（或直接用 `ddl-auto: update` 自动建表，库需先建好并使用 utf8mb4）
2. 启动 Redis；`ollama serve`，确认 `ollama list` 里的模型名与 `app.ollama.model` 一致
3. 设置环境变量 `DB_USER / DB_PASSWORD / JWT_SECRET`（JWT_SECRET 至少 32 字节）
4. `mvn spring-boot:run`

短信默认 `app.sms.mock=true`，验证码只打印到日志。接入真实网关：改 `application.yml` 的 `app.sms.*`，
并按服务商文档调整 `SmsService.callApi` 里的请求体/签名。

## 接口（统一返回 {code,msg,data}，除 /api/auth/** 外需 `Authorization: Bearer <token>`）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /api/auth/sms/send | {phone} 发送验证码（60s 一次、5 分钟有效） |
| POST | /api/auth/register | {phone,password,code} |
| POST | /api/auth/login | {phone,password} |
| GET | /api/assessment/questions | 题目 |
| POST | /api/assessment/submit | {answers:{id:值}} 生成画像文件 |
| GET | /api/assessment/status | 是否已测评 |
| GET | /api/profile | 画像（含 summary、advices） |
| DELETE | /api/profile/advices/{id} | 删除某条建议（id 为 advice_record.id，数字） |
| GET | /api/profile/download | 下载画像 JSON |
| POST | /api/chat | 非流式对话 {message,sessionId?} |
| POST | /api/chat/stream | SSE 流式对话 |
| GET | /api/chat/history?sessionId= | 会话历史 |
| GET | /api/chat/sessions | 会话列表（最近 50 个） |
| PUT | /api/chat/sessions/{sessionId}/title | 重命名会话 {title} |
| DELETE | /api/chat/sessions/{sessionId} | 删除会话及其消息 |

画像文件位置：`./data/profiles/{userId}/profile.json`（由数据库导出的文件，数据变化提交后自动更新）
额外知识库：往 `./data/knowledge/` 放 `{stage}.md`（stage=kindergarten/primary/middle/high/university/career/general），按 `## 标题` 分段，重启生效。

## 新增能力
**短信**：`SMS_PROVIDER=aliyun|tencent|http|mock`（默认 mock）。阿里云需 `ALIYUN_AK_ID/ALIYUN_AK_SECRET/ALIYUN_SMS_SIGN/ALIYUN_SMS_TEMPLATE`（模板变量 `${code}`，AK 用 RAM 子账号只给短信权限）；
腾讯云需 `TENCENT_SECRET_ID/TENCENT_SECRET_KEY/TENCENT_SMS_APP_ID/TENCENT_SMS_SIGN/TENCENT_SMS_TEMPLATE`（模板参数 `{1}`=验证码 `{2}`=有效分钟）。配置不全会在启动时直接报错。

**向量检索**：`ollama pull bge-m3`，`app.knowledge.embedding.enabled=true`（默认）。启动时后台生成向量并缓存到 `data/knowledge-cache/`；
Ollama/模型不可用时自动回退关键词检索。显存紧张时设置 `OLLAMA_MAX_LOADED_MODELS=2`，避免对话模型与 embedding 模型来回换入换出。

**画像新增字段**：`habits`（学习风格/专注/时段/睡眠/运动/屏幕/家长参与）、`holland`（霍兰德职业兴趣代码，题型 `SCALE`，1-5 分，选填）、
`highlights`（规则计算的 strengths / concerns）、`completeness`（完整度百分比与缺失项）。旧用户重新测评后才会有这些字段。

## 代码结构（Spring MVC 按层分包）
```
com.planner
├─ PlannerApplication
├─ controller   接口层：Auth / Assessment / Profile / Chat（只做参数校验与转发）
├─ service      业务层：AuthService, SmsService, AssessmentService, ProfileService, ChatService, KnowledgeBase
├─ task         @Async 异步任务：ProfileSummaryTask（画像摘要）、AdviceExtractor（建议提取）
├─ repository   数据访问：UserRepository, AssessmentRecordRepository, ChatMessageRepository（Spring Data JPA）
├─ entity       JPA 实体：User, AssessmentRecord, ChatMessage
├─ dto          请求/响应对象：AuthDtos, AssessmentDtos, ChatDtos, AdviceIn, Msg
├─ client       外部系统客户端：OllamaClient；client.sms 下为短信通道（Mock/Http/Aliyun/Tencent）
├─ security     AuthInterceptor, JwtUtil
├─ domain       领域规则：QuestionBank（题库）, StageUtil（阶段判断）, ProfileInsights（画像规则分析）
├─ common       Result, BizException, UserContext, GlobalExceptionHandler
├─ config       WebConfig, AsyncConfig
└─ util         TextUtil
```
依赖方向：controller → service → (repository | client | domain)，security/common/util 可被各层使用。

## 数据库（v2）
| 表 | 作用 |
|---|---|
| sys_user | 账号（不变）；验证码只放 Redis，不入库 |
| assessment_record | 每次测评一条：answers_json 原始答案、mbti、profile_json 当次画像快照、summary、updated_at |
| user_profile | 最新画像（每用户一行）：age/stage/mbti/holland_code/completeness 等拆列，完整画像在 profile_json（不含建议） |
| advice_record | 对话提取的建议：category/content/stage/session_id/message_id；status=ACTIVE/DELETED/ARCHIVED |
| chat_session | 会话列表：title/message_count/last_message_at，(user_id, session_id) 唯一 |
| chat_message | 消息（不变） |

升级：已有库执行 `sql/migration_v2.sql` 再执行 `schema.sql` 里的新表；或直接依赖 `ddl-auto: update`。

数据流：
- 提交测评 → 写 assessment_record + user_profile（事务）→ 提交后异步生成摘要 → 回写两张表的 summary（若 user_profile 已被更新的测评取代则丢弃）→ 导出画像文件
- 一轮对话 → chat_message + chat_session 同一事务 → 异步提取建议 → advice_record（按用户行锁串行、与最近 100 条含已删除的去重、ACTIVE 最多 30 条，多的标 ARCHIVED）
- GET /api/profile = user_profile.profile_json + 当前 ACTIVE 建议；用户删除建议为软删除，避免被重新提取
