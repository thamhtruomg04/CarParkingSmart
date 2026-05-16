from rest_framework import viewsets, generics, status, decorators
from rest_framework.decorators import api_view
from rest_framework.response import Response
from django.utils import timezone
from django.core.exceptions import ValidationError
from .models import ChargingStation, Booking, ChargingSlot, TimeSlot
from .serializers import ChargingStationSerializer, BookingSerializer, UserSerializer, ChargingSlotSerializer

class RegisterView(generics.CreateAPIView):
    serializer_class = UserSerializer

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        if serializer.is_valid():
            user = serializer.save()
            return Response({
                "id": user.id,
                "username": user.username,
                "email": user.email
            }, status=status.HTTP_201_CREATED)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

class ChargingStationViewSet(viewsets.ModelViewSet):
    queryset = ChargingStation.objects.all()
    serializer_class = ChargingStationSerializer

    @decorators.action(detail=True, methods=['get'])
    def slots(self, request, pk=None):
        """
        URL tạo ra: GET /api/stations/{id}/slots/
        pk chính là ID của trạm sạc từ URL
        """
        slots = ChargingSlot.objects.filter(station_id=pk)
        serializer = ChargingSlotSerializer(slots, many=True)
        return Response(serializer.data)

    def list(self, request, *args, **kwargs):
        now = timezone.now()
        # Tự động hủy booking hết hạn
        expired_bookings = Booking.objects.filter(
            status='Quick_Booking',
            expiry_time__lt=now
        )
        for b in expired_bookings:
            b.status = 'Cancelled'
            b.save() 

        response = super().list(request, *args, **kwargs)
        
        # Thêm thông tin thực tế số khung giờ còn trống
        data = response.data
        for station in data:
            free_slots = TimeSlot.objects.filter(
                station_id=station['id'], 
                is_available=True
            ).count()
            station['real_available_time_slots'] = free_slots

        return response
    

    # Thêm signal hoặc sửa endpoint cancel để reset TimeSlot
    @decorators.action(detail=True, methods=['post'])
    def cancel(self, request, pk=None):
        booking = Booking.objects.get(pk=pk)
        if booking.status in ['Quick_Booking', 'Confirmed']:
            booking.status = 'Cancelled'
            # ✅ Trả lại TimeSlot
            if booking.time_slot:
                booking.time_slot.is_available = True
                booking.time_slot.save()
            booking.save()
        return Response({"message": "Đã hủy"})
    
    def perform_create(self, serializer):
        station = serializer.save()
        # Tự động tạo TimeSlot cho từng ô và từng khung giờ chẵn (0,2,4,...,22)
        slots = ChargingSlot.objects.filter(station=station)
        time_slots = []
        for slot in slots:
            for hour in range(0, 23, 2):  # 0,2,4,...,22 — mỗi ca 2 tiếng
                time_slots.append(TimeSlot(
                    station=station,
                    slot=slot,
                    start_hour=hour,
                    is_available=True
                ))
        TimeSlot.objects.bulk_create(time_slots, ignore_conflicts=True)
    def get_available_time_slots_count(self):
        """Đếm số khung giờ còn trống của toàn trạm"""
        return TimeSlot.objects.filter(
            station=self,
            is_available=True
        ).count()

    @property
    def available_charging_spots(self):
        """Số ô còn ít nhất 1 khung giờ trống"""
        from django.db.models import Exists, OuterRef
        slots_with_free_time = ChargingSlot.objects.filter(
            station=self,
            timeslot__is_available=True
        ).distinct().count()
        return slots_with_free_time
