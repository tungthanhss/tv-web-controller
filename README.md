# LAN Browser

Trình duyệt Android TV có trang điều khiển chạy ngay trên tivi. Không cần máy tính làm máy chủ, tài khoản hoặc dịch vụ đám mây.

## Sử dụng

1. Mở **LAN Browser** trong danh sách ứng dụng trên tivi.
2. Điện thoại kết nối cùng mạng Wi-Fi, mở **http://192.168.2.34:8765** (hoặc địa chỉ đang hiển thị trên tivi).
3. Nhập website/tìm kiếm; vuốt bàn di chuột, chạm để bấm; chọn ô nhập trên tivi rồi gửi văn bản từ điện thoại.

Có quay lại, tiến tới, tải lại, cuộn, phím điều hướng, âm lượng; phát/dừng video, tua ±10 giây, tắt tiếng và toàn màn hình. Dán link MP4, WebM, M3U8 hoặc MPD vào ô địa chỉ để mở trình phát trực tiếp. Có thể thêm trang điều khiển vào màn hình chính điện thoại qua menu trình duyệt.

Trang điều khiển hoạt động khi ứng dụng đang mở. Không cần nhập mã hay ghép nối. Nếu IP thay đổi, dùng địa chỉ mới trên tivi.

## Biên dịch / cài

Yêu cầu Python 3, JDK 17 và Android SDK: build-tools 35.0.0, platform android-33. `build.ps1` sử dụng `ANDROID_HOME`, mặc định SDK tại `D:\Android\Sdk`. Không cần Gradle. Thư viện HLS.js 1.7.4 và dash.js 5.3.0 đã được lưu kèm trong assets/vendor (có giấy phép và nguồn phiên bản).

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
adb connect 192.168.2.34:5555
adb -s 192.168.2.34:5555 install -r build\LAN-Browser.apk
adb -s 192.168.2.34:5555 shell am start -n vn.lan.tvbrowser/.BrowserActivity
```

`debug.keystore` là khóa phát triển cục bộ, cần giữ để cập nhật APK hiện có. Đây là bản thử nghiệm cài nội bộ, chưa đóng gói để phát hành trên Play Store.

## Kiểm tra

```powershell
node --check assets\remote.js
python test_device.py
python test_video.py --streams
```

Bài kiểm tra dùng tivi thật, một trang thử nghiệm cục bộ qua ADB reverse và example.com: truy cập trực tiếp không cần mã, chặn origin khác, giới hạn đường dẫn/lệnh, mở trang, con trỏ, bấm, nhập Unicode, cuộn, lịch sử và tải lại. Sau kiểm tra trở về màn hình đầu.

## Phạm vi bản đầu

- Một cửa sổ duyệt web bằng Android System WebView, không dùng iframe để nhúng website.
- Gửi chữ thay thế toàn bộ ô đang được chọn trong tài liệu chính. Ô nhập nằm trong iframe khác nguồn có thể không nhận chữ.
- Phát HTML5 video trong trang; trình phát trực tiếp MP4/WebM và luồng HLS (.m3u8), MPEG-DASH (.mpd). Định dạng container không đảm bảo codec bên trong được tivi hỗ trợ. HLS/DASH trực tiếp yêu cầu máy chủ nguồn cho phép CORS và truy cập không cần header đăng nhập riêng.
- Nút Toàn màn hình trên điện thoại phóng video/iframe lớn nhất và ẩn thanh tivi; nút fullscreen của website dùng callback native Android. Nhấn lại hoặc Quay lại để thoát. Video trong iframe khác nguồn có thể cần dùng nút phát/tua của website; ứng dụng không vượt qua giới hạn khác nguồn của trình duyệt.
- Thử nghiệm video trên tivi thật: MP4, WebM, video nhúng, iframe cùng nguồn, fullscreen HTML5 native, tua/âm thanh, HLS và DASH mẫu công khai. File test tải clip CC0 từ MDN vào build/test-media.
- Chưa hỗ trợ tải tệp lên/xuống, nhiều tab, popup đăng nhập, quyền camera/micro hoặc bảo đảm phát video DRM. Một số website không hỗ trợ WebView.
- Điều khiển HTTP trong mạng LAN tin cậy; không chuyển tiếp cổng 8765 ra Internet. Bất kỳ thiết bị nào truy cập được địa chỉ tivi đều có thể điều khiển. API vẫn kiểm tra origin, không mở CORS hoặc cầu nối JavaScript native cho website.
- Dữ liệu phiên duyệt web lưu riêng trong WebView. ADB chỉ cần lúc cài/cập nhật; có thể tắt gỡ lỗi sau khi cài.

Tài liệu nền tảng: [Android WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview), [AAPT2](https://developer.android.com/tools/aapt2).

## Chặn quảng cáo

Bật sẵn trên mọi website: 9.839 tên miền quảng cáo/theo dõi (gồm tên miền con), tài nguyên trong các đường dẫn quảng cáo phổ biến, cửa sổ mới, banner tài trợ, ô quảng cáo và lớp nền popup. Áp dụng chung, không phụ thuộc chophim.cam. Theo dõi phần tử thêm mới hoặc đổi thuộc tính và xử lý iframe cùng nguồn. Giữ DOM, thanh điều hướng, biểu mẫu và trình phát bên trong khung cố định để hạn chế chặn nhầm. Quy tắc đường dẫn chỉ áp dụng cho tài nguyên con, giữ các trang nội dung có đường dẫn trùng tên.

Nguồn danh sách: Peter Lowe, https://pgl.yoyo.org/adservers/ (bản cập nhật 07/11/2026; ghi nguồn và chính sách trong assets/ad-hosts.txt). Kết hợp AdAway default blocklist từ https://adaway.org/hosts.txt (CC Attribution 3.0, giữ nguyên ghi công và giấy phép trong file). Các tên miền quảng cáo đã quan sát được bổ sung trong AdBlock.java. Danh sách được đóng gói để dùng độc lập với máy tính, chưa tự cập nhật.

Kiểm tra quy tắc: `java -cp build/classes vn.lan.tvbrowser.AdBlock assets/ad-hosts.txt`. Kiểm tra trên tivi thật: `python test_adblock.py`. Kiểm tra chung dùng hai hostname độc lập với Chợ Phim, quảng cáo xuất hiện muộn/đổi thuộc tính, iframe, lớp phủ; xác nhận video và thanh điều hướng không bị ẩn. Trạng thái `/state` có số yêu cầu bị chặn và phần tử quảng cáo đã ẩn.

Không đảm bảo loại bỏ quảng cáo được ghép sẵn vào hình ảnh/video hoặc mọi quảng cáo mới chưa có quy tắc. Nội dung trong iframe khác nguồn được lọc tên miền ở tầng mạng; không sửa DOM bên trong iframe đó.
