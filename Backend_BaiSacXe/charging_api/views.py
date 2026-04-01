from rest_framework import viewsets, generics, status, decorators
from rest_framework.response import Response
from django.utils import timezone
from django.db import transaction
from .models import ChargingStation, Booking
from .serializers import ChargingStationSerializer, BookingSerializer, UserSerializer

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

    def list(self, request, *args, **kwargs):
        now = timezone.now()
        # Tìm các booking quá hạn mà vẫn đang ở trạng thái Quick_Booking
        expired_bookings = Booking.objects.filter(
            status='Quick_Booking',
            expiry_time__lt=now
        )

        # Chỉ cần chuyển status sang 'Cancelled', 
        # Model của bạn sẽ tự động cộng lại available_slots nhờ hàm save() đã viết.
        for b in expired_bookings:
            b.status = 'Cancelled'
            b.save() 

        return super().list(request, *args, **kwargs)

class BookingViewSet(viewsets.ModelViewSet):
    queryset = Booking.objects.all().order_by('-booking_time')
    serializer_class = BookingSerializer

    def create(self, request, *args, **kwargs):
        """Xử lý khi bấm 'Đặt ngay' từ Android"""
        return super().create(request, *args, **kwargs)

    @decorators.action(detail=False, methods=['post'])
    def check_in(self, request):
        """
        Action mới: API để Android gọi khi người dùng đến trạm và quét mã QR.
        URL: /api/bookings/check_in/
        """
        user_id = request.data.get('user_id')
        station_id = request.data.get('station_id')

        # Tìm booking đang giữ chỗ hợp lệ
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

        # Cập nhật trạng thái sang ĐANG SẠC
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
            booking.save() # Model tự cộng available_slots += 1
            return Response({"message": "Đã hoàn tất sạc và trả chỗ!"})
        
        return Response({"error": "Không tìm thấy phiên sạc!"}, status=400)