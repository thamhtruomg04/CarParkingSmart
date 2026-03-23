from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import ChargingStationViewSet, BookingViewSet

router = DefaultRouter()
router.register(r'stations', ChargingStationViewSet)
router.register(r'bookings', BookingViewSet)

urlpatterns = [
    path('', include(router.urls)),
]