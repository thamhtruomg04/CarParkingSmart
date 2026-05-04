import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'server_parking.settings')
django.setup()

from datetime import date, timedelta
from charging_api.models import ChargingStation, TimeSlot


def reset_and_create_time_slots(days=7):
    # 🔥 XÓA HẾT
    deleted_count, _ = TimeSlot.objects.all().delete()
    print(f"Đã xóa {deleted_count} TimeSlot")

    stations = ChargingStation.objects.all()
    if not stations.exists():
        print("Không có trạm!")
        return

    today = date.today()
    new_slots = []

    for station in stations:
        for slot in station.slots.all():
            for d in range(days):
                current_date = today + timedelta(days=d)

                for h in range(0, 22, 2):
                    if h + 2 > 23:
                        continue

                    new_slots.append(TimeSlot(
                        station=station,
                        slot=slot,
                        date=current_date,
                        start_hour=h,
                        is_available=True
                    ))

    # 🔥 tạo lại 1 lần
    TimeSlot.objects.bulk_create(new_slots)

    print(f"Đã tạo lại {len(new_slots)} TimeSlot")


if __name__ == "__main__":
    reset_and_create_time_slots(7)