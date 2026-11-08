package com.planner.domain;

import java.util.*;

/**
 * 测评题库。SINGLE/MULTI 的选项 value 即 label；MBTI 题选项 value 为字母(E/I/S/N/T/F/J/P)。
 * 题目写在代码里，方便版本管理；后续要运营化可迁到数据库。
 */
public final class QuestionBank {
    public record Option(String value, String label) {}
    public record Question(String id, String section, String type, String text, String hint,
                           boolean required, List<Option> options) {}

    public static final Map<String, String> SECTIONS = new LinkedHashMap<>();
    private static final List<Question> ALL = new ArrayList<>();
    private static final Map<String, Question> BY_ID = new HashMap<>();

    private static final String[] SUBJECTS = {"语文", "数学", "英语", "物理", "化学", "生物", "政治", "历史", "地理", "信息技术", "艺术", "体育", "无/不适用"};

    private static List<Option> labels(String... xs) {
        List<Option> l = new ArrayList<>();
        for (String x : xs) l.add(new Option(x, x));
        return l;
    }

    private static void add(String id, String sec, String type, String text, String hint, boolean req, List<Option> opts) {
        Question q = new Question(id, sec, type, text, hint, req, opts);
        ALL.add(q);
        BY_ID.put(id, q);
    }

    private static void mbti(String dim, int n, String text, String a, String b) {
        add("mbti_" + dim + "_" + n, "MBTI", "SINGLE", text, "", true,
                List.of(new Option(String.valueOf(dim.charAt(0)), a), new Option(String.valueOf(dim.charAt(1)), b)));
    }

    private static void holland(String dim, int n, String text) {
        add("hol_" + dim + "_" + n, "HOLLAND", "SCALE", "你有多喜欢：" + text,
                "选填；约10岁以上建议填写。1=很不喜欢，5=很喜欢", false,
                List.of(new Option("1", "很不喜欢"), new Option("2", "不太喜欢"), new Option("3", "一般"),
                        new Option("4", "比较喜欢"), new Option("5", "很喜欢")));
    }

