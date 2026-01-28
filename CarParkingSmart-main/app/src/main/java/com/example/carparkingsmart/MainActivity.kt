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

class MainActivity : AppCompatActivity() {

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

    // Danh sách bãi đỗ xe và marker
    private val parkingLots = mutableListOf<ParkingLot>()
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
        var isNearest: Boolean = false
    )
    private var currentNearestParking: ParkingLot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = "CarParkingSmart/1.0 (Android)"
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))

        setContentView(R.layout.activity_main)


        initViews()
        setupMap()
        setupMyLocation()
        setupSearchWithAutocomplete()
        setupButtons()
        setupBottomSheet()
        setupCategoryChips()

        // Khởi tạo bãi đỗ xe
        initializeParkingLots()
        displayParkingLots()
        startParkingUpdates()
        startProximityCheck()
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

        chipRestaurant = findViewById(R.id.chip_restaurant)
        chipCafe = findViewById(R.id.chip_cafe)
        chipHotel = findViewById(R.id.chip_hotel)
        chipHospital = findViewById(R.id.chip_hospital)
        btnBookParking = findViewById(R.id.btn_book_parking)
        btnSaveMySpot = findViewById(R.id.btn_save_my_spot)
        btnFindMySpot = findViewById(R.id.btn_find_my_spot)

        btnShowParkingList = findViewById(R.id.btn_show_parking_list)
    }

    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.setBuiltInZoomControls(false)
        map.controller.setZoom(15.0)
        // Trung tâm khu vực Bắc Từ Liêm
        map.controller.setCenter(GeoPoint(21.0717, 105.7672))
    }

    private fun initializeParkingLots() {
        // Bãi đỗ xe tại khu vực Bắc Từ Liêm, Hà Nội
        parkingLots.add(ParkingLot(1, "Bãi đỗ xe Big C Thăng Long", 21.007235, 105.793757, 150, 45, "222 Trần Duy Hưng, Trung Hoà, Cầu Giấy"))
        parkingLots.add(ParkingLot(2, "Bãi đỗ xe Aeon Mall Hà Đông", 20.989667, 105.751850, 200, 78, "Số 27 Cổ Linh, Long Biên"))
        parkingLots.add(ParkingLot(3, "Bãi đỗ xe Mỹ Đình Plaza", 21.030715, 105.775943, 120, 23, "Mỹ Đình 2, Nam Từ Liêm"))
        parkingLots.add(ParkingLot(4, "Bãi đỗ xe Vincom Trần Duy Hưng", 21.007126,  105.795478, 180, 92, "119 Trần Duy Hưng, Trung Hoà, Cầu Giấy"))
        parkingLots.add(ParkingLot(5, "Bãi đỗ xe Keangnam Landmark", 21.018449, 105.783905, 250, 134, "Phạm Hùng, Nam Từ Liêm"))
        parkingLots.add(ParkingLot(6, "Bãi đỗ xe Lotte Center", 21.031870, 105.811739, 300, 67, "54 Liễu Giai, Ba Đình"))
        parkingLots.add(ParkingLot(7, "Bãi đỗ xe Mipec Long Biên", 21.045422, 105.865876, 100, 8, "229 Tây Sơn, Đống Đa"))
        parkingLots.add(ParkingLot(8, "Bãi đỗ xe Royal City", 21.002899, 105.815364, 280, 156, "72A Nguyễn Trãi, Thanh Xuân"))
        parkingLots.add(ParkingLot(
            id = 9,  // ID mới, tăng dần từ cái cuối cùng (nếu trước có 8 thì dùng 9)
            name = "Bãi đỗ xe Khu A - ĐH Mỏ Địa chất",
            lat = 21.071494,
            lon = 105.773825,
            totalSpots = 120,
            availableSpots = 50,
            address = "Khu A, Trường ĐH Mỏ - Địa chất, Phố Viên, Bắc Từ Liêm, Hà Nội"
        ))
    }

    private fun displayParkingLots() {
        parkingMarkers.forEach { map.overlays.remove(it) }
        parkingMarkers.clear()

        parkingLots.forEach { parking ->
            val marker = Marker(map).apply {
                position = GeoPoint(parking.lat, parking.lon)
                title = parking.name

                val availability = when {
                    parking.availableSpots == 0 -> "Hết chỗ"
                    parking.availableSpots < 10 -> "Còn ${parking.availableSpots} chỗ"
                    else -> "Còn ${parking.availableSpots} chỗ"
                }

                val distanceText = myLocationOverlay?.myLocation?.let { loc ->
                    "• " + formatDistance(calculateDistance(loc.latitude, loc.longitude, parking.lat, parking.lon))
                } ?: ""

                snippet = "$availability $distanceText\n${parking.address}"

                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                icon = when {
                    parking.name.contains("ĐH Mỏ Địa chất", ignoreCase = true) -> {
                        resources.getDrawable(android.R.drawable.btn_star_big_on, null)  // Ngôi sao vàng lớn luôn!
                    }
                    parking.isNearest -> {
                        resources.getDrawable(android.R.drawable.btn_star_big_on, null)  // Ngôi sao cho bãi gần nhất
                    }
                    parking.availableSpots == 0 -> resources.getDrawable(android.R.drawable.ic_dialog_alert, null)
                    parking.availableSpots < 10 -> resources.getDrawable(android.R.drawable.ic_dialog_info, null)
                    else -> resources.getDrawable(android.R.drawable.ic_menu_mylocation, null)
                }

                setOnMarkerClickListener { _, _ ->
                    showParkingDetails(parking)
                    true
                }
            }
            parkingMarkers.add(marker)
            map.overlays.add(marker)
        }
        map.invalidate()
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
            "Bãi đỗ xe"
        )

        // Thêm chữ "GẦN NHẤT" nếu đúng bãi đó
        tvPlaceName.text = parking.name + if (parking.isNearest) " GẦN NHẤT" else ""
        tvPlaceAddress.text = parking.address

        val availability = when {
            parking.availableSpots == 0 -> "Hết chỗ trống"
            parking.availableSpots < 10 -> "Sắp đầy (${parking.availableSpots}/${parking.totalSpots})"
            else -> "Còn ${parking.availableSpots}/${parking.totalSpots} chỗ"
        }

        tvPlaceRating.text = availability
        tvPlaceCategory.text = "Bãi đỗ xe • ${parking.totalSpots} chỗ tổng"

        myLocationOverlay?.myLocation?.let { myLoc ->
            val distance = calculateDistance(myLoc.latitude, myLoc.longitude, parking.lat, parking.lon)
            tvPlaceDistance.text = "${formatDistance(distance)} từ vị trí của bạn"
        } ?: run {
            tvPlaceDistance.text = "Bật GPS để xem khoảng cách"
        }

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.peekHeight = 420

        btnBookParking.visibility = View.VISIBLE
        btnBookParking.setOnClickListener {
            bookParkingSpot(parking)
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
                }
                displayParkingLots()
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
        val message = "BÃI ĐỖ XE GẦN NHẤT: ${parking.name}\n" +
                "Còn ${parking.availableSpots} chỗ • Cách ${formatDistance(distance)}\n" +
                "Đặt trước ngay để giữ chỗ nhé!"

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
            searchNearbyPlaces("restaurant", "Nhà hàng")
        }

        chipCafe.setOnClickListener {
            searchNearbyPlaces("cafe", "Quán cà phê")
        }

        chipHotel.setOnClickListener {
            searchNearbyPlaces("hotel", "Khách sạn")
        }

        chipHospital.setOnClickListener {
            searchNearbyPlaces("hospital", "Bệnh viện")
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