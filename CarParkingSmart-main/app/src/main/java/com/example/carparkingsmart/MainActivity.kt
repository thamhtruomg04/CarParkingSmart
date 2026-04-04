package com.example.carparkingsmart

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random
import android.view.View

import androidx.room.Room
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.carparkingsmart.data.AppDatabase
import com.example.carparkingsmart.data.entity.ChargingStationEntity
import com.example.carparkingsmart.api.RetrofitClient

import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity() {
    private lateinit var db: AppDatabase

    private lateinit var map: MapView
    private lateinit var searchBox: AutoCompleteTextView
    private lateinit var btnVoice: ImageButton
    private lateinit var btnMyLocation: ImageButton
    private lateinit var btnLayers: ImageButton
    private lateinit var btnDirections: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var bottomSheet: LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var tvPlaceName: TextView
    private lateinit var tvPlaceAddress: TextView
    private lateinit var tvPlaceDistance: TextView
    private lateinit var tvPlaceRating: TextView
    private lateinit var tvPlaceCategory: TextView
    private lateinit var btnDirectionsBottom: Button
    private lateinit var btnSavePlace: LinearLayout
    private lateinit var btnSharePlace: LinearLayout
    private lateinit var btnNearby: LinearLayout

    private lateinit var chipRestaurant: MaterialCardView
    private lateinit var chipCafe: MaterialCardView
    private lateinit var chipHotel: MaterialCardView
    private lateinit var chipHospital: MaterialCardView

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var searchMarker: Marker? = null
    private var routeLine: Polyline? = null
    private var currentPlace: PlaceInfo? = null

    private var parkingLots: MutableList<ParkingLot> = mutableListOf()
    private val parkingMarkers = mutableListOf<Marker>()
    private val updateHandler = Handler(Looper.getMainLooper())
    private val notificationHandler = Handler(Looper.getMainLooper())

    private val suggestionsData = mutableListOf<PlaceInfo>()

    private lateinit var btnCloseSheet: ImageButton

    private lateinit var btnBookParking: Button
    private val suggestionsAdapter by lazy {
        ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line,
            suggestionsData.map { it.name })
    }
    private lateinit var btnSaveMySpot: Button
    private lateinit var btnFindMySpot: Button
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private var savedParkingSpot: GeoPoint? = null
    private var savedParkingTime: Long = 0L
    private var savedParkingName: String? = null

    private var currentBookingId: Int = -1

    private var previousNearestParking: ParkingLot? = null

    private var lastNotificationTime: Long = 0L

    private lateinit var btnShowParkingList: Button

    private lateinit var btnBookParkingLater: Button


    data class PlaceInfo(
        val id: Int,
        val lat: Double,
        val lon: Double,
        val name: String,
        val address: String,
        val category: String = "",
        val rating: String = ""
    )

    data class QuickBooking(
        val stationId: Int,
        val stationName: String,
        val bookingTime: Long,
        val expiryTime: Long
    )

    data class ParkingLot(
        val id: Int,
        val name: String,
        val ward: String,
        val lat: Double,
        val lon: Double,
        val totalSpots: Int,
        var availableSpots: Int,
        val address: String,
        var isNearest: Boolean = false,
        val hasChargingStation: Boolean = false,
        val totalChargingSpots: Int = 0,
        var availableChargingSpots: Int = 0
    )
    
    data class ChargingRequest(
        val vehicleId: String,
        val userLat: Double,
        val userLon: Double,
        val timestamp: Long,
        val distanceToStation: Double
    )
    
    private var currentNearestParking: ParkingLot? = null
    private val chargingRequestQueue = mutableListOf<ChargingRequest>()
    private var currentNearestChargingStation: ParkingLot? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cấu hình OSMDroid
        Configuration.getInstance().userAgentValue = "CarParkingSmart/1.0"
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        // Khởi tạo Database
        db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "charging_db").build()

        // Khởi tạo giao diện
        initViews()
        setupMap()
        setupMyLocation()
        setupSearchWithAutocomplete()
        setupButtons()
        setupBottomSheet()

        // setupCategoryChips() <-- Tạm thời comment dòng này lại nếu hàm này bên dưới vẫn đang gọi đến các chipRestaurant cũ

        // Tải dữ liệu từ Django
        loadChargingStationsFromDB()
    }

    private fun initViews() {
        map = findViewById(R.id.map)
        searchBox = findViewById(R.id.search_box)
        btnVoice = findViewById(R.id.btn_voice)
        btnMyLocation = findViewById(R.id.btn_my_location)
        btnLayers = findViewById(R.id.btn_layers)
        btnDirections = findViewById(R.id.btn_directions)
        bottomSheet = findViewById(R.id.bottom_sheet)
        tvPlaceName = findViewById(R.id.tv_place_name)
        tvPlaceAddress = findViewById(R.id.tv_place_address)
        tvPlaceDistance = findViewById(R.id.tv_place_distance)
        tvPlaceRating = findViewById(R.id.tv_place_rating)
        tvPlaceCategory = findViewById(R.id.tv_place_category)
        btnDirectionsBottom = findViewById(R.id.btn_directions_bottom)
        btnSavePlace = findViewById(R.id.btn_save_place)
        btnSharePlace = findViewById(R.id.btn_share_place)
        btnNearby = findViewById(R.id.btn_nearby)
        btnCloseSheet = findViewById(R.id.btn_close_sheet)

        btnBookParking = findViewById(R.id.btn_book_parking)
        btnBookParkingLater = findViewById(R.id.btn_book_parking_later) // THÊM DÒNG NÀY
        btnSaveMySpot = findViewById(R.id.btn_save_my_spot)
        btnFindMySpot = findViewById(R.id.btn_find_my_spot)
        btnShowParkingList = findViewById(R.id.btn_show_parking_list)
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        map.controller.setZoom(15.0)

        map.controller.setCenter(GeoPoint(21.0717, 105.7672))
    }

    private fun displayParkingLots(list: List<ParkingLot>? = null) {
        runOnUiThread {
            // 1. Kiểm tra Map và Dữ liệu
            val dataToShow = list ?: parkingLots
            if (map == null) return@runOnUiThread

            try {
                // 2. Xóa các Marker cũ an toàn
                parkingMarkers.forEach { map.overlays.remove(it) }
                parkingMarkers.clear()

                // 3. Vẽ từng địa điểm lên bản đồ
                dataToShow.forEach { parking ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(parking.lat, parking.lon)
                        title = parking.name

                        // Hiển thị trạng thái chỗ đỗ
                        val availability = if (parking.availableSpots <= 0) "Hết chỗ" else "Còn ${parking.availableSpots}/${parking.totalSpots} chỗ"

                        // Hiển thị thông tin trạm sạc
                        val chargingInfo = if (parking.hasChargingStation) {
                            if (parking.availableChargingSpots <= 0) "\n⚡ Hết chỗ sạc"
                            else "\n⚡ Trạm sạc: ${parking.availableChargingSpots}/${parking.totalChargingSpots}"
                        } else ""

                        // Tính khoảng cách
                        val distanceText = myLocationOverlay?.myLocation?.let { loc ->
                            " • " + formatDistance(calculateDistance(loc.latitude, loc.longitude, parking.lat, parking.lon))
                        } ?: ""

                        snippet = "$availability$distanceText$chargingInfo\n${parking.address}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        // Cập nhật Icon chuẩn (Dùng ContextCompat để chống lỗi Resource)
                        val iconRes = when {
                            parking.name.contains("ĐH Mỏ Địa chất", ignoreCase = true) || parking.isNearest ->
                                android.R.drawable.btn_star_big_on
                            parking.hasChargingStation && parking.availableChargingSpots > 0 ->
                                android.R.drawable.ic_menu_compass
                            parking.availableSpots <= 0 ->
                                android.R.drawable.ic_dialog_alert
                            else ->
                                android.R.drawable.ic_menu_mylocation
                        }
                        icon = ContextCompat.getDrawable(this@MainActivity, iconRes)

                        // ... (phần code bên trên của bạn giữ nguyên)

                        setOnMarkerClickListener { _, _ ->
                            // 1. Cập nhật dữ liệu vào các TextView trong Card
                            showParkingDetails(parking)

                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

                            showInfoWindow()
                            true
                        }

// ... (phần code bên dưới giữ nguyên)
                    }
                    parkingMarkers.add(marker)
                    map.overlays.add(marker)
                }
                map.invalidate() // Vẽ lại bản đồ
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showParkingDetails(parking: ParkingLot) {
        // Lưu thông tin địa điểm hiện tại
        currentPlace = PlaceInfo(
            parking.id,
            parking.lat,
            parking.lon,
            parking.name,
            parking.address,
            if (parking.hasChargingStation) "Trạm sạc xe điện" else "Bãi đỗ xe"
        )

        // 1. Cập nhật UI cơ bản
        tvPlaceName.text = parking.name + if (parking.isNearest) " ⚡ GẦN NHẤT" else ""
        tvPlaceAddress.text = parking.address

        // 2. Cập nhật trạng thái chỗ sạc/đỗ
        if (parking.hasChargingStation) {
            tvPlaceRating.text =
                "⚡ Còn ${parking.availableChargingSpots}/${parking.totalChargingSpots} chỗ sạc"
            tvPlaceRating.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            tvPlaceCategory.text = "Trạm sạc xe điện"
        } else {
            tvPlaceRating.text = "🅿Còn ${parking.availableSpots}/${parking.totalSpots} chỗ"
            tvPlaceRating.setTextColor(android.graphics.Color.parseColor("#757575"))
            tvPlaceCategory.text = "Bãi đỗ xe thường"
        }

        // 3. Hiển thị khoảng cách
        myLocationOverlay?.myLocation?.let { myLoc ->
            val distance =
                calculateDistance(myLoc.latitude, myLoc.longitude, parking.lat, parking.lon)
            tvPlaceDistance.text = "${formatDistance(distance)} từ vị trí của bạn"
        } ?: run {
            tvPlaceDistance.text = "Bật GPS để xem khoảng cách"
        }

        // 4. XỬ LÝ CÁC NÚT ĐẶT CHỖ (MỚI)

        // Nút Đặt Ngay (btn_book_parking)
        val btnBookNow = findViewById<Button>(R.id.btn_book_parking)
        btnBookNow.setOnClickListener {
            if (parking.availableChargingSpots > 0) {
                confirmQuickBooking(parking)
            } else {
                Toast.makeText(this, "Rất tiếc, trạm này hiện đã hết chỗ sạc!", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        // Nút Đặt Trước (btn_book_parking_later)
        val btnBookLater = findViewById<Button>(R.id.btn_book_parking_later)

        btnBookLater.setOnClickListener {
            // Kiểm tra xem đã có thông tin địa điểm chưa
            currentPlace?.let { place ->
                // GỌI HÀM THANH TOÁN QR MÀ BẠN VỪA FIX XONG
                showBookingPayment(place.name)
            } ?: run {
                // Nếu click quá nhanh khi data chưa load kịp
                Toast.makeText(this, "Vui lòng đợi trong giây lát...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getLoggedInUserEmail(): String {
        val sharedPref = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        return sharedPref.getString("user_email", "guest@example.com") ?: "guest@example.com"
    }

    // Hàm phụ: Xác nhận đặt ngay và cảnh báo 10 phút
    private fun confirmQuickBooking(parking: ParkingLot) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Xác nhận Đặt chỗ sạc")
            .setMessage("Hệ thống sẽ giữ chỗ cho bạn tại '${parking.name}' trong vòng 10 phút.")
            .setPositiveButton("Đồng ý & Chỉ đường") { _, _ ->
                val userEmail = getLoggedInUserEmail()

                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.instance.createBooking(
                            userId = userEmail,
                            stationId = parking.id,
                            status = "Quick_Booking"
                        )

                        if (response.isSuccessful) {
                            val body = response.body()
                            currentBookingId = body?.id ?: -1

                            Toast.makeText(this@MainActivity, "Đặt chỗ thành công!", Toast.LENGTH_SHORT).show()

                            // --- SỬA TẠI ĐÂY ---
                            // 1. Không cho phép người dùng kéo xuống để ẩn hẳn Card
                            bottomSheetBehavior.isHideable = false

                            // 2. Đưa về trạng thái thu gọn (hiện một phần thông tin và Timer)
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                            // -------------------

                            lifecycleScope.launch {
                                loadChargingStationsFromDB()
                                displayParkingLots()
                            }

                            showDirections()
                            startBookingTimer(15000L, parking.name)
                        } else {
                            Toast.makeText(this@MainActivity, "Thất bại: Trạm có thể đã hết chỗ!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("API_ERROR", "Error: ${e.message}")
                        Toast.makeText(this@MainActivity, "Lỗi kết nối máy chủ!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // 4. BỔ SUNG HÀM ĐẾM NGƯỢC
    private var bookingCountDownTimer: android.os.CountDownTimer? = null

    private fun startBookingTimer(duration: Long, stationName: String) {
        bookingCountDownTimer?.cancel() // Hủy cái cũ nếu có

        // Truyền vào 15000 (tương đương 15 giây) thay vì duration nếu bạn muốn ép buộc test nhanh
        val testDuration = 15000L

        bookingCountDownTimer = object : android.os.CountDownTimer(testDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60

                // Cập nhật giao diện đếm ngược
                tvPlaceRating.text = "TEST 15s - Giữ chỗ tại $stationName: ${String.format("%02d:%02d", minutes, seconds)}"
                tvPlaceRating.setTextColor(android.graphics.Color.RED)
            }

            override fun onFinish() {
                tvPlaceRating.text = "ĐÃ HẾT HẠN GIỮ CHỖ"
                tvPlaceRating.setTextColor(android.graphics.Color.GRAY)

                if (currentBookingId != -1) { // Kiểm tra ID trước khi gọi
                    lifecycleScope.launch {
                        try {
                            // Gọi API chuyển trạng thái sang Cancelled trên Django
                            RetrofitClient.instance.updateBookingStatus(currentBookingId, "Cancelled")

                            loadChargingStationsFromDB()
                            displayParkingLots()

                            Toast.makeText(this@MainActivity, "Đã tự động hủy giữ chỗ!", Toast.LENGTH_SHORT).show()
                            currentBookingId = -1 // Reset lại ID sau khi hủy
                        } catch (e: Exception) {
                            android.util.Log.e("TIMER_ERROR", "Lỗi: ${e.message}")
                        }
                    }
                }

                val btnBookNow = findViewById<Button>(R.id.btn_book_parking)
                btnBookNow.text = "Đặt lại ngay"
                btnBookNow.isEnabled = true
            }
        }.start()
    }

    private fun findNearestChargingStation() {
        myLocationOverlay?.myLocation?.let { myLoc ->
            val chargingStations = parkingLots.filter { 
                it.hasChargingStation && it.availableChargingSpots > 0 
            }
            
            if (chargingStations.isEmpty()) {
                Toast.makeText(this, "Không có trạm sạc nào còn chỗ trống", Toast.LENGTH_LONG).show()
                
                // Hiển thị tất cả trạm sạc (kể cả đầy)
                showAllChargingStations()
                return
            }
            
            val nearestStation = chargingStations.minByOrNull { station ->
                calculateDistance(myLoc.latitude, myLoc.longitude, station.lat, station.lon)
            }
            
            nearestStation?.let { station ->
                val distance = calculateDistance(myLoc.latitude, myLoc.longitude, station.lat, station.lon)
                
                currentNearestChargingStation = station
                
                Toast.makeText(this,
                    "⚡ TRẠM SẠC GẦN NHẤT\n" +
                    "${station.name}\n" +
                    "Còn ${station.availableChargingSpots}/${station.totalChargingSpots} chỗ sạc\n" +
                    "Cách ${formatDistance(distance)}",
                    Toast.LENGTH_LONG).show()
                
                // Zoom đến trạm sạc
                map.controller.animateTo(GeoPoint(station.lat, station.lon))
                map.controller.setZoom(16.0)
                
                showParkingDetails(station)
            }
        } ?: Toast.makeText(this, "Bật GPS để tìm trạm sạc", Toast.LENGTH_SHORT).show()
    }
    
    private fun showAllChargingStations() {
        myLocationOverlay?.myLocation?.let { myLoc ->
            val allStations = parkingLots
                .filter { it.hasChargingStation }
                .map { station ->
                    val distance = calculateDistance(myLoc.latitude, myLoc.longitude, station.lat, station.lon)
                    Triple(station, distance, station.availableChargingSpots > 0)
                }
                .sortedBy { it.second }
            
            val stationList = allStations.mapIndexed { index, (station, distance, hasSpots) ->
                val status = if (hasSpots) {
                    "Còn ${station.availableChargingSpots}/${station.totalChargingSpots} chỗ"
                } else {
                    "Đã đầy (0/${station.totalChargingSpots})"
                }
                "${index + 1}. ${station.name}\n" +
                "   $status • Cách ${formatDistance(distance)}"
            }.joinToString("\n\n")
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("TẤT CẢ TRẠM SẠC XE ĐIỆN")
                .setMessage(stationList)
                .setPositiveButton("Đóng", null)
                .show()
                
        } ?: Toast.makeText(this, "Bật GPS để xem danh sách", Toast.LENGTH_SHORT).show()
    }


    private fun setupMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }

        val provider = GpsMyLocationProvider(this)
        myLocationOverlay = MyLocationNewOverlay(provider, map)
        myLocationOverlay?.enableMyLocation()

        // Khi bắt được vị trí lần đầu tiên
        myLocationOverlay?.runOnFirstFix {
            runOnUiThread {
                zoomToMyLocation(false)
                // Cập nhật lại danh sách phường dựa trên vị trí mới bắt được
                updateWardChips()
            }
        }
        map.overlays.add(myLocationOverlay)
    }

    private fun setupButtons() {
        btnMyLocation.setOnClickListener { zoomToMyLocation(true) }

        btnLayers.setOnClickListener {
            showLayerOptions()
        }

        btnDirections.setOnClickListener {
            if (currentPlace != null) {
                showDirections()
            } else {
                Toast.makeText(this, "Vui lòng chọn địa điểm đích", Toast.LENGTH_SHORT).show()
            }
        }

        btnVoice.setOnClickListener {
            Toast.makeText(this, "Tính năng đang phát triển", Toast.LENGTH_SHORT).show()
        }

        btnSaveMySpot.setOnClickListener {
            myLocationOverlay?.myLocation?.let { loc ->
                savedParkingSpot = GeoPoint(loc.latitude, loc.longitude)
                savedParkingTime = System.currentTimeMillis()
                currentNearestParking?.let { savedParkingName = it.name }
                Toast.makeText(this, "Đã lưu vị trí xe tại ${savedParkingName ?: "vị trí hiện tại"}!", Toast.LENGTH_LONG).show()
                btnFindMySpot.visibility = View.VISIBLE  // Hiện nút tìm
            } ?: Toast.makeText(this, "Bật GPS để lưu vị trí", Toast.LENGTH_SHORT).show()
        }

        btnFindMySpot.setOnClickListener {
            myLocationOverlay?.myLocation?.let { currentLoc ->
                savedParkingSpot?.let { savedSpot ->
                    calculateRouteWalking(currentLoc.latitude, currentLoc.longitude, savedSpot.latitude, savedSpot.longitude)
                } ?: Toast.makeText(this, "Chưa lưu vị trí xe nào", Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, "Bật GPS để tìm", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateRouteWalking(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        Toast.makeText(this, "Đang tìm đường đi bộ về xe...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val url = URL("https://router.project-osrm.org/route/v1/foot/$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=polyline")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val result = JSONObject(json)
                    if (result.getString("code") == "Ok") {
                        val routes = result.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val geometry = route.getString("geometry")
                            runOnUiThread { drawRoute(geometry) }
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun zoomToMyLocation(animate: Boolean) {
        myLocationOverlay?.myLocation?.let {
            if (animate) {
                map.controller.animateTo(GeoPoint(it.latitude, it.longitude))
            } else {
                map.controller.setCenter(GeoPoint(it.latitude, it.longitude))
            }
            map.controller.setZoom(17.0)
        } ?: run {
            Toast.makeText(this, "Đang tìm vị trí...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchWithAutocomplete() {
        searchBox.setAdapter(suggestionsAdapter)
        searchBox.threshold = 2
        searchBox.dropDownHeight = 800

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }

                if (query.length >= 2) {
                    searchRunnable = Runnable {
                        loadSuggestions(query)
                    }
                    searchHandler.postDelayed(searchRunnable!!, 300)
                } else {
                    suggestionsData.clear()
                    suggestionsAdapter.clear()
                    suggestionsAdapter.notifyDataSetChanged()
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchBox.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val query = searchBox.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchLocation(query)
                    searchBox.dismissDropDown()
                    hideKeyboard()
                    true
                } else false
            } else false
        }

        searchBox.setOnItemClickListener { parent, _, position, _ ->
            if (position < suggestionsData.size) {
                val selected = suggestionsData[position]
                searchBox.setText(selected.name)
                showMarkerAtLocation(selected.lat, selected.lon, selected.name, selected.address)
                hideKeyboard()
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
    }

    private fun loadSuggestions(query: String) {
        // Tìm trong danh sách bãi đỗ xe trước
        val localMatches = parkingLots.filter { parking ->
            parking.name.contains(query, ignoreCase = true) ||
            parking.address.contains(query, ignoreCase = true)
        }.map { parking ->
            PlaceInfo(
                parking.id,
                parking.lat,
                parking.lon,
                parking.name + if (parking.hasChargingStation) " ⚡" else "",
                parking.address,
                "Bãi đỗ xe"
            )
        }
        
        // Nếu có kết quả local, hiển thị ngay
        if (localMatches.isNotEmpty()) {
            runOnUiThread {
                suggestionsData.clear()
                suggestionsData.addAll(localMatches)
                
                suggestionsAdapter.clear()
                suggestionsAdapter.addAll(localMatches.map { it.name })
                suggestionsAdapter.notifyDataSetChanged()
                
                if (searchBox.hasFocus()) {
                    searchBox.showDropDown()
                }
            }
            return
        }
        
        // Nếu không có kết quả local, tìm trên mạng
        Thread {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                var urlString = "https://nominatim.openstreetmap.org/search?format=json&q=$encoded&countrycodes=vn&limit=10&addressdetails=1&accept-language=vi"

                myLocationOverlay?.myLocation?.let { myLoc ->
                    urlString += "&lat=${myLoc.latitude}&lon=${myLoc.longitude}"
                }

                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "CarParkingSmart/1.0")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val array = JSONArray(json)
                    val tempList = mutableListOf<PlaceInfo>()
                    val displayNames = mutableListOf<String>()

                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val lat = obj.getDouble("lat")
                        val lon = obj.getDouble("lon")
                        val displayName = obj.getString("display_name")

                        val shortName = if (obj.has("name") && obj.getString("name").isNotEmpty()) {
                            obj.getString("name")
                        } else {
                            displayName.split(",")[0].trim()
                        }

                        if (!displayNames.contains(shortName)) {
                            tempList.add(PlaceInfo(0,lat, lon, shortName, displayName))
                            displayNames.add(shortName)
                        }
                    }

                    runOnUiThread {
                        suggestionsData.clear()
                        suggestionsData.addAll(tempList)

                        suggestionsAdapter.clear()
                        suggestionsAdapter.addAll(displayNames)
                        suggestionsAdapter.notifyDataSetChanged()

                        if (tempList.isNotEmpty() && searchBox.hasFocus()) {
                            searchBox.showDropDown()
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun searchLocation(query: String) {
        // Kiểm tra xem có phải tìm trạm sạc không
        val isChargingStationSearch = query.contains("sạc", ignoreCase = true) || 
                                      query.contains("điện", ignoreCase = true) ||
                                      query.contains("charging", ignoreCase = true)
        
        // Tìm trong danh sách bãi đỗ xe trước
        val matchingParkingLots = parkingLots.filter { parking ->
            parking.name.contains(query, ignoreCase = true) ||
            parking.address.contains(query, ignoreCase = true)
        }

        // Tiếp tục hàm searchLocation...
        if (matchingParkingLots.isNotEmpty()) {
            val firstMatch = matchingParkingLots[0]
            val point = GeoPoint(firstMatch.lat, firstMatch.lon)
            map.controller.animateTo(point)
            map.controller.setZoom(18.0)
            showParkingDetails(firstMatch)
            Toast.makeText(this, "Đã tìm thấy: ${firstMatch.name}", Toast.LENGTH_SHORT).show()
        } else {
            // Nếu không có trong bãi đỗ thì tìm trên bản đồ chung (Nominatim)
            loadSuggestions(query)
            Toast.makeText(this, "Tìm kiếm trên bản đồ...", Toast.LENGTH_SHORT).show()
        }

        // Nếu tìm "trạm sạc" hoặc "sạc xe điện"
        if (isChargingStationSearch) {
            runOnUiThread {
                findNearestChargingStation()
            }
            return
        }
        
        // Không tìm thấy trong danh sách → tìm trên mạng
        Toast.makeText(this, "Đang tìm kiếm...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                var urlString = "https://nominatim.openstreetmap.org/search?format=json&q=$encoded&countrycodes=vn&limit=1&addressdetails=1&accept-language=vi"

                myLocationOverlay?.myLocation?.let { myLoc ->
                    urlString += "&lat=${myLoc.latitude}&lon=${myLoc.longitude}"
                }

                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "CarParkingSmart/1.0")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val array = JSONArray(json)

                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        val lat = obj.getDouble("lat")
                        val lon = obj.getDouble("lon")
                        val displayName = obj.getString("display_name")
                        val name = if (obj.has("name") && obj.getString("name").isNotEmpty()) {
                            obj.getString("name")
                        } else {
                            query
                        }

                        runOnUiThread {
                            showMarkerAtLocation(lat, lon, name, displayName)
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Không tìm thấy: $query", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Lỗi kết nối: ${conn.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showMarkerAtLocation(lat: Double, lon: Double, title: String, address: String, category: String = "") {
        val point = GeoPoint(lat, lon)

        searchMarker?.let {
            map.overlays.remove(it)
            searchMarker = null
        }

        searchMarker = Marker(map).apply {
            position = point
            this.title = title.ifEmpty { "Vị trí tìm kiếm" }
            this.snippet = address
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            setOnMarkerClickListener { m, _ ->
                showPlaceDetails(
                    m.position.latitude,
                    m.position.longitude,
                    m.title,
                    m.snippet,
                    category
                )
                true
            }
        }

        map.overlays.add(searchMarker)
        map.controller.animateTo(point)
        map.controller.setZoom(17.0)
        map.invalidate()

        showPlaceDetails(lat, lon, title, address, category)
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        // 1. Cấu hình mặc định
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.peekHeight = 300
        bottomSheetBehavior.isHideable = true

        // 2. Callback xử lý logic khóa Card
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // Nếu đang có giao dịch mà bị kéo ẩn (bằng tay), thì ép hiện lại
                if (currentBookingId != -1 && newState == BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.isHideable = false
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        // 3. Logic Nút X (Sửa lại logic 2 bước ẩn)
        btnCloseSheet.setOnClickListener {
            if (currentBookingId != -1) {
                Toast.makeText(this, "Đang có giao dịch đặt chỗ, chỉ có thể thu nhỏ!", Toast.LENGTH_SHORT).show()
                bottomSheetBehavior.isHideable = false
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                return@setOnClickListener
            }

            // Trường hợp không có giao dịch
            bottomSheetBehavior.isHideable = true

            when (bottomSheetBehavior.state) {
                BottomSheetBehavior.STATE_EXPANDED -> {
                    // Nếu đang mở to -> Thu nhỏ lại (Bước 1)
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    // Nếu đang thu nhỏ -> Ấn lần nữa mới ẩn hẳn (Bước 2)
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    clearMapOverlays() // Hàm dọn dẹp map (viết bên dưới)
                }
                else -> {
                    // Nếu lỡ đang ở trạng thái khác thì cứ ẩn đi
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }
        }

        // --- CÁC NÚT HÀNH ĐỘNG KHÁC ---
        btnDirectionsBottom.setOnClickListener {
            currentPlace?.let {
                showDirections()
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } ?: Toast.makeText(this, "Chưa chọn địa điểm đích", Toast.LENGTH_SHORT).show()
        }

        btnSharePlace.setOnClickListener {
            currentPlace?.let { place ->
                val shareText = "${place.name}\n${place.address}\n\nhttps://www.google.com/maps?q=${place.lat},${place.lon}"
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ địa điểm"))
            }
        }

        btnNearby.setOnClickListener {
            currentPlace?.let { place ->
                map.controller.animateTo(GeoPoint(place.lat, place.lon))
                map.controller.setZoom(18.0)
                // Đảm bảo hiện lên nếu đang ẩn
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    // Hàm phụ để dọn dẹp bản đồ khi đóng hẳn
    private fun clearMapOverlays() {
        routeLine?.let { map.overlays.remove(it); routeLine = null }
        searchMarker?.let { map.overlays.remove(it); searchMarker = null }
        map.invalidate()
        currentPlace = null
    }

    private fun showPlaceDetails(lat: Double, lon: Double, name: String, address: String, category: String = "") {
        currentPlace = PlaceInfo(0, lat, lon, name, address, category)

        tvPlaceName.text = name
        tvPlaceAddress.text = address

        val rating = String.format("%.1f", 3.5 + Math.random() * 1.5)
        tvPlaceRating.text = "$rating ★"
        tvPlaceCategory.text = if (category.isNotEmpty()) category else "Địa điểm"

        myLocationOverlay?.myLocation?.let { myLoc ->
            val distance = calculateDistance(myLoc.latitude, myLoc.longitude, lat, lon)
            tvPlaceDistance.text = "${formatDistance(distance)} từ vị trí của bạn"
        } ?: run {
            tvPlaceDistance.text = "Bật GPS để xem khoảng cách"
        }

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.peekHeight = 400
    }

    private fun showBookingPayment(placeName: String) {
        // 1. Inflate Layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_payment_qr, null)
        val imgQR = dialogView.findViewById<ImageView>(R.id.img_qr_code)
        val tvTimerInDialog = dialogView.findViewById<TextView>(R.id.tv_payment_timer)

        if (imgQR == null || tvTimerInDialog == null) {
            Toast.makeText(this, "Lỗi Layout Dialog!", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Tạo link QR (Dùng ID đơn hàng sẽ tốt hơn dùng tên trạm)
        val qrUrl = "https://img.vietqr.io/image/ICB-108876696755-compact.png?amount=50000&addInfo=DatCho_${currentBookingId}"

        Glide.with(this).load(qrUrl).into(imgQR)

        // 3. Khởi tạo Dialog
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("Tôi đã chuyển khoản") { d, _ ->
                xacNhanThanhToan(currentBookingId)
                d.dismiss()
            }
            .setNegativeButton("Hủy") { d, _ ->
                bookingCountDownTimer?.cancel()
                // Trả lại UI ban đầu nếu hủy
                tvPlaceRating.text = "⭐ 4.8"
                tvPlaceRating.setTextColor(Color.parseColor("#4CAF50"))
                d.dismiss()
            }
            .create()

        dialog.show()

        // 4. LOGIC ĐẾM NGƯỢC (Sửa tại đây)
        bookingCountDownTimer?.cancel() // Xóa timer cũ nếu có

        // 1800000ms = 30 phút
        bookingCountDownTimer = object : android.os.CountDownTimer(1800000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                val timeString = String.format("%02d:%02d", minutes, seconds)

                // Cập nhật vào TextView trong Dialog (nếu dialog còn đang hiện)
                if (dialog.isShowing) {
                    tvTimerInDialog.text = "Hiệu lực thanh toán: $timeString"
                }

                // Cập nhật vào tvPlaceRating ở BottomSheet (Dùng runOnUiThread cho chắc chắn)
                runOnUiThread {
                    if (::tvPlaceRating.isInitialized) {
                        tvPlaceRating.text = "⏳ Giữ chỗ: $timeString"
                        tvPlaceRating.setTextColor(Color.RED)
                    }
                }
            }

            override fun onFinish() {
                if (dialog.isShowing) dialog.dismiss()
                handleBookingExpired()
            }
        }.start()
    }

    private fun xacNhanThanhToan(bookingId: Int) {
        if (bookingId == -1) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.updateBookingStatus(bookingId, "Confirmed")
                bookingCountDownTimer?.cancel()

                // VỊ TRÍ SỬA 3: Mở khóa nút X sau khi thanh toán xong
                currentBookingId = -1

                loadChargingStationsFromDB()
                Toast.makeText(this@MainActivity, "Xác nhận thành công!", Toast.LENGTH_LONG).show()

                runOnUiThread {
                    tvPlaceRating.text = "✅ Đã xác nhận"
                    tvPlaceRating.setTextColor(Color.parseColor("#4CAF50"))
                    bottomSheetBehavior.isHideable = true
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Lỗi kết nối Server!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleBookingExpired() {
        if (currentBookingId != -1) {
            lifecycleScope.launch {
                try {
                    RetrofitClient.instance.updateBookingStatus(currentBookingId, "Cancelled")

                    // VỊ TRÍ SỬA 2: Đưa về -1 để nút X hoạt động lại
                    currentBookingId = -1

                    loadChargingStationsFromDB()
                    Toast.makeText(this@MainActivity, "Hết thời gian! Nút xóa chỉ đường đã có hiệu lực.", Toast.LENGTH_LONG).show()

                    tvPlaceRating.text = "⭐ 4.8"
                    tvPlaceRating.setTextColor(Color.parseColor("#4CAF50"))
                    bottomSheetBehavior.isHideable = true

                } catch (e: Exception) {
                    android.util.Log.e("API_ERROR", "Lỗi: ${e.message}")
                }
            }
        }
    }

    private fun showDirections() {
        currentPlace?.let { place ->
            myLocationOverlay?.myLocation?.let { myLoc ->
                calculateRoute(myLoc.latitude, myLoc.longitude, place.lat, place.lon)
            } ?: run {
                Toast.makeText(this, "Không thể xác định vị trí hiện tại", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun calculateRoute(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double) {
        Toast.makeText(this, "Đang tính toán đường đi...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val url = URL("https://router.project-osrm.org/route/v1/driving/$fromLon,$fromLat;$toLon,$toLat?overview=full&geometries=polyline")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val result = JSONObject(json)

                    if (result.getString("code") == "Ok") {
                        val routes = result.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val geometry = route.getString("geometry")
                            val distance = route.getDouble("distance")
                            val duration = route.getDouble("duration")

                            runOnUiThread {
                                drawRoute(geometry)
                                val distText = formatDistance(distance)
                                val timeText = formatDuration(duration)
                                Toast.makeText(this, "Khoảng cách: $distText • Thời gian: $timeText",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Không tìm thấy đường đi", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Không thể tính toán: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun drawRoute(encodedPolyline: String) {
        routeLine?.let { map.overlays.remove(it) }

        val points = decodePolyline(encodedPolyline)
        routeLine = Polyline().apply {
            outlinePaint.color = Color.parseColor("#1A73E8")
            outlinePaint.strokeWidth = 14f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            setPoints(points)
        }

        map.overlays.add(routeLine)
        map.invalidate()

        if (points.isNotEmpty()) {
            val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(points)
            map.zoomToBoundingBox(bounds, true, 100)
        }
    }

    private fun decodePolyline(encoded: String): List<GeoPoint> {
        val poly = mutableListOf<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            poly.add(GeoPoint(lat / 1E5, lng / 1E5))
        }
        return poly
    }

    private fun showLayerOptions() {
        val items = arrayOf("Bản đồ tiêu chuẩn", "Bản đồ vệ tinh", "Bản đồ địa hình")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Chọn kiểu bản đồ")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> map.setTileSource(TileSourceFactory.MAPNIK)
                    1 -> map.setTileSource(TileSourceFactory.USGS_SAT)
                    2 -> map.setTileSource(TileSourceFactory.OPEN_SEAMAP)
                }
                map.invalidate()
                Toast.makeText(this, "Đã chuyển sang ${items[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            "%.1f km".format(meters / 1000)
        }
    }

    private fun formatDuration(seconds: Double): String {
        val minutes = (seconds / 60).toInt()
        return if (minutes < 60) {
            "$minutes phút"
        } else {
            val hours = minutes / 60
            val mins = minutes % 60
            "$hours giờ $mins phút"
        }
    }

    // 1. Hàm tạo danh sách Chip (Gọi cái này trong runOnUiThread của loadChargingStationsFromDB)
    private fun updateWardChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_wards)
        chipGroup.removeAllViews()

        // 1. Lấy vị trí hiện tại của người dùng
        val userLoc = myLocationOverlay?.myLocation

        // 2. Lọc danh sách phường dựa trên khoảng cách
        val nearbyWards = if (userLoc != null) {
            parkingLots.filter { station ->
                // Tính khoảng cách từ người dùng đến trạm
                val distance = calculateDistance(
                    userLoc.latitude, userLoc.longitude,
                    station.lat, station.lon
                )
                distance <= 5000 // Chỉ lấy các trạm trong bán kính 5km (5000 mét)
            }.map { it.ward }.distinct().sorted()
        } else {
            // Nếu chưa có GPS, tạm thời hiện tất cả hoặc hiện danh sách trống tùy bạn
            parkingLots.map { it.ward }.distinct().sorted()
        }

        // 3. Luôn thêm nút "Tất cả" đầu tiên
        addWardChip("Tất cả", true)

        // 4. Chỉ thêm các Chip của phường thỏa mãn điều kiện 5km
        nearbyWards.forEach { wardName ->
            if (wardName != "Tất cả") {
                addWardChip(wardName, false)
            }
        }
    }

    // 2. Hàm vẽ từng Chip lên màn hình
    private fun addWardChip(wardName: String, isDefault: Boolean) {
        val chip = Chip(this)
        chip.text = wardName
        chip.isCheckable = true
        chip.isChecked = isDefault

        // Sử dụng màu từ colors.xml (Đảm bảo đã thêm green_main vào colors.xml)
        chip.setChipBackgroundColorResource(if (isDefault) R.color.green_main else R.color.white)
        chip.setTextColor(if (isDefault) android.graphics.Color.WHITE else android.graphics.Color.BLACK)

        chip.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                filterByWard(wardName)
            }
        }
        findViewById<ChipGroup>(R.id.chip_group_wards).addView(chip)
    }

    private fun showParkingListDialog(wardName: String, list: List<ParkingLot>) {
        // Tạo danh sách tên để hiển thị
        val stationNames = list.map { it.name }.toTypedArray()

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Bãi đỗ tại Phường $wardName")

        builder.setItems(stationNames) { _, which ->
            // Lấy đối tượng bãi đỗ được chọn
            val selectedStation = list[which]

            // 1. Di chuyển camera đến bãi đỗ đó
            val point = GeoPoint(selectedStation.lat, selectedStation.lon)
            map.controller.animateTo(point)
            map.controller.setZoom(18.0)

            // 2. Hiển thị thông tin chi tiết (Mở Bottom Sheet)
            showParkingDetails(selectedStation)
        }

        builder.setNegativeButton("Đóng", null)
        builder.show()
    }

    // 3. Hàm lọc dữ liệu chuẩn
    private fun filterByWard(wardName: String) {
        val filteredList = if (wardName == "Tất cả") {
            parkingLots
        } else {
            parkingLots.filter { it.ward == wardName }
        }

        // 1. Luôn cập nhật Marker trên bản đồ trước
        displayParkingLots(filteredList)

        // 2. Nếu chọn một phường cụ thể và có dữ liệu, hiện danh sách tên bãi đỗ
        if (wardName != "Tất cả" && filteredList.isNotEmpty()) {
            showParkingListDialog(wardName, filteredList)
        }
    }

    private fun loadChargingStationsFromDB() {
        lifecycleScope.launch {
            try {
                val apiStations = RetrofitClient.instance.getStations()

                // Xóa cũ thêm mới an toàn
                parkingLots.clear()

                apiStations.forEach { station ->
                    parkingLots.add(ParkingLot(
                        id = station.id,
                        name = station.name,
                        ward = station.ward ?: "Khác",
                        lat = station.latitude,
                        lon = station.longitude,
                        totalSpots = station.total_slots ?: 0,
                        availableSpots = station.available_slots ?: 0,
                        address = station.address ?: "",
                        hasChargingStation = true,
                        totalChargingSpots = station.total_slots ?: 0,
                        availableChargingSpots = station.available_slots ?: 0
                    ))
                }

                // ĐƯA LÊN UI THREAD ĐỂ VẼ - KHÔNG SẼ BỊ OUT
                runOnUiThread {
                    if (parkingLots.isNotEmpty()) {
                        displayParkingLots(parkingLots)
                        updateWardChips()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupMyLocation()
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchHandler.removeCallbacksAndMessages(null)
        updateHandler.removeCallbacksAndMessages(null)
        notificationHandler.removeCallbacksAndMessages(null)
    }
}