    static {
        SECTIONS.put("BASIC", "基本信息");
        SECTIONS.put("MBTI", "性格测评（MBTI 简化版）");
        SECTIONS.put("HOLLAND", "职业兴趣（霍兰德简化版，选填）");
        SECTIONS.put("TUTORING", "课外班/辅导班经历");
        SECTIONS.put("ACADEMIC", "学业情况");
        SECTIONS.put("HABIT", "学习习惯与生活");
        SECTIONS.put("INTEREST", "兴趣与特点");
        SECTIONS.put("GOAL", "目标与困扰");

        add("b_filled_by", "BASIC", "SINGLE", "本次由谁填写？", "年龄较小请由家长代填", true, labels("本人填写", "家长/监护人代填"));
        add("b_name", "BASIC", "TEXT", "昵称或称呼", "如：小明", false, List.of());
        add("b_age", "BASIC", "NUMBER", "年龄（周岁）", "3-60", true, List.of());
        add("b_gender", "BASIC", "SINGLE", "性别", "", false, labels("男", "女", "不愿透露"));
        add("b_grade", "BASIC", "TEXT", "当前所处年级/状态", "如：幼儿园大班、小学三年级、初二、高一、大二、已工作2年", true, List.of());
        add("b_city", "BASIC", "TEXT", "所在城市/省份（用于参考升学政策）", "", false, List.of());
        add("b_budget", "BASIC", "SINGLE", "家庭对教育投入的大致预算", "", false, labels("较紧张", "适中", "比较充裕", "暂不确定"));

        mbti("EI", 1, "在聚会或班级活动中，你更倾向于", "主动和很多人交流", "和少数熟悉的人聊天");
        mbti("EI", 2, "空闲时，哪种方式更能让你恢复精力", "和朋友一起活动", "自己安静待着");
        mbti("EI", 3, "遇到想法时，你通常", "边说边想", "想清楚再说");
        mbti("EI", 4, "到一个新环境，你", "很快就能融入", "需要时间先观察");
        mbti("SN", 1, "学新东西时，你更喜欢", "具体的例子和步骤", "整体概念和各种可能性");
        mbti("SN", 2, "你更相信", "亲眼所见的事实", "直觉和灵感");
        mbti("SN", 3, "你更常", "关注眼前的细节", "想象未来的可能");
        mbti("SN", 4, "做事情，你更喜欢", "用成熟可靠的方法", "尝试新奇的方法");
        mbti("TF", 1, "做决定时，你更看重", "逻辑与公平", "感受与人际和谐");
        mbti("TF", 2, "朋友难过时，你通常先", "帮他分析问题、想办法", "先陪伴、安慰");
        mbti("TF", 3, "被批评时，你更在意", "说得对不对", "对方的态度如何");
        mbti("TF", 4, "你更欣赏", "理性冷静的人", "温暖体贴的人");
        mbti("JP", 1, "对待作业/任务，你通常", "提前完成，按计划推进", "临近截止时才集中冲刺");
        mbti("JP", 2, "对于日程安排，你", "喜欢提前安排好", "喜欢随机应变");
        mbti("JP", 3, "你的书桌/书包通常", "整齐有序", "比较随意");
        mbti("JP", 4, "面对选择，你", "尽快做决定", "多保留余地、多看看");

        holland("R", 1, "修理东西、动手做模型");
        holland("R", 2, "户外活动、操作工具或设备");
        holland("I", 1, "研究一个问题，弄清背后的原理");
        holland("I", 2, "做实验、分析数据");
        holland("A", 1, "画画、写作、设计或表演");
        holland("A", 2, "创作新奇、与众不同的作品");
        holland("S", 1, "帮助别人解决困难");
        holland("S", 2, "照顾、教导他人");
        holland("E", 1, "组织活动、说服别人");
        holland("E", 2, "带领团队完成目标");
        holland("C", 1, "整理资料、制作计划表");
        holland("C", 2, "按规则把事情做得井井有条");

        add("t_classes", "TUTORING", "MULTI", "上过哪些类型的班？（可多选）", "", false,
                labels("学科类（语数英等）", "奥数/竞赛", "编程/机器人", "艺术（音乐/美术/舞蹈）", "体育运动",
                        "语言（英语口语/小语种）", "科学实验/科技", "留学/考证培训", "没有上过任何班"));
        add("t_detail", "TUTORING", "TEXT", "具体情况", "上过哪些、持续多久、效果和感受如何（选填）", false, List.of());
        add("t_attitude", "TUTORING", "SINGLE", "对上课外班的态度", "", false, labels("喜欢", "一般", "抗拒/压力大", "没上过"));

        add("a_scores", "ACADEMIC", "TEXT", "最近一次主要科目成绩及排名", "如：语文92 数学88 英语95，班级前20%；幼儿园可不填", false, List.of());
        add("a_strong", "ACADEMIC", "MULTI", "擅长的科目", "", false, labels(SUBJECTS));
        add("a_weak", "ACADEMIC", "MULTI", "薄弱的科目", "", false, labels(SUBJECTS));
        add("a_hours", "ACADEMIC", "SINGLE", "平均每天课外学习时间（不含学校课程）", "", false, labels("不到1小时", "1-2小时", "2-4小时", "4小时以上"));
        add("a_issues", "ACADEMIC", "MULTI", "学习上的困扰", "", false,
                labels("容易分心", "拖延", "缺少学习方法", "粗心", "基础薄弱", "考试焦虑/压力大", "理解记忆吃力", "暂无明显困扰"));

        add("h_style", "HABIT", "MULTI", "哪些学习方式对你更有效？（可多选）", "", false,
                labels("看图/视频理解更快", "听讲解/讨论更有效", "动手做/实践更有效", "读文字资料、做笔记", "和同伴一起学", "独自安静地学"));
        add("h_focus", "HABIT", "SINGLE", "单次能持续专注多久", "", false, labels("不到15分钟", "15-30分钟", "30-60分钟", "1小时以上"));
        add("h_best_time", "HABIT", "SINGLE", "一天中学习效率最高的时段", "", false, labels("早晨", "上午", "下午", "晚上", "不固定"));
        add("h_sleep", "HABIT", "SINGLE", "平均每天睡眠时间", "", false, labels("不足7小时", "7-8小时", "8-9小时", "9小时以上"));
        add("h_exercise", "HABIT", "SINGLE", "每周运动次数（每次约30分钟以上）", "", false, labels("几乎没有", "1-2次", "3-4次", "5次以上"));
        add("h_screen", "HABIT", "SINGLE", "平均每天用于娱乐的屏幕时间（手机/游戏/视频）", "", false, labels("不到1小时", "1-2小时", "2-4小时", "4小时以上"));
        add("h_family", "HABIT", "SINGLE", "家长参与学习的程度", "", false, labels("完全放手", "偶尔过问", "经常辅导/监督", "压力较大/期望很高"));

        add("i_interests", "INTEREST", "MULTI", "感兴趣的方向", "", false,
                labels("阅读写作", "数学逻辑", "科学探究", "编程", "艺术设计", "音乐", "体育", "动手制作", "组织/社交", "商业/创业", "动物与自然", "游戏"));
        add("i_traits", "INTEREST", "TEXT", "用几句话描述自己（性格、优缺点）", "选填", false, List.of());

        add("g_goal", "GOAL", "TEXT", "近期最想达成的目标", "如：考上重点初中、提高英语、选好科目、找到实习", false, List.of());
        add("g_career", "GOAL", "TEXT", "感兴趣的职业方向", "选填", false, List.of());
        add("g_path", "GOAL", "SINGLE", "未来大致路径的想法", "", false,
                labels("国内升学", "出国留学", "考公/考编", "直接就业", "创业", "还没想好"));
        add("g_worry", "GOAL", "TEXT", "目前最困扰的问题", "选填", false, List.of());
    }

    private QuestionBank() {}

    public static List<Question> all() { return Collections.unmodifiableList(ALL); }
    public static Question get(String id) { return BY_ID.get(id); }
}