class BookingViewSet(viewsets.ModelViewSet):
    queryset = Booking.objects.all().order_by('-booking_time')
    serializer_class = BookingSerializer

    def create(self, request, *args, **kwargs):
        try:
            serializer = self.get_serializer(data=request.data)
            
            if serializer.is_valid(raise_exception=True):
                booking = serializer.save()
                
                return Response({
                    "id": booking.id,
                    "message": "Đặt chỗ thành công!",
                    "slot_code": booking.slot.slot_code if booking.slot else None,
                    "scheduled_hour": booking.scheduled_hour
                }, status=status.HTTP_201_CREATED)

        except ValidationError as e:
            error_msg = e.messages[0] if hasattr(e, 'messages') and e.messages else str(e)
            return Response({
                "error": "Không thể đặt chỗ",
                "detail": error_msg
            }, status=status.HTTP_400_BAD_REQUEST)

        except Exception as e:
            import traceback
            traceback.print_exc()
            return Response({
                "error": "Lỗi server",
                "detail": str(e)
            }, status=status.HTTP_500_INTERNAL_SERVER_ERROR)

    @decorators.action(detail=False, methods=['post'])
    def check_in(self, request):
        user_id = request.data.get('user_id')
        station_id = request.data.get('station_id')
        booking = Booking.objects.filter(
            user_id=user_id,
            station_id=station_id,
            status='Quick_Booking',
            is_checked_in=False
        ).first()

        if not booking:
            return Response({"error": "Không tìm thấy thông tin đặt chỗ!"}, status=status.HTTP_404_NOT_FOUND)

        if booking.expiry_time < timezone.now():
            return Response({"error": "Thời gian giữ chỗ đã hết hạn!"}, status=status.HTTP_400_BAD_REQUEST)

        booking.status = 'Confirmed'
        booking.is_checked_in = True
        booking.save()

        return Response({
            "message": "Check-in thành công! Bắt đầu sạc.",
            "status": booking.status
        }, status=status.HTTP_200_OK)

    @decorators.action(detail=False, methods=['post'])
    def complete_charging(self, request):
        user_id = request.data.get('user_id')
        booking = Booking.objects.filter(user_id=user_id, status='Confirmed').first()

        if booking:
            booking.status = 'Completed'
            booking.save()
            return Response({"message": "Đã hoàn tất sạc và trả chỗ!"})
        
        return Response({"error": "Không tìm thấy phiên sạc!"}, status=400)

    @decorators.action(detail=True, methods=['post'])
    def confirm_payment(self, request, pk=None):
        try:
            booking = Booking.objects.get(pk=pk)
        except Booking.DoesNotExist:
            return Response({"error": "Không tìm thấy booking!"}, 
                        status=status.HTTP_404_NOT_FOUND)

        # Đã confirmed rồi thì không làm gì
        if booking.status == "Confirmed":
            return Response({
                "message": "Đơn đặt chỗ đã được xác nhận trước đó."
            }, status=status.HTTP_200_OK)

        # Chỉ cho phép xác nhận khi đang ở trạng thái Quick_Booking
        if booking.status != "Quick_Booking":
            return Response({
                "error": "Chỉ có thể xác nhận thanh toán cho đơn Quick_Booking"
            }, status=status.HTTP_400_BAD_REQUEST)

        booking.status = "Confirmed"
        booking.save() 

        return Response({
            "message": "Thanh toán thành công! Chỗ sạc đã được giữ.",
            "booking_id": booking.id,
            "station": booking.station.name,
            "remaining_slots": booking.station.available_slots
        }, status=status.HTTP_200_OK)


    @decorators.action(detail=False, methods=['get'])
    def booked_hours(self, request):
        station_id = request.query_params.get('station_id')
        slot_id    = request.query_params.get('slot_id')

        if not station_id or not slot_id:
            return Response({"error": "Thiếu tham số"}, status=400)

        booked = TimeSlot.objects.filter(
            station_id=station_id,
            slot_id=slot_id,
            is_available=False
        ).values_list('start_hour', flat=True)

        return Response(list(booked))
    
    @decorators.action(detail=False, methods=['post'])
    def create_booking_with_slot(self, request):
        print("RAW DATA:", request.data)          # ← thêm dòng này
        print("CONTENT TYPE:", request.content_type)
        user_id = request.data.get('user_id')
        station_id = request.data.get('station')
        slot_id = request.data.get('slot')
        scheduled_hour = request.data.get('scheduled_hour')
        print(f"Parsed: user_id={user_id}, station={station_id}, slot={slot_id}, hour={scheduled_hour}")

        if any(v is None for v in [user_id, station_id, slot_id, scheduled_hour]):
            return Response({"error": "Thiếu thông tin"}, status=400)

        # Ép kiểu an toàn
        try:
            scheduled_hour = int(scheduled_hour)
            station_id = int(station_id)
            slot_id = int(slot_id)
        except (ValueError, TypeError):
            return Response({"error": "Dữ liệu không hợp lệ"}, status=400)

        try:
            station = ChargingStation.objects.get(id=station_id)
            slot = ChargingSlot.objects.get(id=slot_id)
            time_slot = TimeSlot.objects.get(
                station=station,
                slot=slot,
                start_hour=scheduled_hour
            )

            if not time_slot.is_available:
                return Response({"error": "Khung giờ này đã được đặt"}, status=400)

            time_slot.is_available = False
            time_slot.save()

            booking = Booking.objects.create(
                user_id=user_id,
                station=station,
                slot=slot,
                time_slot=time_slot,
                scheduled_hour=scheduled_hour,
                status='Quick_Booking'
            )

            return Response({"id": booking.id, "message": "Đặt chỗ thành công"}, status=201)

        except TimeSlot.DoesNotExist:
            return Response({"error": "Khung giờ không tồn tại"}, status=400)
        except Exception as e:
            return Response({"error": str(e)}, status=400)
