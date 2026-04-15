import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'server_parking.settings')
django.setup()

from django.contrib.auth.models import User

username = 'truongtv'
email = 'truong.vu@tsg.net.vn' # Email của bạn
password = 'MatKhauCuaBan123' # Thay bằng mật khẩu bạn muốn

if not User.objects.filter(username=username).exists():
    User.objects.create_superuser(username, email, password)
    print("Admin user created successfully!")
else:
    print("Admin user already exists.")