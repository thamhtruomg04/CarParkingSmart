from django.contrib import admin
from .models import ChargingStation, Booking

@admin.register(ChargingStation)
class StationAdmin(admin.ModelAdmin):
    list_display = ('name', 'available_slots', 'total_slots', 'latitude', 'longitude')

@admin.register(Booking)
class BookingAdmin(admin.ModelAdmin):
    list_display = ('user_id', 'station', 'status', 'expiry_time', 'amount')
    list_filter = ('status', 'station')
    search_fields = ('user_id',)