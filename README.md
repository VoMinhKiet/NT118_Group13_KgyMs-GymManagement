# KgyMs - Gym Management System

> Ứng dụng quản lý phòng tập Gym trên nền tảng Android  
> Đồ án môn học **NT118 - Phát triển ứng dụng trên thiết bị di động**  
> Trường Đại học Công nghệ Thông tin - Đại học Quốc gia Thành phố Hồ Chí Minh (UIT - VNUHCM)

---

## Tổng quan dự án

**KgyMs (Gym Management System)** là ứng dụng di động hỗ trợ quản lý và vận hành phòng tập Gym, được phát triển trong khuôn khổ đồ án môn học **NT118 - Phát triển ứng dụng trên thiết bị di động**.

Ứng dụng hướng đến việc số hóa các hoạt động quản lý phòng tập, hỗ trợ quản trị viên theo dõi hội viên, nhân sự, gói tập, tài sản, hóa đơn và giao dịch. Đồng thời, KgyMs cung cấp cho hội viên các chức năng phục vụ quá trình tập luyện, quản lý thông tin cá nhân và theo dõi thông tin liên quan đến thẻ tập.

Hệ thống phân chia chức năng theo vai trò người dùng, giúp tách biệt các nghiệp vụ quản trị và các chức năng dành cho hội viên.

---

## Thông tin đồ án

| Thông tin | Nội dung |
|---|---|
| **Môn học** | NT118 - Phát triển ứng dụng trên thiết bị di động |
| **Đề tài** | KgyMs - Gym Management System |
| **Trường** | Trường Đại học Công nghệ Thông tin - ĐHQG TP.HCM |
| **Nhóm** | Group 13 |
| **Nền tảng** | Android |
| **Ngôn ngữ lập trình** | Kotlin |
| **Giao diện** | Jetpack Compose |
| **Kiến trúc** | MVVM |
| **Backend và xác thực** | Firebase |

## Thành viên nhóm

| MSSV | Họ và tên |
|---|---|
| 24520918 | Võ Minh Kiệt |
| 24521059 | Lê Quang Minh |
| 24520917 | Trương Tấn Kiệt |

---

## Nhóm người dùng chính

### 1. Admin - Quản trị viên

Hỗ trợ quản lý và theo dõi các hoạt động vận hành của phòng Gym:

- **Quản lý hội viên:** Xem danh sách và theo dõi thông tin hội viên.
- **Quản lý nhân sự:** Quản lý thông tin nhân viên phục vụ hoạt động phòng tập.
- **Quản lý gói tập:** Theo dõi thông tin các gói tập và dịch vụ.
- **Quản lý tài sản:** Quản lý danh mục tài sản và thiết bị.
- **Quản lý hóa đơn và giao dịch:** Theo dõi hóa đơn, biên nhận và lịch sử giao dịch.
- **Dashboard:** Tổng hợp thông tin và các chỉ số phục vụ công tác quản lý.

### 2. Member - Hội viên

Cung cấp các chức năng hỗ trợ hội viên sử dụng dịch vụ phòng Gym:

- **Quản lý hồ sơ cá nhân:** Theo dõi và cập nhật thông tin tài khoản.
- **Quản lý thẻ tập:** Theo dõi thông tin thẻ tập và gói tập.
- **Theo dõi luyện tập:** Tiếp cận kế hoạch và thông tin liên quan đến quá trình tập luyện.
- **Thông tin thể chất:** Hỗ trợ theo dõi dữ liệu InBody theo chức năng được triển khai.
- **Tương tác với phòng Gym:** Tiếp cận thông tin và dịch vụ được cung cấp trong ứng dụng.

---

## Tính năng nổi bật

### 1. Xác thực và quản lý tài khoản

- Đăng ký và đăng nhập bằng email, mật khẩu.
- Đăng nhập bằng tài khoản Google.
- Hỗ trợ khôi phục mật khẩu.
- Quản lý trạng thái tài khoản và phân quyền người dùng theo vai trò.

### 2. Hệ thống quản trị Admin

- Dashboard tổng quan.
- Quản lý hội viên và nhân sự.
- Quản lý danh mục tài sản.
- Quản lý gói tập, hóa đơn và giao dịch.
- Điều hướng giữa các chức năng quản trị thông qua giao diện dành riêng cho Admin.

### 3. Quản lý hội viên và hoạt động tập luyện

- Theo dõi thông tin hội viên và hồ sơ cá nhân.
- Quản lý thông tin liên quan đến thẻ tập và gói tập.
- Hỗ trợ các nghiệp vụ liên quan đến check-in, lịch sử tập luyện và dữ liệu InBody theo phạm vi triển khai.

