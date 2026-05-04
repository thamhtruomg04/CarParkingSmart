import os
import django

# 👇 sửa đúng tên project của bạn
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'server_parking.settings')

django.setup()

from datetime import date, timedelta
from charging_api.models import ChargingStation, TimeSlot


def create_fixed_time_slots(days=7):
    stations = ChargingStation.objects.all()
    if not stations.exists():
        print("❌ Chưa có trạm sạc nào trong database!")
        return

    today = date.today()
    created_count = 0

    for station in stations:
        for slot in station.slots.all():
            for d in range(days):
                current_date = today + timedelta(days=d)

                for h in range(0, 22, 2):
                    if h + 2 > 23:
                        continue

                    _, created = TimeSlot.objects.get_or_create(
                        station=station,
                        slot=slot,
                        date=current_date,
                        start_hour=h,
                        defaults={'is_available': True}
                    )
                    if created:
                        created_count += 1

    print(f'✅ Đã tạo {created_count} khung giờ!')


if __name__ == "__main__":
    create_fixed_time_slots(7)