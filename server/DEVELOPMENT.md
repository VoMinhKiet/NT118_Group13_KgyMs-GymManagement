# KgyMs server — tài liệu kỹ thuật

> Dành cho người sửa code server. Hướng dẫn test chức năng thanh toán cho cả nhóm: xem [README.md](README.md).

Backend nhỏ cho app KgyMs: nhận webhook **SePay** khi tiền vào tài khoản ACB `28899471`, tự kiểm tra
hoá đơn và kích hoạt gói trên Firebase Realtime Database. App gọi server để tạo/huỷ hoá đơn, check-in
và (admin) xử lý đối soát. Server đang chạy tại `https://nt-118-group13-kgy-ms-gym-managemen.vercel.app`;
push lên nhánh `main` thì Vercel tự deploy lại.

Node.js + TypeScript + Express 5 + `firebase-admin`, test bằng Vitest, deploy lên Vercel (Root Directory = `server`).

## Lệnh

```powershell
npm.cmd install
npm.cmd test          # unit test (không cần .env, không chạm Firebase thật)
npm.cmd run typecheck
npm.cmd run dev       # chạy local ở http://localhost:3000 (cần server/.env)
```

> PowerShell trên máy nhóm chặn script `.ps1`, nên gọi `npm.cmd` / `npx.cmd` thay cho `npm` / `npx`.

## Biến môi trường

| Tên | Ý nghĩa |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT_BASE64` | Nội dung file khoá service account, mã hoá base64 |
| `FIREBASE_DATABASE_URL` | `https://kgyms-72c74-default-rtdb.asia-southeast1.firebasedatabase.app/` |
| `SEPAY_WEBHOOK_API_KEY` | Key ngẫu nhiên dài (≥ 32 ký tự), trùng với API Key của webhook trên SePay |
| `CHECKIN_SECRET` | Trùng giá trị `gymInfo/checkInSecret` trên Firebase |
| `BANK_ACCOUNT_NO` | `28899471` |
| `PAYMENT_EXPIRY_MINUTES` | Mặc định `15` |
| `PORT` | Chỉ dùng local, mặc định `3000` |

Thiếu biến bắt buộc thì server không khởi động và báo **tên** biến thiếu (không bao giờ in giá trị).

### Ghi bí mật vào `server/.env` mà không hiện ra màn hình

