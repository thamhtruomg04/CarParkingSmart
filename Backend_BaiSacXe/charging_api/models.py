from django.db import models
from django.utils import timezone
from datetime import timedelta
from django.core.exceptions import ValidationError

class ChargingStation(models.Model):
    name = models.CharField(max_length=255, verbose_name="Tên trạm")
    address = models.TextField(verbose_name="Địa chỉ")
    latitude = models.DecimalField(max_digits=12, decimal_places=9, null=True, blank=True)
    longitude = models.DecimalField(max_digits=12, decimal_places=9, null=True, blank=True)
    total_slots = models.IntegerField(default=0)
    available_slots = models.IntegerField(default=0)

    def __str__(self):
        return self.name

class Booking(models.Model):
    STATUS_CHOICES = [
        ('Pending', 'Chờ thanh toán'),
        ('Confirmed', 'Đã giữ chỗ'),
        ('Completed', 'Đã sạc xong'),
        ('Cancelled', 'Đã hủy'),
    ]

    user_id = models.CharField(max_length=100, verbose_name="ID người dùng")
    station = models.ForeignKey(ChargingStation, on_delete=models.CASCADE)
    booking_time = models.DateTimeField(auto_now_add=True)
    expiry_time = models.DateTimeField(null=True, blank=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='Pending')
    qr_code_data = models.TextField(null=True, blank=True)
    amount = models.DecimalField(max_digits=10, decimal_places=2, default=0)

    def save(self, *args, **kwargs):
        is_new = self.pk is None
        
        if is_new:
            if self.station.available_slots <= 0:
                raise ValidationError("Trạm sạc đã hết chỗ trống!")
            
            self.expiry_time = timezone.now() + timedelta(hours=1)
            self.qr_code_data = f"PAYMENT_FOR_BOOKING_{self.user_id}_{timezone.now().timestamp()}"
            
            self.station.available_slots -= 1
            self.station.save()

        elif self.status == 'Cancelled':
            previous_status = Booking.objects.get(pk=self.pk).status
            if previous_status != 'Cancelled':
                self.station.available_slots += 1
                self.station.save()

        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.user_id} - {self.station.name}"