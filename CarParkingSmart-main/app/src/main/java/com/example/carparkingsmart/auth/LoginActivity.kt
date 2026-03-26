package com.example.carparkingsmart.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.carparkingsmart.MainActivity
import com.example.carparkingsmart.R
import com.example.carparkingsmart.api.LoginRequest

class LoginActivity : AppCompatActivity() {
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kiểm tra Token cũ (Auto Login)
        val sharedPref = getSharedPreferences("AUTH_PREF", Context.MODE_PRIVATE)
        if (sharedPref.getString("token", null) != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)

        btnLogin.setOnClickListener {
            // Sử dụng .trim() để loại bỏ khoảng trắng thừa nếu lỡ tay nhập nhầm
            val user = findViewById<EditText>(R.id.edtUsername).text.toString().trim()
            val pass = findViewById<EditText>(R.id.edtPassword).text.toString().trim()

            if (user.isNotEmpty() && pass.isNotEmpty()) {
                viewModel.login(LoginRequest(user, pass))
            } else {
                Toast.makeText(this, "Vui lòng nhập tài khoản và mật khẩu", Toast.LENGTH_SHORT).show()
            }
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        viewModel.loginResult.observe(this) { token ->
            if (token != null) {
                // Lưu Token vào bộ nhớ máy để Auto Login cho lần sau
                sharedPref.edit().putString("token", token).apply()

                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                // Nếu thất bại, thường là do sai URL hoặc sai User/Pass
                Toast.makeText(this, "Đăng nhập thất bại! Kiểm tra lại tài khoản.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}