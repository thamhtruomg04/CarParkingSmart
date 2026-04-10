from rest_framework import serializers
from .models import ChargingStation, Booking
from django.contrib.auth.models import User
from .models import ChargingSlot 

class UserSerializer(serializers.ModelSerializer):
    class Meta:
        model = User
        fields = ['id', 'username', 'password', 'email']
        extra_kwargs = {'password': {'write_only': True}}

    def create(self, validated_data):
        user = User.objects.create_user(**validated_data)
        return user
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

class ChargingSlotSerializer(serializers.ModelSerializer):
    class Meta:
        model = ChargingSlot
        fields = ['id', 'slot_code', 'is_available'] # Các trường này phải khớp với Android