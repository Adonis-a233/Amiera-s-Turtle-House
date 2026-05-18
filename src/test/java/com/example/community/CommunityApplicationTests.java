package com.example.community;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 完整测试套件——按模块顺序执行所有测试类
 *
 * 运行前置条件：
 *   1. MySQL 服务已启动，数据库已初始化（执行 database-init.sql）
 *   2. Redis 服务已启动
 *   3. RabbitMQ 服务已启动（IM 消息队列）
 *   4. application.yaml 中的连接配置正确
 *   5. （可选）Elasticsearch + Ollama/DashScope 已启动（SearchControllerTest 需要）
 *
 * 运行方式：
 *   IDEA：右键点击此类 → Run 'CommunityApplicationTests'
 *   命令行：mvn test -Dmaven.surefire.skip=false -Dtest=CommunityApplicationTests
 */
@Suite
@SuiteDisplayName("Craving 社区平台完整测试套件")
@SelectClasses({
    // ── 用户模块 ──────────────────────────────────────────────────────────────
    UserControllerTest.class,           // 1. 注册、登录、JWT 认证
    UserProfileControllerTest.class,    // 2. 个人资料、修改用户名/密码、上传头像

    // ── 文章模块 ──────────────────────────────────────────────────────────────
    ArticleControllerTest.class,        // 3. 发布、列表、详情、点赞、按用户查询
    ArticleExtendedTest.class,          // 4. 点赞列表、草稿、搜索、行为上报、编辑、删除、收藏、分享

    // ── 评论模块 ──────────────────────────────────────────────────────────────
    CommentControllerTest.class,        // 5. 发表评论、回复、树形列表、删除

    // ── 联系人模块 ────────────────────────────────────────────────────────────
    ContactControllerTest.class,        // 6. 关注、取关、拉黑、解封、好友列表

    // ── 审核模块（需 ADMIN 角色） ────────────────────────────────────────────
    ArticleReviewControllerTest.class,  // 7. 待审核列表、人工通过、人工拒绝


    // ── IM 即时通讯模块 ───────────────────────────────────────────────────────
    ImConversationControllerTest.class, // 8. 会话列表、建群、消息历史、标记已读、密钥、总未读、入群

    // ── 语义搜索模块（需 Elasticsearch） ────────────────────────────────────
    SearchControllerTest.class,         // 9. 文本搜索、以图搜图、图文混合搜索

    // ── 敏感词管理模块（需 ADMIN 角色） ─────────────────────────────────────
    SensitiveWordControllerTest.class   // 10. 查看列表、添加、删除
})
public class CommunityApplicationTests {
}
