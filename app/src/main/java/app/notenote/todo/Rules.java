package app.notenote.todo;

import java.net.URI;

/** Pure decisions shared by capture and the background worker. */
public final class Rules {
    public static final long DAY = 86_400_000L;
    private Rules() {}
    public static String classify(String text, String requested) {
        if (requested.equals("think") || requested.equals("action") || requested.equals("note")) return requested;
        if (text.matches("(?s).*(为什么|怎么|如何|研究|分析|思考|纠结|情绪|不理解|搞懂|不知道|调研).*")) return "think";
        if (text.matches("(?s).*(提醒|预约|办理|办卡|银行卡|买|缴费|取快递|寄快递|去医院|打电话|提交|报销|开会|还书).*")) return "action";
        return "note";
    }
    public static boolean reviewEligible(String kind, boolean done, long lastReview, long snooze, long now) {
        return kind.equals("think") && !done && snooze <= now && lastReview == 0;
    }
    public static boolean reminderEligible(boolean done, long due, long lastReminder, long snooze, long now) {
        return !done && due > 0 && due <= now && snooze <= now && (lastReminder == 0 || now - lastReminder >= DAY);
    }
    public static boolean quiet(int hour, int from, int to) {
        if (from == to) return false;
        return from > to ? hour >= from || hour < to : hour >= from && hour < to;
    }
    public static String endpoint(String input) {
        URI uri;
        try { uri = URI.create(input.trim()); } catch (RuntimeException e) { throw new IllegalArgumentException("服务地址格式不正确"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("请填写不含密钥的 HTTPS 服务地址");
        String s = uri.toString().replaceAll("/+$", "");
        return s.endsWith("/chat/completions") ? s : s + "/chat/completions";
    }
}
