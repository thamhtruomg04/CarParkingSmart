from django.contrib import admin
from .models import ChargingStation, Booking

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