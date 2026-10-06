# Lục Bảo

Ứng dụng xem YouTube **không có quảng cáo** cho Android: không quảng cáo lúc mở app, lúc tạm dừng hay khi tìm kiếm, và không có quảng cáo của YouTube. Giao diện tông xanh lá có 2 chế độ: *Rừng đêm* (tối) và *Lá non* (sáng).

## Tính năng

- Xem video chất lượng tới **4K 60fps** (tự bỏ những mức máy không giải mã nổi), hoặc **chỉ âm thanh** để tiết kiệm 4G.
- **Nghe nền / tắt màn hình**, điều khiển trên màn hình khoá, thông báo và tai nghe Bluetooth.
- **Hình trong hình (PiP)**: về màn hình chính thì video tự thu thành cửa sổ nổi.
- Trang chủ: *Dành cho bạn* (gợi ý dựa trên video đã xem), Âm nhạc, Trò chơi, Phim & Trailer, Podcast, Trực tiếp.
- Tìm kiếm có gợi ý. Dán link YouTube hoặc bấm *Chia sẻ → Lục Bảo* từ Zalo, Messenger, trình duyệt… để mở thẳng video.
- **Lịch sử** có thanh tiến độ (mở lại là xem tiếp đúng chỗ đang dừng) và **Yêu thích**. Mọi thứ lưu trên máy, không cần tài khoản.
- Tốc độ phát 0,75×–2×, tự phát video tiếp theo, chạm đúp hai bên video để tua 10 giây.
- **Theo dõi kênh không cần tài khoản**: video mới của các kênh đang theo dõi luôn hiện đầu tiên ở Trang chủ (mục *Dành cho bạn* và *Đang theo dõi*). Danh sách chỉ lưu trên máy.
- **Khoá màn hình cho trẻ em**: chặn mọi thao tác trên trình phát; nhấn giữ biểu tượng ổ khoá 1,5 giây để mở.
- Khi xem toàn màn hình: nút **Nhiều video hơn** mở danh sách video khác ở 1/3 bên phải, video vẫn phát ở 2/3 bên trái.
- **Tải về máy**: tải video với độ phân giải tuỳ chọn (MP4 tới 1080p, WebM cho 1440p/4K) hoặc chỉ tải nhạc (M4A). Tệp lưu ở Movies/LucBao và Music/LucBao, xem lại trong Thư viện › Đã tải.
- **Dịch sang tiếng Việt**: bấm biểu tượng phụ đề trên video (hoặc nút *Dịch tiếng Việt* dưới video) để bật **phụ đề tiếng Việt**, **phụ đề + thuyết minh** hoặc **chỉ thuyết minh** (không hiện chữ). Mặc định tắt. Phụ đề lấy từ video; nếu video không có sẵn tiếng Việt thì YouTube tự dịch. Thuyết minh dùng giọng đọc tiếng Việt của máy và tự vặn nhỏ tiếng gốc khi đọc. Video không có phụ đề thì chưa dịch được.
- **Chọn phông chữ** trong Cài đặt: Roboto, phông của máy, Be Vietnam Pro hoặc Noto Sans. Riêng chữ "Lục Bảo" được vẽ thành hình nên luôn hiển thị đúng.
- **Tự cập nhật**: cài một lần là xong, người dùng không phải làm gì thêm.

## Tự cập nhật hoạt động thế nào

Ứng dụng được tách thành 2 phần:

| Phần | Nội dung | Cập nhật |
|---|---|---|
| **Bộ phát YouTube** (`engine/`) | NewPipeExtractor + cách gửi yêu cầu tới YouTube. Đây là phần hay hỏng khi YouTube thay đổi. | **Tự động hoàn toàn, không cần cài lại.** App tải gói mới, kiểm tra chữ ký rồi nạp ngay. |
| **Ứng dụng** (`app/`) | Giao diện, trình phát, thư viện | Chỉ khi bạn sửa code. App tự tải và nhờ Android cài. Android 12 trở lên thường cài âm thầm; một số máy (ColorOS, MIUI…) vẫn hỏi một lần. |