### 4. Giao diện và trải nghiệm người dùng

- Giao diện Android được xây dựng bằng Jetpack Compose.
- Tổ chức màn hình theo chức năng và vai trò người dùng.
- Sử dụng kiến trúc MVVM để phân tách giao diện, trạng thái và xử lý nghiệp vụ.
- Hướng đến khả năng bảo trì và mở rộng trong quá trình phát triển.

---

## Kiến trúc hệ thống

KgyMs sử dụng kiến trúc **MVVM (Model - View - ViewModel)** nhằm phân tách trách nhiệm giữa giao diện người dùng và xử lý nghiệp vụ.

```text
KgyMs
├── UI
│   ├── Admin
│   ├── Auth
│   └── Member
├── ViewModel
│   └── Quản lý trạng thái và sự kiện giao diện
└── Data
    ├── Model
    ├── Repository
    └── Firebase
```

### Vai trò các thành phần

- **UI:** Hiển thị giao diện và tiếp nhận tương tác.
- **ViewModel:** Quản lý trạng thái giao diện và điều phối xử lý nghiệp vụ.
- **Model:** Định nghĩa cấu trúc dữ liệu.
- **Repository:** Tổ chức logic truy xuất và xử lý dữ liệu.
- **Firebase:** Hỗ trợ xác thực và lưu trữ dữ liệu theo cấu hình của dự án.

---

## Công nghệ sử dụng

### Mobile Application

| Công nghệ | Vai trò |
|---|---|
| **Kotlin** | Ngôn ngữ lập trình chính |
| **Android Studio** | Môi trường phát triển ứng dụng |
| **Jetpack Compose** | Xây dựng giao diện Android |
| **MVVM** | Kiến trúc tổ chức mã nguồn |
| **Kotlin Coroutines** | Hỗ trợ xử lý tác vụ bất đồng bộ |

### Backend và dữ liệu

| Công nghệ | Vai trò |
|---|---|
| **Firebase Authentication** | Xác thực tài khoản người dùng |
| **Firebase Realtime Database** | Lưu trữ và truy xuất dữ liệu |
| **Firebase Storage** | Lưu trữ tệp và tài nguyên nếu được sử dụng trong phiên bản triển khai |

### Công cụ phát triển

- **Git và GitHub:** Quản lý mã nguồn và hỗ trợ cộng tác nhóm.
- **Android Emulator:** Chạy thử và kiểm thử ứng dụng.
- **Gradle:** Quản lý cấu hình xây dựng và thư viện phụ thuộc.

---

## Cài đặt và khởi chạy

### Yêu cầu hệ thống

- Android Studio tương thích với cấu hình Gradle của dự án.
- JDK phù hợp với phiên bản Android Gradle Plugin đang sử dụng.
- Android SDK theo cấu hình của dự án.
- Kết nối Internet để sử dụng các dịch vụ Firebase.
- File `google-services.json` tương ứng với ứng dụng Firebase của nhóm.

### Các bước thực hiện

**1. Clone repository**

```bash
git clone https://github.com/VoMinhKiet/NT118_Group13_KgyMs-GymManagement.git
```

**2. Mở dự án**

Mở thư mục repository vừa tải về bằng Android Studio và chờ Gradle đồng bộ.

**3. Cấu hình Firebase**

- Đảm bảo ứng dụng Android đã được đăng ký trong Firebase Console với đúng package name.
- Đặt file `google-services.json` vào thư mục `app/`.
- Bật và cấu hình các dịch vụ Firebase mà ứng dụng sử dụng.
- Kiểm tra quyền truy cập dữ liệu phù hợp với yêu cầu của hệ thống.

**4. Chạy ứng dụng**

Chọn Android Emulator hoặc thiết bị Android đã kết nối, sau đó nhấn **Run** trong Android Studio.

---

## Mục tiêu dự án

KgyMs hướng đến việc vận dụng kiến thức phát triển ứng dụng di động Android để xây dựng một hệ thống quản lý phòng Gym có cấu trúc rõ ràng, giao diện dễ sử dụng và khả năng tích hợp dịch vụ đám mây.

Thông qua đồ án, nhóm thực hành quy trình phát triển ứng dụng, tổ chức mã nguồn theo kiến trúc MVVM, quản lý dữ liệu, tích hợp xác thực và phối hợp phát triển bằng Git/GitHub.

---

**KgyMs - Gym Management System**  
*NT118 - Phát triển ứng dụng trên thiết bị di động | UIT - VNUHCM*
