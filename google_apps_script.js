/**
 * Google Apps Script — UNICORN CHAMPIONSHIP Team Confirmation
 * 
 * Hướng dẫn sử dụng:
 * 1. Mở Google Sheet → Extensions → Apps Script
 * 2. Paste toàn bộ script này vào editor
 * 3. Thay đổi API_URL và API_KEY bên dưới cho đúng
 * 4. Lưu script (Ctrl+S)
 * 5. Vào Triggers (⏰) → Add Trigger → onEdit → From spreadsheet → On edit
 * 
 * Khi admin tick checkbox cột AG (Status):
 * - Script gửi POST request đến bot API
 * - Bot tạo/cập nhật team trên Discord
 * - Cột AG được ghi đè thành "✅ Đã duyệt" hoặc "❌ Lỗi: ..."
 */

// ============================================================
// CẤU HÌNH — THAY ĐỔI THEO SETUP CỦA BẠN
// ============================================================
var API_URL = "https://discord-bot-template-production-34b7.up.railway.app/api/confirm-team";
var API_KEY = "unicorn-championship-2026-xyz"; // Phải khớp với env var API_SECRET_KEY trên Railway

// Cột mapping (1-indexed)
var COL_STATUS = 33;        // AG - Checkbox Status
var COL_TEAM_NAME = 3;      // C  - Tên đội tuyển
var COL_SHORT_NAME = 4;     // D  - Tên viết tắt
var COL_CAPTAIN_DISCORD = 9; // I  - Discord ID đội trưởng
var COL_MEMBER2_DISCORD = 14; // N  - Discord ID thành viên 2
var COL_MEMBER3_DISCORD = 18; // R  - Discord ID thành viên 3
var COL_MEMBER4_DISCORD = 21; // U  - Discord ID thành viên 4
var COL_MEMBER5_DISCORD = 24; // X  - Discord ID thành viên 5
var COL_MEMBER6_DISCORD = 27; // AA - Discord ID thành viên 6 (optional)

var HEADER_ROW = 1; // Dòng header (bỏ qua)

/**
 * Trigger chạy khi có cell bị edit.
 * Chỉ xử lý khi: cột AG, giá trị mới = TRUE (checkbox ticked).
 */
function onEdit(e) {
  var sheet = e.source.getActiveSheet();
  var range = e.range;
  var row = range.getRow();
  var col = range.getColumn();

  // Bỏ qua header row
  if (row <= HEADER_ROW) return;

  // Chỉ xử lý cột Status (AG = cột 33)
  if (col !== COL_STATUS) return;

  // Chỉ xử lý khi tick checkbox (TRUE)
  var newValue = e.value;
  if (newValue !== "TRUE") return;

  // Guard: nếu ô đã có text (VD: "✅ Đã duyệt") thay vì boolean → bỏ qua
  var cellValue = range.getValue();
  if (typeof cellValue === "string" && cellValue.length > 5) {
    return; // Đã xử lý trước đó
  }

  // Đọc data từ row
  var rowData = sheet.getRange(row, 1, 1, sheet.getLastColumn()).getValues()[0];

  var teamName = rowData[COL_TEAM_NAME - 1] || "";
  var shortName = rowData[COL_SHORT_NAME - 1] || "";
  var captainDiscord = rowData[COL_CAPTAIN_DISCORD - 1] || "";

  // Thu thập Discord IDs thành viên (bỏ rỗng)
  var memberDiscordCols = [
    COL_MEMBER2_DISCORD,
    COL_MEMBER3_DISCORD,
    COL_MEMBER4_DISCORD,
    COL_MEMBER5_DISCORD,
    COL_MEMBER6_DISCORD
  ];

  var membersDiscord = [];
  for (var i = 0; i < memberDiscordCols.length; i++) {
    var discordId = rowData[memberDiscordCols[i] - 1];
    if (discordId && discordId.toString().trim() !== "") {
      membersDiscord.push(discordId.toString().trim());
    }
  }

  // Validate dữ liệu tối thiểu
  if (!teamName || !shortName || !captainDiscord) {
    range.setValue("❌ Thiếu dữ liệu: Tên đội / Tên viết tắt / Captain Discord");
    return;
  }

  // Gửi request đến bot API
  var payload = {
    teamName: teamName.toString().trim(),
    shortName: shortName.toString().trim(),
    captainDiscord: captainDiscord.toString().trim(),
    membersDiscord: membersDiscord,
    rowNumber: row
  };

  try {
    var options = {
      method: "post",
      contentType: "application/json",
      headers: {
        "X-API-Key": API_KEY
      },
      payload: JSON.stringify(payload),
      muteHttpExceptions: true // Để xử lý error response
    };

    var response = UrlFetchApp.fetch(API_URL, options);
    var statusCode = response.getResponseCode();
    var responseBody = JSON.parse(response.getContentText());

    if (statusCode === 200 && responseBody.success) {
      // Thành công → ghi đè checkbox thành text
      var actionLabel = responseBody.action === "CREATED" ? "✅ Đã duyệt" : "🔄 Đã cập nhật";
      range.setValue(actionLabel);

      Logger.log("Row " + row + ": " + responseBody.action + " — " + responseBody.message);
    } else {
      // Lỗi từ bot
      range.setValue("❌ " + (responseBody.message || "Lỗi không xác định"));
      Logger.log("Row " + row + " ERROR: " + responseBody.message);
    }

  } catch (error) {
    // Lỗi network hoặc exception
    range.setValue("❌ Lỗi kết nối: " + error.message);
    Logger.log("Row " + row + " EXCEPTION: " + error.message);
  }
}
