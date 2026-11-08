# SyncTap

Ứng dụng Android đồng bộ thao tác chạm giữa nhiều cửa sổ (app clone, game nhân bản). Chạm ở một cửa sổ, các cửa sổ còn lại chạm theo cùng vị trí tương đối.

## Cách hoạt động
- **Accessibility Service** hiển thị nút nổi `SYNC` và một lớp phủ trong suốt để bắt thao tác chạm.
- Khi bật SYNC, thao tác chạm/vuốt được quy đổi về toạ độ tương đối (0..1) trong cửa sổ được chạm, rồi phát lại đồng thời lên tất cả cửa sổ bằng `dispatchGesture()`.
- Màn hình được chia đều thành N cửa sổ: máy dọc chia trên/dưới, máy ngang chia trái/phải.

## Build APK bằng GitHub Actions
1. Đẩy toàn bộ mã nguồn này lên repo GitHub (giữ nguyên cấu trúc thư mục, nhớ cả `.github/workflows/build.yml`).
2. Vào tab **Actions**, chọn workflow **Build APK** (tự chạy khi push, hoặc bấm **Run workflow**).
3. Khi xong, mở lần chạy đó, mục **Artifacts**, tải `SyncTap-debug` và giải nén lấy file APK.
4. Cài APK lên điện thoại (cho phép cài từ nguồn không rõ).

## Sử dụng
1. Mở SyncTap, bấm **Mở cài đặt trợ năng**, bật dịch vụ **SyncTap**.
   - Android 13+: nếu bị chặn, vào Cài đặt → Ứng dụng → SyncTap → ⋮ → **Cho phép cài đặt bị hạn chế**.
2. Mở các game clone (cửa sổ nổi, freeform hoặc split-screen) và sắp xếp vị trí.
3. Trong SyncTap chọn **Số cửa sổ** khớp với số game đang mở.
4. Bấm nút nổi **VÙNG** để sửa khung từng cửa sổ:
   - Giữ vào một **cạnh** của khung rồi kéo: di chuyển cả vùng.
   - Kéo một **góc** (ô vuông trắng): đổi cỡ vùng.
   - **+ VÙNG** / **XOÁ VÙNG**: thêm, xoá vùng đang chọn (viền vàng).
   - **LƯU VÙNG**: lưu và thoát. **HUỶ SỬA** (nút SYNC khi đang sửa): thoát không lưu.
5. Bấm nút nổi **SYNC OFF** để bật (thành **SYNC ON**, hiện khung xanh quanh các vùng). Tắt SYNC mới sửa được vùng.
6. Trong app có nút đổi **chế độ phát**: REALTIME (phát từng đoạn ngay khi ngón tay đang di chuyển, độ trễ thấp) hoặc GHI RỒI PHÁT (ổn định hơn nhưng trễ bằng thời gian vuốt).

## Giới hạn hiện tại
- Chế độ REALTIME cần Android 8 trở lên và có thể không hoạt động trên một số máy; khi đó chuyển sang GHI RỒI PHÁT.
- Chỉ hỗ trợ một ngón (chưa có zoom hai ngón).
- Vùng cửa sổ chỉnh thủ công; cửa sổ nổi di chuyển hoặc đổi cỡ thì phải chỉnh lại vùng.
- Nhiều game phát hiện thao tác tự động và có thể khoá tài khoản. Tự cân nhắc điều khoản của game.

## Cấu trúc
```
.github/workflows/build.yml      CI build APK
app/src/main/java/com/synctap/   MainActivity.kt, SyncService.kt
app/src/main/res/xml/            accessibility_config.xml
app/src/main/AndroidManifest.xml
```

## Hướng phát triển
- Phát lại thời gian thực (gửi từng đoạn nhỏ khi đang vuốt).
- Hỗ trợ đa điểm chạm.
- Tự dò vị trí/kích thước cửa sổ thay vì chia đều.
