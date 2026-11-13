"""Tạo tool/checkin_qr.html – trang hiển thị QR điểm danh xoay vòng cho màn hình ở quầy.

Cách dùng (từ thư mục gốc repo hoặc thư mục tool):
    python tool/build_checkin_qr.py

Kết quả là một file HTML duy nhất, chạy offline (mở bằng trình duyệt).
Lần đầu mở, trang hỏi `checkInSecret` (Firebase: gymInfo/checkInSecret) và chỉ lưu trong
localStorage của trình duyệt đó. File HTML KHÔNG chứa secret nên commit lên git an toàn.

Định dạng mã phải khớp với app (KgyMs/app/.../data/qr/CheckInCode.kt):
    KGYMS-CHECKIN:<epochSeconds // 30>:<8 ký tự hex đầu của HMAC-SHA256(secret, window)>
"""

from __future__ import annotations

import sys
from pathlib import Path

TOOL_DIR = Path(__file__).resolve().parent
SRC_DIR = TOOL_DIR / "src"
OUTPUT = TOOL_DIR / "checkin_qr.html"
PLACEHOLDER = "/*__QR_JS__*/"


def build() -> Path:
    template = (SRC_DIR / "checkin_template.html").read_text(encoding="utf-8")
    qr_js = (SRC_DIR / "qr.js").read_text(encoding="utf-8")
    if PLACEHOLDER not in template:
        raise SystemExit(f"Không tìm thấy {PLACEHOLDER} trong checkin_template.html")
    if "</script" in qr_js.lower():
        raise SystemExit("qr.js không được chứa chuỗi </script>")

    banner = (
        "<!-- File sinh tự động bởi tool/build_checkin_qr.py – đừng sửa tay, "
        "hãy sửa tool/src/ rồi chạy lại script. -->\n"
    )
    html = template.replace(PLACEHOLDER, qr_js.strip(), 1)
    html = html.replace("<html lang=\"vi\">", banner + "<html lang=\"vi\">", 1)
    OUTPUT.write_text(html, encoding="utf-8", newline="\n")
    return OUTPUT


if __name__ == "__main__":
    # Console Windows mặc định cp1252 không in được tiếng Việt.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    path = build()
    print(f"Đã tạo {path.relative_to(TOOL_DIR.parent)} – mở file này bằng trình duyệt trên máy ở quầy.")
    sys.exit(0)
