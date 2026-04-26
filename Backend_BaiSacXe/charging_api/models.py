from django.db import models
from django.utils import timezone
from datetime import timedelta
from django.core.exceptions import ValidationError
from django.db.models.signals import post_delete
from django.dispatch import receiver

class ChargingStation(models.Model):
    name = models.CharField(max_length=255, verbose_name="Tên trạm")
    ward = models.CharField(max_length=100, verbose_name="Phường/Xã", default="Khác")
    address = models.TextField(verbose_name="Địa chỉ")
    latitude = models.DecimalField(max_digits=20, decimal_places=15, null=True, blank=True)
    longitude = models.DecimalField(max_digits=20, decimal_places=15, null=True, blank=True)
    total_slots = models.IntegerField(default=0)
    available_slots = models.IntegerField(default=0)

    def __str__(self):
        return self.name


class ChargingSlot(models.Model):
    station = models.ForeignKey(ChargingStation, on_delete=models.CASCADE, related_name='slots')
    slot_code = models.CharField(max_length=5) # Ví dụ: A1, B2...
    is_available = models.BooleanField(default=True)

    def __str__(self):
        return f"{self.station.name} - {self.slot_code}" 

class Booking(models.Model):
    STATUS_CHOICES = [
        ('Quick_Booking', 'Đang giữ chỗ 10p'),
        ('Confirmed', 'Đã giữ chỗ/Đang sạc'),
        ('Completed', 'Đã sạc xong'),
        ('Cancelled', 'Đã hủy/Hết hạn'),
    ]

    user_id = models.CharField(max_length=100, verbose_name="ID người dùng")
    station = models.ForeignKey(ChargingStation, on_delete=models.CASCADE)
    booking_time = models.DateTimeField(auto_now_add=True)
    expiry_time = models.DateTimeField(null=True, blank=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='Quick_Booking')
    qr_code_data = models.TextField(null=True, blank=True)
    amount = models.DecimalField(max_digits=10, decimal_places=2, default=0)
    is_checked_in = models.BooleanField(default=False)
    slot = models.ForeignKey(ChargingSlot, on_delete=models.SET_NULL, null=True, blank=True)

    def save(self, *args, **kwargs):
        is_new = self.pk is None

        if is_new:
            # KIỂM TRA VÀ GIẢM available_slots NGAY KHI TẠO MỚI
            if self.station.available_slots <= 0:
                raise ValidationError("Trạm sạc đã hết chỗ trống!")
            
            # Giảm số chỗ trống NGAY khi tạo Quick_Booking
            self.station.available_slots -= 1
            self.station.save()

            self.expiry_time = timezone.now() + timedelta(minutes=10)
            self.qr_code_data = f"PAYMENT_FOR_BOOKING_{self.user_id}_{timezone.now().timestamp()}"
            
            # Đánh dấu slot là không còn trống
            if self.slot:
                self.slot.is_available = False
                self.slot.save()

        else:
            # Xử lý khi CẬP NHẬT booking đã tồn tại
            old_booking = Booking.objects.get(pk=self.pk)
            
            # Nếu chuyển từ Quick_Booking hoặc Confirmed sang Cancelled/Completed
            if self.status in ['Cancelled', 'Completed'] and old_booking.status not in ['Cancelled', 'Completed']:
                # Trả lại slot
                if self.slot:
                    self.slot.is_available = True
                    self.slot.save()
                
                # TRẢ LẠI available_slots
                self.station.available_slots += 1
                self.station.save()

        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.user_id} - {self.station.name}"


# Signal xử lý khi xóa booking từ Admin
@receiver(post_delete, sender=Booking)
def restore_slot_on_delete(sender, instance, **kwargs):
    """Trả lại slot khi xóa booking từ Admin"""
    if instance.slot:
        instance.slot.is_available = True
        instance.slot.save()
    
    # Chỉ cộng lại nếu booking chưa ở trạng thái kết thúc
    if instance.status not in ['Cancelled', 'Completed']:
        instance.station.available_slots += 1
        instance.station.save()