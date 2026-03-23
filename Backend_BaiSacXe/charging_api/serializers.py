from rest_framework import serializers
from .models import ChargingStation, Booking

class ChargingStationSerializer(serializers.ModelSerializer):
    class Meta:
        model = ChargingStation
        fields = '__all__'

class BookingSerializer(serializers.ModelSerializer):
    station_name = serializers.ReadOnlyField(source='station.name')

    class Meta:
        model = Booking
        fields = '__all__'
        read_only_fields = ['expiry_time', 'qr_code_data', 'booking_time']