package com.gmjproductions.simplemap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.gmjacobs.productions.openchargemap.model.OpenChargeMapViewModel
import com.gmjacobs.productions.openchargemap.model.OpenChargeMapViewModelFactory
import com.gmjacobs.productions.openchargemap.model.poi.PoiItem
import com.gmjacobs.productions.openchargemap.network.APIResponse
import com.gmjacobs.productions.openchargemap.utils.MapMarkers
import com.gmjproductions.simplemap.ui.helpers.BoundingGpsBox
import com.gmjproductions.simplemap.ui.helpers.OnLocationChangeInMeters
import com.gmjproductions.simplemap.ui.theme.SimpleMapTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.events.MapAdapter
import org.osmdroid.events.ScrollEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.constants.GeoConstants
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import java.util.*
import kotlinx.coroutines.flow.*
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.gmjacobs.productions.openchargemap.model.GeocodeViewModel
import com.gmjacobs.productions.openchargemap.model.geocode.ForwardGeocodeResponse
import com.gmjacobs.productions.openchargemap.model.geocode.GeocodeForwardResponseItem
import java.lang.RuntimeException

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [MainMapFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
@OptIn(InternalCoroutinesApi::class)
class MainMapFragment : Fragment() {
    private val LogTag = MainMapFragment::class.java.simpleName
    private val uiViewModel by viewModels<UIViewModel>()
    private lateinit var scrollEventsJob: Job
    private lateinit var mapView: MapView
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val openChargeMapViewModel by activityViewModels<OpenChargeMapViewModel> {
        OpenChargeMapViewModelFactory(
            requireActivity().application
        )
    }
    private val geocodeViewModel by activityViewModels<GeocodeViewModel>()

    val getInfoViewSetting: Flow<Boolean> by lazy {
        requireContext().dataStoree.data.map { preferences ->
            preferences[INFOWINDOW_SHOW_ALL] ?: false
        }
    }

    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    @InternalCoroutinesApi
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireActivity())
        return ComposeView(requireContext()).apply {
            setContent {
                SimpleMapTheme { // A surface container using the 'background' color from the theme
                    Surface(color = MaterialTheme.colors.background) {
                        BuildUI()
                    }
                }
            }
        }
    }


    @SuppressLint("UnusedMaterialScaffoldPaddingParameter")
    @InternalCoroutinesApi
    @Composable
    fun BuildUI() {
        val scaffoldState = rememberScaffoldState()
        Scaffold(scaffoldState = scaffoldState, content = { MapContent() }, topBar = {
            TopBar()
        },
            bottomBar = { showSnackBarMessage(uiViewModel = uiViewModel) })
    }

    @Composable
    fun TopBar() {
        val enteredLocation = remember { mutableStateOf("") }
        TopAppBar(
            Modifier
                .background(Color.Red)
                .wrapContentSize()) {
            Row {
                LocationEntry()
                GetLocations()
                OptionMenu()
                GetGeocodeForward()
            }
        }
    }

    @Composable
    fun OptionMenu() {
        val expanded = remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Box(modifier = Modifier
            .fillMaxHeight()
            .fillMaxSize()
            .background(Color.White), contentAlignment = Alignment.Center) {
            Text(modifier = Modifier
                .background(Color.White)
                .clickable { expanded.value = true }
                .wrapContentSize(), text = "Options",
                color = Color.Black)
        }
        DropdownMenu(modifier = Modifier.background(Color.White),
            expanded = expanded.value,
            offset = DpOffset(x = 100.dp, 0.dp),
            onDismissRequest = { expanded.value = false }) {
            DropdownMenuItem(onClick = { }) {
                DropDownItem(text = "show one POI info window",
                    getInfoViewSetting.collectAsState(
                        initial = null).value) {
                    scope.launch {
                        infoWindowShowAll(it)
                        expanded.value = false
                    }
                }
            }
        }
    }

    @Composable
    fun LocationEntry() {
        val entry = remember { mutableStateOf("") }
        TextField(value = entry.value,
            placeholder = {
                Text("Enter location")
            }, onValueChange = {
                entry.value = it
                uiViewModel.updateLocationEntry(it)
            },
            colors = TextFieldDefaults.textFieldColors(Color.Black, backgroundColor = Color.White),
            modifier = Modifier
                .wrapContentSize(), singleLine = true)
    }

    @Composable
    fun GetLocations() {
        val entry by uiViewModel.userEntry.collectAsState("")
        LaunchedEffect(entry) {
            if (entry.isNotEmpty()) {
                Log.d("GetLocations:", "update with $entry")
                uiViewModel.searchEntry.value = entry
            }
        }
    }


    @Composable
    fun GetGeocodeForward(userEntry: String = uiViewModel.searchEntry.value) {

        val responseState by geocodeViewModel.forwardResponse.collectAsState(null)

        LaunchedEffect(userEntry) {
            geocodeViewModel.geocodeForward(userEntry)
        }
        responseState?.value?.onSuccess { list ->
            Log.d("GetGeocodeForward", "number of items: ${list.size}")
        }
        responseState?.value?.onFailure {
            Log.d("GetGeocodeForward", "error", it)
        }

    }


    @Composable
    fun DropDownItem(text: String, showChecked: Boolean? = getInfoViewSetting.collectAsState(
        initial = false).value, checkBoxClicked: (Boolean) -> Unit) {
        showChecked?.also {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = it, onCheckedChange = {
                    checkBoxClicked(it)
                })
                Text(text, Modifier.padding(10.dp))
            }
        }
    }

    @Composable
    fun MapContent() {
        val centerMapState = remember {
            mutableStateOf(false)
        }
        ConstraintLayout {
            val (map, progress, zoomBtns, OSMCreds, currentLocation) = createRefs()
            AndroidView({
                MapView(it).apply {
                    isTilesScaledToDpi = true
                }
            }, Modifier.constrainAs(map) {
                start.linkTo(parent.start)
                top.linkTo(parent.top)
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom)
            }) {
                mapView = it
                mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                mapView.controller.setZoom(10.0)
            }
            showStatusBar(Modifier
                .constrainAs(progress) {
                    centerTo(parent)
                }
                .size(100.dp), uiViewModel = uiViewModel)

            buildZoomButtons(Modifier.constrainAs(zoomBtns) {
                top.linkTo(parent.top, margin = 16.dp)
                start.linkTo(parent.start, margin = 16.dp)
            })

            showOpenStreetMapCreds(Modifier.constrainAs(OSMCreds) {
                start.linkTo(zoomBtns.start)
                bottom.linkTo(parent.bottom, 30.dp)
            })
            showCurrentLocationBtn(Modifier.constrainAs(currentLocation) {
                end.linkTo(parent.end, margin = 10.dp)
                bottom.linkTo(parent.bottom, margin = 100.dp)
            }) {
                centerMapState.value = true
                mapView.controller.setZoom(10.0)
            }
        } // setup snackbar
        CenterMap(centerMapState = centerMapState)
        CheckLocationPermissions(centerMapState)
    }

    @Composable
    fun buildZoomButtons(modifier: Modifier) {
        Column(modifier = modifier.size(125.dp)) {
            Button(onClick = {
                if (mapView.canZoomIn()) {
                    mapView.controller.zoomIn()
                }
            }, Modifier.fillMaxWidth()) {
                Text("zoom in")
            }
            Spacer(modifier = Modifier.size(10.dp))
            Button(onClick = {
                if (mapView.canZoomOut()) {
                    mapView.controller.zoomOut()
                }
            }, Modifier.fillMaxWidth()) {
                Text("zoom out")
            }
        }
    }

    @Composable
    fun showOpenStreetMapCreds(modifier: Modifier) {
        Text(
            "© OpenStreetMap contributors",
            modifier.wrapContentSize(),
            color = Color.DarkGray,
            fontSize = 14.sp
        )
    }

    @Composable
    fun showStatusBar(visible: Boolean, modifier: Modifier) {
        if (visible) {
            CircularProgressIndicator(modifier)
        }
    }

    @Composable
    fun showStatusBar(modifier: Modifier, uiViewModel: UIViewModel) {
        showStatusBar(uiViewModel.showProgressBar.value, modifier)
    }

    @Composable
    fun showSnackBarMessage(uiViewModel: UIViewModel) {
        val snackBarMessage: String by uiViewModel.snackbarMessage.observeAsState("")
        showSnackBarMessage(snackBarMessage)
        LaunchedEffect(snackBarMessage) {
            delay(3 * 1000)
            uiViewModel.showSnackBarMessage("")
        }
    }

    @Composable
    fun showSnackBarMessage(message: String) {
        if (message.isNotEmpty()) {
            Snackbar(Modifier.padding(5.dp)) {
                Text(text = message)
            }
        }
    }

    @Composable
    fun showCurrentLocationBtn(modifier: Modifier, onClick: () -> Unit) {
        Image(
            modifier = modifier
                .size(50.dp)
                .clickable(onClick = onClick),
            painter = painterResource(id = R.drawable.ic_current_location),
            contentDescription = null,
        )
    }

    @Composable
    fun CenterMap(
        centerMapState: MutableState<Boolean>,
        location: State<Location?> = getLastLocation().collectAsState(null)
    ) {
        if (centerMapState.value) {
            location.value?.also {
                val startPoint = GeoPoint(it.latitude, it.longitude)
                mapView.controller.setCenter(startPoint)
                centerMapState.value = false
            }
        }
    }

    @Composable
    fun CheckLocationPermissions(centerMapState: MutableState<Boolean>) {
        val initOpenChargeMap = remember { mutableStateOf(false) }
        val showLocationPermsissionsRationale = remember { mutableStateOf(false) }
        val permissions =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                when {
                    permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION,
                        false) || permissions.getOrDefault(
                        Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                        centerMapState.value = true
                        initOpenChargeMap.value = true
                    }
                    else -> {
                        when {
                            shouldShowRequestPermissionRationale(
                                Manifest.permission.ACCESS_FINE_LOCATION) || shouldShowRequestPermissionRationale(
                                Manifest.permission.ACCESS_COARSE_LOCATION) -> {
                                showLocationPermsissionsRationale.value = true
                            }
                            else -> {
                                showLocationPermsissionsRationale.value = true
                            }

                        }
                    }
                }
            }
        LaunchedEffect(true) {
            permissions.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        ShowLocationPermissionRationale(showLocationPermsissionsRationale) {
            showLocationPermsissionsRationale.value = false
            requireActivity().finish()
        }
        InitOpenChargeMap(permissionsGranted = initOpenChargeMap)
    }

    @Composable
    fun ShowLocationPermissionRationale(show: State<Boolean>, onClick: () -> Unit) {
        if (show.value) {
            AlertDialog(onDismissRequest = onClick,
                text = {
                    Text(stringResource(id = R.string.location_rationale))
                },
                buttons = {
                    Button(onClick = onClick) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                }
            )

        }
    }

    @Composable
    fun InitOpenChargeMap(
        permissionsGranted: State<Boolean>,
        dbInitState: Boolean = openChargeMapViewModel.dbIntialized.observeAsState(
            false
        ).value
    ) {
        if (permissionsGranted.value && dbInitState) {
            LaunchedEffect(true) {
                openChargeMapViewModel.getChargeTypes()
                openChargeMapViewModel.getStatusTypes()
                openChargeMapViewModel.getConnectionTypesByName("chademo", "J1772", "CCS")
                openChargeMapViewModel.getCountriesByName(
                    "united states",
                    "mexico",
                    "canada",
                    "puerto rico",
                    "israel",
                    "france"
                )
                openChargeMapViewModel.getOperatorsByName("blink", "chargepoint", "evgo network")
                openChargeMapViewModel.getUsageTypesByName("public")

            }
            WaitEVParamDataToBeLoaded()
        }
    }

    @Composable
    fun WaitEVParamDataToBeLoaded(
        paramsFetched: Boolean? = openChargeMapViewModel.paramsFetched.observeAsState().value) {
        paramsFetched?.also {
            if (it) {
                ListenForPoiUpdates()
                LaunchedEffect(true) {
                    listenForMapScrollEvents()
                    // show initial charging stations
                    relocateOpenChargeMapMarkers(mapView.boundingBox.BoundingGpsBox())
                }
            }
        }
    }

    @Composable
    fun ListenForPoiUpdates(
        optional: Optional<APIResponse>? = openChargeMapViewModel.pois.observeAsState().value) {
        val scope = rememberCoroutineScope()
        uiViewModel.showProgressBar(false)
        optional?.orElse(null)?.also { response ->
            when (response) {
                is APIResponse.Success<*> -> {
                    val list = (response as APIResponse.Success<List<PoiItem>>).data
                    Log.d(LogTag, "${list.size} returned")
                    if (list.isNotEmpty()) { // clear previous pins
                        clearClustersAndMarkers()
                        val markerClusterer = RadiusMarkerClusterer(context).apply {
                            setIcon(
                                BitmapFactory.decodeResource(
                                    resources,
                                    R.drawable.marker_cluster
                                )
                            )
                        }
                        list.forEach { poi ->
                            val nxtMarker = object : Marker(mapView) {
                                val currentMarker = this
                                override fun onSingleTapConfirmed(event: MotionEvent?,
                                    mapView: MapView?): Boolean {
                                    val isClicked = super.onSingleTapConfirmed(event, mapView)
                                    if (isClicked) {
                                        uiViewModel.showSnackBarMessage(
                                            "${poi.addressInfo?.latitude},${poi.addressInfo?.longitude} -- ${poi.operatorInfo.title}")
                                        scope.launch {
                                            getInfoViewSetting.collect { showOne ->
                                                markerClusterer.items.forEach {
                                                    if (showOne && it != currentMarker) {
                                                        if (it.isInfoWindowOpen) {
                                                            it.closeInfoWindow()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    return isClicked
                                }
                            }.apply {
                                poi.addressInfo?.also { addressInfo ->
                                    icon = BitmapDrawable(
                                        resources,
                                        MapMarkers(requireContext()).getIconForPOI(poi)
                                    )
                                    position = GeoPoint(addressInfo.latitude, addressInfo.longitude)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    infoWindow =
                                        MarkerInfoWindow(R.layout.bonuspack_bubble, mapView).apply {
                                            title = addressInfo.title
                                            subDescription = addressInfo.addressLine1

                                        }
                                }
                            }
                            markerClusterer.add(nxtMarker)
                        }
                        mapView.overlayManager.filter { it is Marker }.also {
                            Log.d(LogTag, "Markers added to OverlayManager, NO MARKERS ${it.size}")
                        }
                        markerClusterer.items.filter { it is Marker }.also {
                            Log.d(LogTag, "Markers added to MarkerClusterer, NO MARKERS ${it.size}")
                        }
                        mapView.overlayManager.add(markerClusterer)
                        mapView.invalidate()
                    }
                    uiViewModel.showSnackBarMessage("${list.size} pois found")
                }
                is APIResponse.Exception -> {
                    uiViewModel.showSnackBarMessage(response.message)
                }
            }
        }
    }


    fun relocateOpenChargeMapMarkers(box: BoundingGpsBox) {
        Log.d(
            LogTag,
            "relocateOpenChargeMapMarkers: lat:${box.center.first}, lon:${box.center.second}"
        ) // wait for all params to get fetched into model
        openChargeMapViewModel.getPOIs(
            box.center.first,
            box.center.second,
            countryIDs = openChargeMapViewModel.getCountryIDs(),
            operatorIDs = openChargeMapViewModel.getOperatorIDs(),
            connectionTypeIDs = openChargeMapViewModel.getConnectionTypeIDs(),
            usageTypeIDs = openChargeMapViewModel.getUsageTypeIDs(),
            statusTypeIDs = openChargeMapViewModel.getStatusTypeIDs(),
            maxResults = 100,
            radiusInMiles = (box.distanceMetersWidth / GeoConstants.METERS_PER_STATUTE_MILE).toInt()
        )
        uiViewModel.showProgressBar(true)
    }

    fun clearClustersAndMarkers() {
        // clear clusters
        mapView.overlayManager.filter {
            it is RadiusMarkerClusterer
        }.map {
            it as RadiusMarkerClusterer
        }.also {
            // remove all open infowindows
            it.forEach {
                it.items.forEach {
                    it.closeInfoWindow()
                }
            }
            mapView.overlayManager.removeAll(it)
        }

    }

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) {
            mapView.onPause()
        }
        if (::scrollEventsJob.isInitialized && scrollEventsJob.isActive) {
            scrollEventsJob.cancel()
        }
    }

    @InternalCoroutinesApi
    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
        }
    }


    fun listenForMapScrollEvents() {
        if (::scrollEventsJob.isInitialized && scrollEventsJob.isActive) {
            scrollEventsJob.cancel()
        }
        scrollEventsJob = lifecycleScope.launch {
            var previousLocation: GeoPoint? = null
            mapScrollFlow().debounce(1 * 750).collect { box ->
                val nxtCenterLocation = GeoPoint(box.center.first, box.center.second)
                nxtCenterLocation.OnLocationChangeInMeters(
                    box.distanceMetersWidth.toInt() / 2,
                    previousLocation
                ) { thresholdMet, distance ->
                    if (thresholdMet) {
                        relocateOpenChargeMapMarkers(box)
                    }
                }
                previousLocation = nxtCenterLocation
            }
        }
    }

    private var myMapListener: MapAdapter? = null

    @ExperimentalCoroutinesApi
    fun mapScrollFlow() = callbackFlow<BoundingGpsBox> {
        myMapListener = object : MapAdapter() {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapView.boundingBox.apply {
                    trySendBlocking(this.BoundingGpsBox())
                }
                return super.onScroll(event)
            }
        }
        try {
            mapView.addMapListener(myMapListener)

        } catch (ex: Exception) {

        }
        awaitClose {
            mapView.removeMapListener(myMapListener)
        }
    } //    @Preview //    @Composable //    fun showPreview() { //    }


    @SuppressLint("MissingPermission")
    fun getLastLocation() = callbackFlow<Location> {
        fusedLocationProviderClient.lastLocation.addOnSuccessListener {
            trySendBlocking(it)
        }
        awaitClose {
            cancel()
        }
    }

    //    val getInfoViewSetting: Flow<Boolean> = Context.dataStoree.data.map { preferences ->
//        (preferences[INFOWINDOW_SHOW_ALL] ?: false)
//    }
    suspend fun infoWindowShowAll(show: Boolean) {
        requireContext().dataStoree.edit { preferences ->
            preferences[INFOWINDOW_SHOW_ALL] = show
        }
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MainMapFragment.
         */ // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) = MainMapFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PARAM1, param1)
                putString(ARG_PARAM2, param2)
            }
        }

        val Context.dataStoree: DataStore<Preferences> by preferencesDataStore("mapUserPrefs")
        val INFOWINDOW_SHOW_ALL = booleanPreferencesKey("infowindow_showall")
    }


}