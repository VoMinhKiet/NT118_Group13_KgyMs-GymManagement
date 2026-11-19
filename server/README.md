# Test chức năng thanh toán KgyMs

Hướng dẫn cho cả nhóm test **thanh toán tự động** và **check-in** trên máy của mình.
Server đã chạy sẵn trên Vercel, **không cần cài hay dựng gì thêm**: chỉ cần build app mới nhất.

> Sửa code server (Node.js)? Xem [DEVELOPMENT.md](DEVELOPMENT.md).

## Cách hoạt động (đọc 30 giây)

1. Hội viên chọn gói trong app → app nhờ **server** tạo hoá đơn `KGxxxxxxxx` (hạn chuyển khoản **15 phút**).
2. Hội viên quét mã VietQR → chuyển khoản vào **ACB 28899471** với nội dung `KGYMS KGxxxxxxxx`.
3. **SePay** thấy tiền vào → báo cho server → server kiểm tra mã + số tiền → hoá đơn **Đã thanh toán**, gói **kích hoạt**, hội viên nhận **thông báo**.
4. Chuyển sai (thiếu/dư tiền, mất mã, quá hạn…) → giao dịch vào mục **Đối soát ngân hàng** cho admin xử lý.

Không ai phải bấm "Xác nhận" nữa (admin vẫn bấm tay được khi cần).

## Chuẩn bị

1. `git pull` nhánh `main`, mở thư mục `KgyMs` bằng Android Studio, **build và cài app lên điện thoại thật** (cần camera để quét QR và app ngân hàng để chuyển khoản).
2. Đăng nhập bằng một tài khoản **hội viên** (không phải admin/PT).
3. Điện thoại cần có mạng.

> ⚠️ **App bản cũ không còn tạo hoá đơn / check-in được** (Firebase đã chặn hội viên tự ghi, mọi thứ phải đi qua server). Thấy lỗi "Không có quyền…" thì build lại từ `main`.

## Test 1: Thanh toán thành công (chuyển 5.000đ thật)

1. Tab **QR** → **QR thanh toán** → chọn **Vé ngày (5.000đ)** → xác nhận.
2. App hiện mã VietQR. Kiểm tra: **ACB • 28899471**, **5.000đ**, nội dung **`KGYMS KG…`**, dòng *"Vui lòng chuyển khoản trước HH:mm"*.
3. Mở app ngân hàng **bất kỳ** → quét mã → chuyển. **Không sửa số tiền và nội dung.**
4. Quay lại KgyMs, đợi khoảng 5–30 giây (không cần bấm gì):
   - Màn hình tự chuyển sang **"Thanh toán thành công"** kèm thời hạn gói.
   - Chuông thông báo ở Home có *"KgyMs đã nhận 5.000đ cho hoá đơn KG… (Vé ngày). Thời hạn: …"*.
   - Nếu đang có vé ngày còn hạn, vé mới **nối tiếp** sang ngày hôm sau (đúng thiết kế).

Tiền 5.000đ vào tài khoản của nhóm (ACB 28899471), không mất đi đâu.

## Test 2: Hoá đơn hết hạn

1. Tạo hoá đơn như Test 1 nhưng **không chuyển tiền**.
2. Sau 15 phút, màn VietQR tự đổi sang **"Hoá đơn đã hết hạn"** (mã QR biến mất).
3. Quay lại danh sách, chọn lại gói đó → app tạo **hoá đơn mới** (mã khác), hoá đơn cũ tự chuyển **Đã huỷ**.

Nếu vẫn chuyển tiền cho hoá đơn đã hết hạn → tiền không mất, giao dịch vào **Đối soát** (Test 5).

## Test 3: Huỷ hoá đơn

Tạo hoá đơn → bấm **Huỷ hoá đơn** → xác nhận. Hoá đơn chuyển **Đã huỷ**; chọn lại gói sẽ tạo hoá đơn mới.

## Test 4: Check-in / check-out

1. Nhờ người giữ khoá điểm danh mở `tool/checkin_qr.html` trên máy tính (mã QR đổi mỗi 30 giây).
2. App: tab **QR** → **Quét QR điểm danh tại gym** → quét mã trên màn hình.

| Tình huống | Kết quả mong đợi |
|---|---|
| Có gói đang hoạt động hôm nay, quét lần 1 | **"Check-in thành công"** + tên gói |
| Quét lần 2 trong ngày | **"Check-out thành công"**, hiện số phút đã tập |
| Không có gói còn hạn | **"Chưa có gói đang hoạt động"** + nút **Mua vé ngày** (đi tới Test 1) |
| Tài khoản admin hoặc PT | **"Không cần điểm danh"** |
| Quét ảnh chụp mã từ lâu | **"Mã đã hết hạn"** |
| Quét QR khác (link, mã sản phẩm…) | **"Không phải mã điểm danh"** |

## Test 5: Đối soát (cần tài khoản admin)

Tạo một giao dịch "lệch" bằng 1 trong các cách (đều là tiền thật, nên chuyển ít):
- Chuyển **6.000đ** cho hoá đơn 5.000đ (dư tiền), hoặc **4.000đ** (thiếu tiền).
- Chuyển vào ACB 28899471 với nội dung **không có mã**, vd `KGYMS TEST`.

Rồi đăng nhập **admin** → **Quản lý hoá đơn** → **Đối soát ngân hàng**:
- Thấy giao dịch trong tab **Cần xử lý**, có lý do (*Chuyển dư tiền*, *Không có mã hoá đơn*…), số tiền nhận / cần.
- Bấm **Xử lý** → chọn:
  - **Kích hoạt hoá đơn**: nhập/giữ mã `KG…` → gói của hội viên được kích hoạt + có thông báo.
  - **Đã hoàn tiền**: khi đã chuyển trả khách.
  - **Bỏ qua**: giao dịch không liên quan.
- Giao dịch chuyển sang tab **Đã xử lý**.

## Gặp lỗi?

| Thấy gì | Làm gì |
|---|---|
| "Không có quyền thực hiện thao tác này" | App bản cũ → build lại từ `main`. |
| "Không kết nối được máy chủ KgyMs" | Kiểm tra mạng. Mở `https://nt-118-group13-kgy-ms-gym-managemen.vercel.app/health` trên trình duyệt phải thấy `{"ok":true}`; không thấy thì báo người quản lý Vercel. |
| "Máy chủ KgyMs phản hồi quá lâu" | Lần gọi đầu sau một thời gian server "ngủ" có thể chậm vài giây → thử lại. |
| Đã chuyển tiền 2 phút mà vẫn "Đang chờ xác nhận" | Kiểm tra nội dung chuyển khoản có đúng `KGYMS KG…` không, số tiền có đúng không. Sai thì giao dịch nằm ở **Đối soát** → nhờ admin xử lý. Đúng mà vẫn chờ → báo người quản lý server kèm **mã hoá đơn** và **giờ chuyển**. |
| Admin mở Đối soát báo "không có quyền" | Đăng nhập đúng tài khoản admin (`role = admin`). |

## Lưu ý

- Đừng chia sẻ API key SePay, khoá Firebase hay file `server/.env` cho ai; các bạn test **không cần** những thứ này.
- Đừng dùng tài khoản admin để mua gói (admin không cần check-in).
- Mỗi lần test thành công tạo một hoá đơn + gói thật trên Firebase; không cần xoá.
