package unicorn.bot.dailycustombot.model;

import java.awt.Color;

/**
 * Enum phân loại tựa game trong hệ thống Daily Custom.
 * Dùng để xác định layout embed, label hiển thị, màu sắc, và default template.
 * <p>
 * Matching dùng {@code contains} thay vì exact match để hỗ trợ
 * các biến thể tên game (VD: "Liên Minh Huyền Thoại", "LoL", "lmht").
 */
public enum GameType {

    VALORANT("Súng", "Agent", new Color(0xE56A1E), "CUSTOM DEATHMATCH", "THE DEATHMATCH KING"),
    LOL("Chế độ", "Tướng", new Color(0x0BC6E3), "CUSTOM MATCH", "THE RIFT KING"),
    GENERIC("Chi tiết 1", "Chi tiết 2", new Color(0xE56A1E), "CUSTOM MATCH", "DAILY CUSTOM");

    private final String detail1Label;
    private final String detail2Label;
    private final Color embedColor;
    private final String subHeader;
    private final String footerText;

    GameType(String detail1Label, String detail2Label, Color embedColor, String subHeader, String footerText) {
        this.detail1Label = detail1Label;
        this.detail2Label = detail2Label;
        this.embedColor = embedColor;
        this.subHeader = subHeader;
        this.footerText = footerText;
    }

    public String detail1Label() { return detail1Label; }
    public String detail2Label() { return detail2Label; }
    public Color embedColor() { return embedColor; }
    public String subHeader() { return subHeader; }
    public String footerText() { return footerText; }

    /**
     * Emoji prefix cho field headers trong embed.
     */
    public String fieldEmoji() {
        return this == LOL ? "🔵" : "🟠";
    }

    /**
     * Xác định GameType từ tên game (case-insensitive, dùng contains).
     * Hỗ trợ nhiều biến thể:
     * - Valorant: "Valorant", "Val", v.v.
     * - LoL: "LoL", "League of Legends", "Liên Minh Huyền Thoại", "LMHT", v.v.
     */
    public static GameType fromName(String gameName) {
        if (gameName == null) return GENERIC;

        String normalized = gameName.toLowerCase().trim();

        // Valorant matching
        if (normalized.contains("valorant") || normalized.equals("val")) {
            return VALORANT;
        }

        // LoL matching — hỗ trợ "lol", "league", "liên minh", "lmht"
        if (normalized.contains("lol")
                || normalized.contains("league")
                || normalized.contains("liên minh")
                || normalized.contains("lmht")) {
            return LOL;
        }

        return GENERIC;
    }
}