Phía GitHub (không ai phải làm gì):

- `.github/workflows/engine-update.yml` chạy **6 tiếng một lần**. Workflow kiểm tra bản NewPipeExtractor mới nhất, và cả phiên bản mà ứng dụng NewPipe chính thức đang dùng (thường là bản đã sửa lỗi). Nếu có bản mới hơn, nó tự build gói bộ phát, đăng lên release `engine-latest` rồi ghi lại số phiên bản. Bản nào build lỗi 3 lần sẽ bị bỏ qua.
- `.github/workflows/build.yml` chạy mỗi khi code được đẩy lên `main`. Nó build cả app lẫn bộ phát rồi đăng lên `app-latest`.
- Workflow tự giữ lịch chạy, để GitHub không tạm dừng nó sau 60 ngày không có hoạt động.
- Mọi gói tải về đều phải được **ký bằng cùng khoá** với app đã cài. Gói nào không đúng chữ ký sẽ bị từ chối.

**Link tải để chia sẻ cho mọi người (không bao giờ đổi):**
`https://github.com/<tài-khoản>/<repo>/releases/download/app-latest/LucBao.apk`

## Thiết lập một lần

1. Tạo một repo **công khai** trên GitHub rồi đẩy toàn bộ thư mục này lên nhánh `main`.
2. Vào **Settings → Secrets and variables → Actions → New repository secret** và thêm 2 secret:
   - `KEYSTORE_BASE64`: nội dung khoá ký đã mã hoá base64 (`base64 -w0 lucbao.jks`).
   - `KEYSTORE_PASSWORD`: mật khẩu khoá. Alias của khoá phải là `lucbao`.
3. Vào tab **Actions**, chọn **Build & publish Lục Bảo → Run workflow**.
4. Khoảng 10 phút sau, trong mục **Releases** sẽ có `LucBao.apk`. Gửi link ở trên cho mọi người.

> ⚠️ **Giữ kỹ file khoá `lucbao.jks` và mật khẩu.** Nếu mất khoá, các máy đã cài sẽ không nhận được bản cập nhật nữa và phải gỡ app ra cài lại.

## Cài trên điện thoại

1. Mở link tải → cài `LucBao.apk`. Nếu điện thoại hỏi, cho phép cài từ nguồn này. Nếu Play Protect cảnh báo, bấm **Vẫn cài đặt**.
2. Lần đầu mở app, bấm **Bật** ở 2 mục: *Cho phép tự cập nhật* và *Cho phép thông báo*.
3. Xong. Sau này không cần làm gì nữa.

## Build trên máy tính (tuỳ chọn)

Mở thư mục này bằng Android Studio, hoặc chạy `./gradlew :app:assembleDebug`. Bản build trên máy không có link cập nhật (`UPDATE_REPO` để trống) nên sẽ không tự cập nhật.

Cấu hình: AGP 8.11, Kotlin 2.2, Jetpack Compose, Media3 1.7, compileSdk 36, minSdk 26 (Android 8.0).

## Lưu ý

- Lục Bảo lấy video trực tiếp từ YouTube mà không qua trình phát chính thức, nên **vi phạm điều khoản của YouTube**. Ứng dụng chỉ dành cho dùng riêng và chia sẻ cho người quen, **không được đưa lên CH Play**.
- Video giới hạn độ tuổi (cần đăng nhập) chưa xem được.
- Lục Bảo không thuộc YouTube hay Google.
- Mã nguồn theo giấy phép **GPLv3** (giống NewPipeExtractor, xem `LICENSE`). Phông chữ Roboto, Be Vietnam Pro, Noto Sans và Playfair Display (chỉ dùng để vẽ chữ "Lục Bảo") dùng giấy phép SIL OFL (xem `docs/licenses`).