Mở PowerShell **trong thư mục `server`**, dán đoạn sau một lần cho mỗi cửa sổ PowerShell (định nghĩa hàm ghi `.env`):

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
function Set-EnvValue([string]$Name, [string]$Value) {
  $file = Join-Path (Get-Location) '.env'
  $lines = @([IO.File]::ReadAllLines($file) | Where-Object { $_ -notmatch "^$Name=" })
  [IO.File]::WriteAllLines($file, $lines + "$Name=$Value")
}
```

**Khoá Firebase** (file JSON tải từ Firebase Console, để **ngoài** repo):

```powershell
$keyPath = "$env:USERPROFILE\Documents\kgyms-keys\kgyms-service-account.json"   # sửa cho đúng đường dẫn
Set-EnvValue FIREBASE_SERVICE_ACCOUNT_BASE64 ([Convert]::ToBase64String([IO.File]::ReadAllBytes($keyPath)))
```

**API key cho webhook SePay** (tạo ngẫu nhiên, ghi vào `.env` và chép vào clipboard để dán lên SePay / trình quản lý mật khẩu):

```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
$key = ($b | % { $_.ToString('x2') }) -join ''
Set-EnvValue SEPAY_WEBHOOK_API_KEY $key; Set-Clipboard $key; Remove-Variable key, b
```

**Khoá check-in** (tạo khoá mới, ghi `.env`, chép vào clipboard để dán vào Firebase Console `gymInfo/checkInSecret`):

```powershell
$b = New-Object byte[] 24; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
$secret = ($b | % { $_.ToString('x2') }) -join ''
Set-EnvValue CHECKIN_SECRET $secret; Set-Clipboard $secret; Remove-Variable secret, b
```

**Kiểm tra `.env` đã đủ chưa** (chỉ in *có* / *TRỐNG*, không in giá trị):

```powershell
Get-Content .env | % { if ($_ -match '^([A-Z0-9_]+)=(.*)$') { '{0}: {1}' -f $matches[1], $(if ($matches[2]) { 'có' } else { 'TRỐNG' }) } }
```

## API

Lỗi luôn có dạng `{ "error": "<mã>", "message": "<tiếng Việt>" }`.

| Route | Xác thực | Mô tả |
|---|---|---|
| `GET /health` | — | `200 { "ok": true }` |
| `POST /webhooks/sepay` | `Authorization: Apikey <SEPAY_WEBHOOK_API_KEY>` | Nhận giao dịch từ SePay |
| `POST /payments` `{ packageId }` | `Authorization: Bearer <Firebase ID token>` | Tạo (hoặc trả lại) hoá đơn chuyển khoản → `{ paymentId, amount, expiresAt, packageName }` |
| `POST /payments/:id/cancel` | Bearer | Huỷ hoá đơn `pending` của mình |
| `POST /check-in` `{ code }` | Bearer | `{ result: "not_checkin_code" \| "invalid_code" \| "staff_not_required" \| "checked_out" \| "no_active_membership" \| "checked_in", ... }` |
| `POST /admin/reconciliation/:id/resolve` `{ action, paymentId?, note? }` | Bearer + `users/{uid}/role == "admin"` | `action`: `activate` \| `refunded` \| `ignored` |

### Webhook SePay

1. Sai API key ⇒ `401`.
2. `transferType != "in"` hoặc `accountNumber != BANK_ACCOUNT_NO` ⇒ `200 { success: true }`, bỏ qua.
3. Chống trùng bằng transaction trên `bankTransactions/{id}` (SePay gửi lại tối đa 7 lần).
4. Tách mã `KG` + 8 ký tự trong `content` (ưu tiên mã đứng sau `KGYMS`; ACB chèn số phía trước nội dung).
5. Hoá đơn không có / không `pending` / quá `expiresAt`, hoặc tiền thiếu/dư ⇒ ghi `reconciliation/{id}` (`open`), vẫn trả `200`.
6. Khớp hết ⇒ transaction `pending → paid` trên `payments/{id}/status`, rồi một multi-path update: hoá đơn
   (`paidAt`, `paidVia: "sepay"`, `bankTransactionId`), membership `active` + ngày (nối tiếp gói cùng loại), thông báo, `bankTransactions/{id}` `matched`.
7. Lỗi hệ thống bất ngờ ⇒ xoá `bankTransactions/{id}` vừa tạo và trả `500` để SePay gửi lại.

Hoá đơn cũ không có `expiresAt` (do app tạo trước khi có server, hoặc admin tạo tại quầy) được coi là **không hết hạn**.
Log tự động của server có `actorUid = "system"`, tiêu đề bắt đầu bằng "Hệ thống".

### Giả lập một lần SePay gọi (local)

⚠️ Lệnh này ghi vào Firebase **thật** (`bankTransactions`, `reconciliation`). Nội dung `KGYMS TEST` sẽ vào đối soát `no_code`.

```powershell
$key = (Select-String -Path .env -Pattern '^SEPAY_WEBHOOK_API_KEY=(.*)$').Matches[0].Groups[1].Value
$body = @{ id = 900000001; gateway = 'ACB'; transactionDate = '2026-11-19 10:00:00'; accountNumber = '28899471'
           content = 'TEST-KGYMS TEST'; transferType = 'in'; transferAmount = 5000; referenceCode = 'TEST' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:3000/webhooks/sepay -Headers @{ Authorization = "Apikey $key" } -ContentType 'application/json' -Body $body
Remove-Variable key
```

## Cấu trúc

```
src/app.ts            Điểm vào Vercel (default export Express app)
src/local.ts          Chạy local (đọc .env, listen)
src/createApp.ts      Ghép route + xử lý lỗi
src/config.ts         Đọc & kiểm tra biến môi trường
src/firebase.ts       firebase-admin (Db, AuthVerifier thật)
src/middleware/       sepayAuth, firebaseAuth (+ requireAdmin)
src/routes/           health, webhook, payments, checkIn, admin
src/services/         webhook, activation, payments, checkIn, reconciliation, activityLog (đọc/ghi qua Db)
src/domain/           Hàm thuần: paymentCode, activation, checkInCode, checkIn, dates, log, models
test/                 Vitest + Firebase giả trong bộ nhớ (memoryDb.ts)
```
