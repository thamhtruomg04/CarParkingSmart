package com.example.carparkingsmart

import android.Manifest
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

    private var previousNearestParking: ParkingLot? = null

    private var lastNotificationTime: Long = 0L

    private lateinit var btnShowParkingList: Button

    private fun setupWardChips(allStations: List<ParkingLot>) {
        val chipGroup = findViewById<ChipGroup>(R.id.chip_group_wards)
        chipGroup.removeAllViews()

        val wards = allStations.map { extractWard(it.address) }.distinct().sorted()

        wards.forEach { wardName ->
            // Sửa lỗi: Thêm 'this' vào Chip constructor
            val chip = Chip(this).apply {
                text = wardName
                isCheckable = true

                // Sửa lỗi 1: Dùng ColorStateList thay vì Resource ID để tránh lỗi green_500
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))

                // Sửa lỗi 2: Ép kiểu màu trắng chuẩn Android
                setTextColor(android.graphics.Color.WHITE)

                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        val filteredList = allStations.filter { it.address.contains(wardName, ignoreCase = true) }
                        // Sửa lỗi 3: Đảm bảo hàm displayParkingLots nhận vào 1 List
                        displayParkingLots(filteredList)
                    } else {
                        displayParkingLots(allStations)
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun extractWard(address: String): String {
        val keywords = listOf(
            "Đức Thắng", "Cổ Nhuế", "Xuân Đỉnh", "Phú Diễn",
            "Minh Khai", "Xuân La", "Liên Mạc", "Thụy Phương"
        )

        // Tìm từ khóa trong địa chỉ (không phân biệt hoa thường)
        val found = keywords.find { address.contains(it, ignoreCase = true) }

        return found ?: "Khác"
    }
    /*
    private fun extractWard(address: String): String {
        val regex = Regex("(Phường|P\\.)\\s+([^,]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(address)

        return match?.groupValues?.get(2)?.trim() ?: "Khác"
    }*/

    data class PlaceInfo(
        val lat: Double,
        val lon: Double,
        val name: String,
        val address: String,
        val category: String = "",
        val rating: String = ""
    )

    data class ParkingLot(
        val id: Int,
        val name: String,
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

        btnBookParking = findViewById(R.id.btn_book_parking)
        btnSaveMySpot = findViewById(R.id.btn_save_my_spot)
        btnFindMySpot = findViewById(R.id.btn_find_my_spot)
        btnShowParkingList = findViewById(R.id.btn_show_parking_list)

        // XÓA HOẶC COMMENT CÁC DÒNG chipRestaurant, chipCafe... VÌ XML KHÔNG CÓ
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

                        setOnMarkerClickListener { _, _ ->
                            showParkingDetails(parking)
                            showInfoWindow()
                            true
                        }
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

    private fun bookParkingSpot(parking: ParkingLot) {
        if (parking.availableSpots <= 0) {
            Toast.makeText(this, "Rất tiếc, chỗ đã hết rồi!", Toast.LENGTH_LONG).show()
            return
        }

        parking.availableSpots--
        Toast.makeText(this, "ĐẶT TRƯỚC thành công tại ${parking.name}!", Toast.LENGTH_LONG).show()

        displayParkingLots()           // cập nhật lại marker
        showParkingDetails(parking)    // refresh thông tin bottom sheet
    }

    private fun showParkingDetails(parking: ParkingLot) {
        currentPlace = PlaceInfo(
            parking.lat,
            parking.lon,
            parking.name,
            parking.address,
            if (parking.hasChargingStation) "Trạm sạc xe điện" else "Bãi đỗ xe"
        )

        // 1. Tên bãi và trạng thái gần nhất
        tvPlaceName.text = parking.name + if (parking.isNearest) " ⚡ GẦN NHẤT" else ""
        tvPlaceAddress.text = parking.address

        // 2. QUAN TRỌNG: Ghi đè chữ "Đang tải" bằng dữ liệu thực tế
        if (parking.hasChargingStation) {
            // Cập nhật vào TextView tv_place_rating
            tvPlaceRating.text = "⚡ Còn ${parking.availableChargingSpots}/${parking.totalChargingSpots} chỗ sạc"
            tvPlaceRating.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Màu xanh lá

            // Cập nhật category
            tvPlaceCategory.text = "Trạm sạc xe điện"
        } else {
            tvPlaceRating.text = "🅿️ Còn ${parking.availableSpots}/${parking.totalSpots} chỗ"
            tvPlaceRating.setTextColor(android.graphics.Color.parseColor("#757575")) // Màu xám

            tvPlaceCategory.text = "Bãi đỗ xe thường"
        }

        // 3. Tính toán khoảng cách
        myLocationOverlay?.myLocation?.let { myLoc ->
            val distance = calculateDistance(myLoc.latitude, myLoc.longitude, parking.lat, parking.lon)
            tvPlaceDistance.text = "${formatDistance(distance)} từ vị trí của bạn"
        } ?: run {
            tvPlaceDistance.text = "Bật GPS để xem khoảng cách"
        }

        // 4. Cấu hình Bottom Sheet (Giữ thông báo hiện ra)
        bottomSheetBehavior.peekHeight = 450 // Tăng nhẹ chiều cao để không bị che chữ
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // 5. Nút đặt chỗ
        btnBookParking.visibility = View.VISIBLE
        btnBookParking.setOnClickListener {
            if (parking.hasChargingStation) {
                showChargingStationDialog(parking)
            } else {
                bookParkingSpot(parking)
            }
        }
    }

    private fun startParkingUpdates() {
        // Cập nhật số chỗ trống mỗi 10 giây (giả lập)
        val updateRunnable = object : Runnable {
            override fun run() {
                parkingLots.forEach { parking ->
                    // Giả lập thay đổi số chỗ trống (-2 đến +3)
                    val change = Random.nextInt(-2, 4)
                    parking.availableSpots = (parking.availableSpots + change)
                        .coerceIn(0, parking.totalSpots)
                    
                    // Cập nhật chỗ sạc nếu có
                    if (parking.hasChargingStation) {
                        val chargingChange = Random.nextInt(-1, 3)
                        parking.availableChargingSpots = (parking.availableChargingSpots + chargingChange)
                            .coerceIn(0, parking.totalChargingSpots)
                    }
                }
                displayParkingLots()
                processChargingRequestQueue()
                updateHandler.postDelayed(this, 10000) // 10 giây
            }
        }
        updateHandler.post(updateRunnable)
    }

    private fun startProximityCheck() {
        // Kiểm tra khoảng cách mỗi 15 giây
        val proximityRunnable = object : Runnable {
            override fun run() {
                myLocationOverlay?.myLocation?.let { myLoc ->
                    checkNearbyParkingLots(myLoc.latitude, myLoc.longitude)
                }
                notificationHandler.postDelayed(this, 15000) // 15 giây
            }
        }
        notificationHandler.postDelayed(proximityRunnable, 5000) // Bắt đầu sau 5 giây
    }

    private fun checkNearbyParkingLots(userLat: Double, userLon: Double) {
        val nearbyThreshold = 2000.0

        val parkingWithDistance = parkingLots
            .filter { it.availableSpots > 0 }
            .map { parking ->
                val distance = calculateDistance(userLat, userLon, parking.lat, parking.lon)
                Triple(parking, distance, distance <= nearbyThreshold)
            }
            .sortedBy { it.second }

        // Reset trạng thái cũ
        currentNearestParking?.isNearest = false
        parkingLots.forEach { it.isNearest = false }

        if (parkingWithDistance.isNotEmpty()) {
            val nearest = parkingWithDistance[0].first
            nearest.isNearest = true

            // Chỉ thông báo nếu bãi gần nhất thay đổi (không spam)
            if (nearest.id != previousNearestParking?.id ||
                (System.currentTimeMillis() - lastNotificationTime > 5 * 60 * 1000)) {  // 5 phút
                currentNearestParking = nearest
                previousNearestParking = nearest
                lastNotificationTime = System.currentTimeMillis()

                if (parkingWithDistance[0].second <= 1500) {
                    showNearestParkingNotification(nearest, parkingWithDistance[0].second)
                }
            }
        } else {
            // Không còn bãi nào gần → reset
            previousNearestParking = null
            currentNearestParking = null
        }

        displayParkingLots()
    }

    private fun showNearestParkingNotification(parking: ParkingLot, distance: Double) {
        val chargingInfo = if (parking.hasChargingStation) {
            "⚡ Còn ${parking.availableChargingSpots} cổng sạc • "
        } else ""
        
        val message = "⚡ TRẠM SẠC GẦN NHẤT: ${parking.name}\n" +
                chargingInfo +
                "Còn ${parking.availableSpots} chỗ • Cách ${formatDistance(distance)}\n" +
                "Đặt trước ngay để sạc xe điện của bạn!"

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    // ============ TÍNH NĂNG TRẠM SẠC XE ĐIỆN ============
    
    private fun showChargingStationDialog(parking: ParkingLot) {
        val options = if (parking.availableChargingSpots > 0) {
            arrayOf("Đặt chỗ đỗ thường", "Đặt chỗ sạc xe điện", "Tìm trạm sạc gần nhất")
        } else {
            arrayOf("Đặt chỗ đỗ thường", "Tìm trạm sạc gần nhất (Trạm này đã đầy)")
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("${parking.name}")
            .setMessage("Bãi này có trạm sạc xe điện\n⚡ Còn ${parking.availableChargingSpots}/${parking.totalChargingSpots} chỗ sạc")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> bookParkingSpot(parking)
                    1 -> {
                        if (parking.availableChargingSpots > 0) {
                            requestChargingSpot(parking)
                        }
                    }
                    2 -> findNearestChargingStation()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }
    
    private fun requestChargingSpot(parking: ParkingLot) {
        myLocationOverlay?.myLocation?.let { myLoc ->
            val distance = calculateDistance(myLoc.latitude, myLoc.longitude, parking.lat, parking.lon)
            val vehicleId = "VEHICLE_${System.currentTimeMillis()}"
            
            val request = ChargingRequest(
                vehicleId = vehicleId,
                userLat = myLoc.latitude,
                userLon = myLoc.longitude,
                timestamp = System.currentTimeMillis(),
                distanceToStation = distance
            )
            
            // Thêm vào hàng đợi
            chargingRequestQueue.add(request)
            
            // Sắp xếp theo khoảng cách (ưu tiên xe gần nhất)
            chargingRequestQueue.sortBy { it.distanceToStation }
            
            // Xử lý yêu cầu
            processChargingRequest(parking, request)
            
        } ?: Toast.makeText(this, "Bật GPS để đặt chỗ sạc", Toast.LENGTH_SHORT).show()
    }
    
    private fun processChargingRequest(parking: ParkingLot, request: ChargingRequest) {
        if (parking.availableChargingSpots <= 0) {
            // Hết chỗ - gợi ý trạm khác
            Toast.makeText(this, "Trạm sạc đã đầy! Đang tìm trạm thay thế...", Toast.LENGTH_SHORT).show()
            suggestAlternativeChargingStations(request.userLat, request.userLon, parking)
            return
        }
        
        // Kiểm tra vị trí trong hàng đợi
        val position = chargingRequestQueue.indexOf(request) + 1
        val queueSize = chargingRequestQueue.size
        
        if (position == 1) {
            // Xe gần nhất - được ưu tiên
            parking.availableChargingSpots--
            parking.availableSpots--
            chargingRequestQueue.remove(request)
            
            Toast.makeText(this, 
                "✅ ĐẶT CHỖ SẠC THÀNH CÔNG!\n" +
                "Bạn được ưu tiên vì gần trạm nhất\n" +
                "⚡ ${parking.name}\n" +
                "Còn ${parking.availableChargingSpots} chỗ sạc",
                Toast.LENGTH_LONG).show()
                
            displayParkingLots()
            showParkingDetails(parking)
        } else {
            // Không phải xe gần nhất
            Toast.makeText(this,
                "⏳ Đã thêm vào hàng đợi (Vị trí: $position/$queueSize)\n" +
                "Có ${position - 1} xe gần hơn đang được ưu tiên\n" +
                "Khoảng cách của bạn: ${formatDistance(request.distanceToStation)}",
                Toast.LENGTH_LONG).show()
        }
    }
    
    private fun processChargingRequestQueue() {
        // Xử lý hàng đợi định kỳ
        if (chargingRequestQueue.isEmpty()) return
        
        // Sắp xếp lại theo khoảng cách
        chargingRequestQueue.sortBy { it.distanceToStation }
        
        // Tìm trạm sạc có chỗ trống
        val availableStations = parkingLots.filter { 
            it.hasChargingStation && it.availableChargingSpots > 0 
        }
        
        if (availableStations.isEmpty()) return
        
        // Xử lý yêu cầu đầu tiên trong hàng đợi
        val nextRequest = chargingRequestQueue.firstOrNull() ?: return
        
        // Tìm trạm gần nhất với yêu cầu này
        val nearestStation = availableStations.minByOrNull { station ->
            calculateDistance(nextRequest.userLat, nextRequest.userLon, station.lat, station.lon)
        }
        
        nearestStation?.let { station ->
            if (station.availableChargingSpots > 0) {
                station.availableChargingSpots--
                station.availableSpots--
                chargingRequestQueue.removeAt(0)
                displayParkingLots()
            }
        }
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
    
    private fun suggestAlternativeChargingStations(userLat: Double, userLon: Double, currentStation: ParkingLot) {
        val alternativeStations = parkingLots
            .filter { 
                it.hasChargingStation && 
                it.availableChargingSpots > 0 && 
                it.id != currentStation.id 
            }
            .map { station ->
                val distance = calculateDistance(userLat, userLon, station.lat, station.lon)
                Pair(station, distance)
            }
            .sortedBy { it.second }
            .take(3)
        
        if (alternativeStations.isEmpty()) {
            Toast.makeText(this, 
                "❌ Tất cả trạm sạc đều đã đầy!\n" +
                "Vui lòng thử lại sau hoặc đặt chỗ đỗ thường",
                Toast.LENGTH_LONG).show()
            return
        }
        
        // Tạo danh sách gợi ý
        val suggestions = alternativeStations.mapIndexed { index, (station, distance) ->
            "${index + 1}. ${station.name}\n" +
            "   ⚡ Còn ${station.availableChargingSpots}/${station.totalChargingSpots} chỗ • " +
            "Cách ${formatDistance(distance)}"
        }.joinToString("\n\n")
        
        val message = "🔋 GỢI Ý TRẠM SẠC THAY THẾ:\n\n$suggestions"
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Trạm ${currentStation.name} đã đầy")
            .setMessage(message)
            .setPositiveButton("Xem trạm đầu tiên") { _, _ ->
                alternativeStations.firstOrNull()?.let { (station, _) ->
                    map.controller.animateTo(GeoPoint(station.lat, station.lon))
                    map.controller.setZoom(16.0)
                    showParkingDetails(station)
                }
            }
            .setNeutralButton("Xem tất cả") { _, _ ->
                showAllChargingStations()
            }
            .setNegativeButton("Đóng", null)
            .show()
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
                    "✅ Còn ${station.availableChargingSpots}/${station.totalChargingSpots} chỗ"
                } else {
                    "❌ Đã đầy (0/${station.totalChargingSpots})"
                }
                "${index + 1}. ${station.name}\n" +
                "   $status • Cách ${formatDistance(distance)}"
            }.joinToString("\n\n")
            
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⚡ TẤT CẢ TRẠM SẠC XE ĐIỆN")
                .setMessage(stationList)
                .setPositiveButton("Đóng", null)
                .show()
                
        } ?: Toast.makeText(this, "Bật GPS để xem danh sách", Toast.LENGTH_SHORT).show()
    }

    private fun showParkingNotification(parking: ParkingLot, distance: Double) {
        val message = "Bãi đỗ xe gần bạn!\n" +
                "${parking.name}\n" +
                "Còn ${parking.availableSpots} chỗ trống\n" +
                "Cách bạn ${formatDistance(distance)}"

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Tạo notification âm thanh nếu cần
        val notification = android.app.Notification.Builder(this)
            .setContentTitle("Bãi đỗ xe gần bạn")
            .setContentText("${parking.name} - Còn ${parking.availableSpots} chỗ")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
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
        myLocationOverlay?.runOnFirstFix {
            runOnUiThread {
                zoomToMyLocation(false)
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

    private fun setupCategoryChips() {
        chipRestaurant.setOnClickListener {
            findNearestChargingStation()
            Toast.makeText(this, "Đang tìm trạm sạc nhanh gần bạn...", Toast.LENGTH_SHORT).show()
        }

        chipCafe.setOnClickListener {
            showAllChargingStations()
            Toast.makeText(this, "Hiển thị tất cả trạm sạc thường...", Toast.LENGTH_SHORT).show()
        }

        chipHotel.setOnClickListener {
            val evParkingLots = parkingLots.filter { it.hasChargingStation }
            Toast.makeText(this, "Tìm thấy ${evParkingLots.size} bãi đỗ xe điện", Toast.LENGTH_SHORT).show()
        }

        chipHospital.setOnClickListener {
            Toast.makeText(this, "🌿 Tìm kiếm trạm sạc thân thiện môi trường", Toast.LENGTH_SHORT).show()
            findNearestChargingStation()
        }
    }

    private fun searchNearbyPlaces(type: String, displayName: String) {
        myLocationOverlay?.myLocation?.let { myLoc ->
            Toast.makeText(this, "Đang tìm $displayName gần bạn...", Toast.LENGTH_SHORT).show()

            Thread {
                try {
                    val encoded = URLEncoder.encode(displayName, "UTF-8")
                    val viewbox = "${myLoc.longitude-0.05},${myLoc.latitude-0.05},${myLoc.longitude+0.05},${myLoc.latitude+0.05}"
                    val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=$encoded&countrycodes=vn&limit=20&addressdetails=1&accept-language=vi&bounded=1&viewbox=$viewbox")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "CarParkingSmart/1.0")
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000

                    if (conn.responseCode == 200) {
                        val json = conn.inputStream.bufferedReader().readText()
                        val array = JSONArray(json)

                        runOnUiThread {
                            if (array.length() > 0) {
                                val obj = array.getJSONObject(0)
                                val lat = obj.getDouble("lat")
                                val lon = obj.getDouble("lon")
                                val name = obj.optString("name", obj.getString("display_name").split(",")[0])
                                val fullAddress = obj.getString("display_name")
                                showMarkerAtLocation(lat, lon, name, fullAddress, displayName)
                                Toast.makeText(this, "Tìm thấy ${array.length()} $displayName gần đây", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Không tìm thấy $displayName gần bạn", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Lỗi: ${conn.responseCode}", Toast.LENGTH_SHORT).show()
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
        } ?: run {
            Toast.makeText(this, "Vui lòng bật định vị", Toast.LENGTH_SHORT).show()
        }
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
                            tempList.add(PlaceInfo(lat, lon, shortName, displayName))
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
        
        if (matchingParkingLots.isNotEmpty()) {
            // Tìm thấy bãi đỗ xe
            val parking = matchingParkingLots[0]
            
            runOnUiThread {
                // Zoom đến bãi đỗ xe
                map.controller.animateTo(GeoPoint(parking.lat, parking.lon))
                map.controller.setZoom(17.0)
                
                // Hiển thị thông tin
                showParkingDetails(parking)
                
                Toast.makeText(this, 
                    "Tìm thấy: ${parking.name}" + 
                    if (parking.hasChargingStation) " ⚡ (Có trạm sạc)" else "",
                    Toast.LENGTH_LONG).show()
            }
            return
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
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.peekHeight = 0

        btnDirectionsBottom.setOnClickListener {
            showDirections()
        }

        btnSavePlace.setOnClickListener {
            Toast.makeText(this, "Đã lưu địa điểm", Toast.LENGTH_SHORT).show()
        }

        btnSharePlace.setOnClickListener {
            currentPlace?.let { place ->
                val shareText = "${place.name}\n${place.address}\n\nXem trên bản đồ:\nhttps://maps.google.com/?q=${place.lat},${place.lon}"
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Chia sẻ địa điểm qua"))
            }
        }

        btnNearby.setOnClickListener {
            currentPlace?.let { place ->
                Toast.makeText(this, "Đang tìm địa điểm gần ${place.name}...", Toast.LENGTH_SHORT).show()
                searchNearbyCurrentPlace(place)
            }
        }

        findViewById<ImageButton>(R.id.btn_close_sheet).setOnClickListener {
            closeBottomSheetAndClearMap()
        }
    }

    private fun closeBottomSheetAndClearMap() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        searchMarker?.let {
            map.overlays.remove(it)
            searchMarker = null
        }

        routeLine?.let {
            map.overlays.remove(it)
            routeLine = null
        }

        currentPlace = null
        map.invalidate()

        Toast.makeText(this, "Đã xóa địa điểm", Toast.LENGTH_SHORT).show()
    }

    private fun searchNearbyCurrentPlace(place: PlaceInfo) {
        Thread {
            try {
                val viewbox = "${place.lon-0.01},${place.lat-0.01},${place.lon+0.01},${place.lat+0.01}"
                val url = URL("https://nominatim.openstreetmap.org/search?format=json&q=restaurant,cafe,hotel&countrycodes=vn&limit=10&addressdetails=1&accept-language=vi&bounded=1&viewbox=$viewbox")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "CarParkingSmart/1.0")

                if (conn.responseCode == 200) {
                    val json = conn.inputStream.bufferedReader().readText()
                    val array = JSONArray(json)

                    runOnUiThread {
                        if (array.length() > 0) {
                            Toast.makeText(this, "Tìm thấy ${array.length()} địa điểm gần đây", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Không tìm thấy địa điểm gần đây", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Lỗi tìm kiếm", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showPlaceDetails(lat: Double, lon: Double, name: String, address: String, category: String = "") {
        currentPlace = PlaceInfo(lat, lon, name, address, category)

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
                        setupWardChips(parkingLots)
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