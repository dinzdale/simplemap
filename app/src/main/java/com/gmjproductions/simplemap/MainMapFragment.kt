package com.gmjproductions.simplemap

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.gmjacobs.productions.openchargemap.model.OpenChargeMapViewModel
import com.gmjacobs.productions.openchargemap.model.OpenChargeMapViewModelFactory
import com.gmjacobs.productions.openchargemap.model.poi.PoiItem
import com.gmjacobs.productions.openchargemap.utils.MapMarkers
import com.gmjproductions.simplemap.ui.helpers.BoundingGpsBox
import com.gmjproductions.simplemap.ui.helpers.OnLocationChangeInMeters
import com.gmjproductions.simplemap.ui.theme.SimpleMapTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.osmdroid.events.MapAdapter
import org.osmdroid.events.ScrollEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.constants.GeoConstants
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import java.util.*

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [MainMapFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MainMapFragment : Fragment() {
    private val LogTag = MainMapFragment::class.java.simpleName
    private lateinit var scrollEventsJob: Job
    private lateinit var mapView: MapView
    private lateinit var openChargeMapViewModel: OpenChargeMapViewModel

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

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        openChargeMapViewModel = ViewModelProvider(this,
            OpenChargeMapViewModelFactory(application = requireActivity().application)).get(
            OpenChargeMapViewModel::class.java)

        return ComposeView(requireContext()).apply {
            setContent {
                SimpleMapTheme { // A surface container using the 'background' color from the theme
                    Surface(color = MaterialTheme.colors.background) {
                        buildUI()
                    }
                }
            }
        }
    }


    @Composable fun buildUI() {
        ConstraintLayout {
            val (map, zoomBtns, OSMCreds) = createRefs()
            AndroidView({
                MapView(it)
            }, Modifier.constrainAs(map) {
                start.linkTo(parent.start)
                top.linkTo(parent.top)
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom)
            }) {
                mapView = it
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                val mapController = mapView.controller
                mapController.setZoom(10.0)
                val startPoint = GeoPoint(39.9151, -73.9857)
                mapController.setCenter(startPoint)
                initOpenChargeMap()
            }
            buildZoomButtons(Modifier.constrainAs(zoomBtns) {
                top.linkTo(parent.top, margin = 16.dp)
                start.linkTo(parent.start, margin = 16.dp)
            })

            showOpenStreetMapCreds(Modifier.constrainAs(OSMCreds) {
                start.linkTo(zoomBtns.start)
                bottom.linkTo(parent.bottom, 30.dp)
            })
        }
    }

    @Composable fun buildZoomButtons(modifier: Modifier) {
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

    @Composable fun showOpenStreetMapCreds(modifier: Modifier) {
        Text("© OpenStreetMap contributors", modifier.wrapContentSize(), color = Color.Companion.DarkGray, fontSize = 14.sp)
    }

    fun initOpenChargeMap() {
        openChargeMapViewModel.dbIntialized.removeObserver(openChargeMapDBInitializedListener)
        openChargeMapViewModel.dbIntialized.observe(this.viewLifecycleOwner,
            openChargeMapDBInitializedListener)
    }

    val openChargeMapDBInitializedListener = Observer<Boolean> {
        if (it) {
            openChargeMapViewModel.getChargeTypes()
            openChargeMapViewModel.getStatusTypes()

            openChargeMapViewModel.getConnectionTypesByName("chademo", "J1772", "CCS")
            openChargeMapViewModel.getCountriesByName("united states",
                "mexico",
                "canada",
                "puerto rico",
                "israel",
                "france")
            openChargeMapViewModel.getOperatorsByName("blink", "chargepoint")
            openChargeMapViewModel.getUsageTypesByName("public")
            openChargeMapViewModel.paramsFetched.observe(this) {
                if (it) {
                    openChargeMapViewModel.pois.observe(this, openChargeMapPOIObserver)
                    listenForMapScrollEvents()
                }
            }
        }
    }


    fun relocateOpenChargeMapPins(box: BoundingGpsBox) {
        Log.d(LogTag,
            "relocateOpenChargeMapPins: lat:${box.center.first}, lon:${box.center.second}") // wait for all params to get fetched into model
        openChargeMapViewModel.getPOIs(box.center.first,
            box.center.second,
            countryIDs = openChargeMapViewModel.getCountryIDs(),
            operatorIDs = openChargeMapViewModel.getOperatorIDs(),
            connectionTypeIDs = openChargeMapViewModel.getConnectionTypeIDs(),
            usageTypeIDs = openChargeMapViewModel.getUsageTypeIDs(),
            statusTypeIDs = openChargeMapViewModel.getStatusTypeIDs(),
            maxResults = 100,
            radiusInMiles = (box.distanceMetersWidth / GeoConstants.METERS_PER_STATUTE_MILE).toInt())
    }


    val openChargeMapPOIObserver = Observer<Optional<List<PoiItem>>> {
        it.ifPresent { list ->
            Log.d(LogTag, "${list.size} returned")
            if (list.isNotEmpty()) { // clear previous pins
                val markerList = mapView.overlayManager.filter {
                    it is Marker
                }.map { it as Marker }
                if (markerList.isNotEmpty()) {
                    markerList.forEach {
                        it.closeInfoWindow()
                    }
                    mapView.overlayManager.removeAll(markerList)
                }

                list.forEach { poi ->
                    val nxtMarker = Marker(mapView).apply {
                        poi.addressInfo?.also { addressInfo ->
                            icon = BitmapDrawable(resources,
                                MapMarkers(requireContext()).getIconForPOI(poi))
                            position = GeoPoint(addressInfo.latitude, addressInfo.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            infoWindow =
                                MarkerInfoWindow(R.layout.bonuspack_bubble, mapView).apply {
                                    title = addressInfo.title
                                    subDescription = addressInfo.addressLine1

                                }
                        }

                    }
                    mapView.overlayManager.add(nxtMarker)
                }
                mapView.invalidate()
            }
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
            mapScrollFlow().debounce(1 * 1000).collect { box ->
                val nxtCenterLocation = GeoPoint(box.center.first, box.center.second)
                nxtCenterLocation.OnLocationChangeInMeters(box.distanceMetersWidth.toInt() / 2,
                    previousLocation) { thresholdMet, distance ->
                    if (thresholdMet) {
                        relocateOpenChargeMapPins(box)
                    }
                }
                previousLocation = nxtCenterLocation
            }
        }
    }

    private var myMapListener: MapAdapter? = null

    @ExperimentalCoroutinesApi fun mapScrollFlow() = callbackFlow<BoundingGpsBox> {
        myMapListener = object : MapAdapter() {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapView.boundingBox.apply {
                    sendBlocking(this.BoundingGpsBox())
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
        @JvmStatic fun newInstance(param1: String, param2: String) = MainMapFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_PARAM1, param1)
                putString(ARG_PARAM2, param2)
            }
        }
    }
}