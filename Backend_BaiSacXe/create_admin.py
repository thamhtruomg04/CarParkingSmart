import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'server_parking.settings')
django.setup()

from django.contrib.auth.models import User

username = 'truongtv'
email = 'truong.vu@tsg.net.vn' 
password = 'Hanoi@12345' 

user, created = User.objects.get_or_create(username=username, defaults={'email': email})
user.set_password(password) # Ép cập nhật mật khẩu mới nhất trong code
user.is_superuser = True
user.is_staff = True
user.save()

if created:
    print("Admin user created successfully!")
else:
    print("Admin user password updated successfully!")