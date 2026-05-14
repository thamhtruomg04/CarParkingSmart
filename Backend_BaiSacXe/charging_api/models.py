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
    latitude = models.DecimalField(max_digits=20, decimal_places=14, null=True, blank=True)
    longitude = models.DecimalField(max_digits=20, decimal_places=14, null=True, blank=True)
    total_slots = models.IntegerField(default=0)
    available_slots = models.IntegerField(default=0)
    def save(self, *args, **kwargs):
        is_new = self.pk is None
        
        # Nếu là trạm mới, mặc định chỗ trống = tổng số chỗ
        if is_new:
            self.available_slots = self.total_slots
            
        super().save(*args, **kwargs)

        # LOGIC TỰ ĐỘNG TẠO SLOT (A1, A2... B1, B2...)
        # Chỉ chạy khi trạm mới được tạo và có số lượng total_slots > 0
        if is_new and self.total_slots > 0:
            new_slots = []
            for i in range(1, self.total_slots + 1):
                row_char = chr(65 + (i - 1) // 10)
                num = (i - 1) % 10 + 1
                slot_code = f"{row_char}{num}"
                new_slots.append(ChargingSlot(station=self, slot_code=slot_code))
            
            created_slots = ChargingSlot.objects.bulk_create(new_slots)

            # ← THÊM PHẦN NÀY: tự tạo TimeSlot cho từng ô vừa tạo
            time_slots = []
            for slot in created_slots:
                for hour in range(0, 23, 2):  # 0,2,4,...,22
                    time_slots.append(TimeSlot(
                        station=self,
                        slot=slot,
                        start_hour=hour,
                        is_available=True
                    ))
            TimeSlot.objects.bulk_create(time_slots, ignore_conflicts=True)

    def __str__(self):
        return self.name


class ChargingSlot(models.Model):
    station = models.ForeignKey(ChargingStation, on_delete=models.CASCADE, related_name='slots')
    slot_code = models.CharField(max_length=5) # Ví dụ: A1, B2...
    is_available = models.BooleanField(default=True)

    def __str__(self):
        return f"{self.station.name} - {self.slot_code}" 

class TimeSlot(models.Model):
    station = models.ForeignKey(ChargingStation, on_delete=models.CASCADE, related_name='time_slots')
    slot = models.ForeignKey(ChargingSlot, on_delete=models.CASCADE, related_name='time_slots', null=True, blank=True)
    start_hour = models.IntegerField()
    is_available = models.BooleanField(default=True)

    class Meta:
        unique_together = ('station', 'slot', 'start_hour')  # bỏ 'date'

    def __str__(self):
        end_hour = self.start_hour + 2
        return f"{self.start_hour:02d}:00 - {end_hour:02d}:00"

    @property
    def end_hour(self):
        return self.start_hour + 2

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
    time_slot = models.ForeignKey(TimeSlot, on_delete=models.SET_NULL, null=True, blank=True)
    scheduled_hour = models.IntegerField(null=True, blank=True, verbose_name="Khung giờ đặt (0-23)")

    def save(self, *args, **kwargs):
        is_new = self.pk is None

        if is_new:
            # Kiểm tra TimeSlot (ưu tiên khung giờ)
            if self.time_slot:
                if not self.time_slot.is_available:
                    raise ValidationError("Khung giờ này đã được đặt bởi người khác!")
                
                self.time_slot.is_available = False
                self.time_slot.save()

            # Kiểm tra ChargingSlot
            elif self.slot:
                existing = Booking.objects.filter(
                    slot=self.slot,
                    status__in=['Quick_Booking', 'Confirmed']
                ).exclude(pk=self.pk).exists()

                if existing:
                    raise ValidationError("Ô sạc này hiện đang có người sử dụng!")

                self.slot.is_available = False
                self.slot.save()

            # Kiểm tra slot trạm
            if self.station.available_slots <= 0:
                raise ValidationError("Trạm sạc hiện đã hết chỗ!")

            self.station.available_slots -= 1
            self.station.save()

            self.expiry_time = timezone.now() + timedelta(minutes=10)
            self.qr_code_data = f"PAYMENT_FOR_BOOKING_{self.user_id}_{timezone.now().timestamp()}"

        else:
            # Logic cập nhật (hủy/complete) giữ nguyên
            old = Booking.objects.get(pk=self.pk)
            if self.status in ['Cancelled', 'Completed'] and old.status not in ['Cancelled', 'Completed']:
                if self.slot:
                    self.slot.is_available = True
                    self.slot.save()
                if self.time_slot:
                    self.time_slot.is_available = True
                    self.time_slot.save()
                self.station.available_slots += 1
                self.station.save()

        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.user_id} - {self.station.name}"


# Signal xử lý khi xóa booking từ Admin
@receiver(post_delete, sender=Booking)
def restore_slot_on_delete(sender, instance, **kwargs):
    """Trả lại slot khi xóa booking từ Admin (Xử lý an toàn khi xóa Station)"""
    try:
        # Kiểm tra xem slot có tồn tại không trước khi truy cập
        if instance.slot_id: # Kiểm tra ID trước để tránh tự động truy vấn nếu không cần
            if instance.slot: # Truy cập instance.slot có thể gây lỗi nếu đã bị CASCADE xóa
                instance.slot.is_available = True
                instance.slot.save()
    except Exception:
        # Nếu slot đã bị xóa trước đó (do CASCADE từ Station), bỏ qua lỗi này
        pass
    
    try:
        # Tương tự với station, nếu xóa station thì instance.station cũng có thể gây lỗi
        if instance.status not in ['Cancelled', 'Completed']:
            # Chỉ thực hiện nếu Station vẫn còn tồn tại
            station = instance.station
            station.available_slots += 1
            station.save()
    except Exception:
        pass


