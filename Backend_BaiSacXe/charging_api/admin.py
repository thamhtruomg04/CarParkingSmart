from django.contrib import admin
from .models import ChargingStation, Booking, ChargingSlot

@admin.register(ChargingStation)
class StationAdmin(admin.ModelAdmin):
    list_display = ('name', 'ward', 'available_slots', 'total_slots', 'latitude', 'longitude')
    search_fields = ('name', 'ward')

@admin.register(Booking)
class BookingAdmin(admin.ModelAdmin):
    # Đã thêm các trường mới vào hiển thị để Truong dễ quản lý
    list_display = ('user_id', 'station', 'status', 'booking_time', 'expiry_time', 'is_checked_in', 'amount')
    list_filter = ('status', 'station', 'is_checked_in')
    search_fields = ('user_id', 'station__name')
    # Cho phép sửa nhanh check-in ngay tại danh sách
    list_editable = ('status', 'is_checked_in')

@admin.register(ChargingSlot)
class ChargingSlotAdmin(admin.ModelAdmin):
    # Hiển thị: Tên ô, Thuộc trạm nào, Trạng thái trống hay không
    list_display = ('slot_code', 'station', 'is_available')
    # Bộ lọc bên phải: Lọc theo trạm hoặc trạng thái
    list_filter = ('station', 'is_available')
    # Tìm kiếm theo tên ô
    search_fields = ('slot_code',)
    # Cho phép tích chọn Trống/Hết ngay tại danh sách
    list_editable = ('is_available',)