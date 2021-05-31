package com.gmjproductions.simplemap

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.gmjacobs.productions.openchargemap.model.OpenChargeMapViewModel
import com.gmjacobs.productions.openchargemap.model.OpenChargeMapViewModelFactory
import com.gmjacobs.productions.openchargemap.model.poi.PoiItem
import com.gmjacobs.productions.openchargemap.utils.MapMarkers
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
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
                             ): View? {
        openChargeMapViewModel = ViewModelProvider(
            this,
            OpenChargeMapViewModelFactory(application = requireActivity().application)
                                                  ).get(OpenChargeMapViewModel::class.java)


        return inflater.inflate(R.layout.map_layout, container, false).apply {
            findViewById<MapView>(R.id.map)?.also {
                mapView = it
                mapView.setTileSource(TileSourceFactory.MAPNIK);
                val mapController = mapView.controller
                mapController.setZoom(10.0)
                val startPoint = GeoPoint(39.9151, -73.9857)
                mapController.setCenter(startPoint)
                initOpenChargeMap()
            }
            findViewById<ComposeView>(R.id.compose_view)?.apply {
                setContent {
                    SimpleMapTheme {
                        // A surface container using the 'background' color from the theme
                        Surface(color = MaterialTheme.colors.background) {
                            helloWorld("Hello Y'all")
                        }
                    }
                }
            }
        }

    }

    fun initOpenChargeMap() {
        openChargeMapViewModel.dbIntialized.removeObserver(openChargeMapDBInitializedListener)
        openChargeMapViewModel.dbIntialized.observe(
            this.viewLifecycleOwner,
            openChargeMapDBInitializedListener
                                                   )
    }

    val openChargeMapDBInitializedListener = Observer<Boolean> {
        if (it) {
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


    fun relocateOpenChargeMapPins(newLocation: GeoPoint, clearPins: Boolean) {
        Log.d(
            LogTag,
            "relocateOpenChargeMapPins: lat:${newLocation.latitude}, lon:${newLocation.longitude}"
             )

        if (clearPins) {
            // clear OCM pins here
        }
        // wait for all params to get fetched into model
        openChargeMapViewModel.getPOIs(
            newLocation.latitude,
            newLocation.longitude,
            countryIDs = openChargeMapViewModel.getCountryIDs(),
            operatorIDs = openChargeMapViewModel.getOperatorIDs(),
            connectionTypeIDs = openChargeMapViewModel.getConnectionTypeIDs(),
            usageTypeIDs = openChargeMapViewModel.getUsageTypeIDs(),
            statusTypeIDs = openChargeMapViewModel.getStatusTypeIDs(),
            maxResults = 100,
            radiusInMiles = 30
                                      )
    }


    val openChargeMapPOIObserver = Observer<Optional<List<PoiItem>>> {
        it.ifPresent { list ->
            Log.d(LogTag, "${list.size} returned")
            if (list.isNotEmpty()) {
                mapView.overlayManager.removeAll {
                    it is Marker
                }
                list.forEach { poi ->
                    val nxtMarker = Marker(mapView).apply {
                        poi.addressInfo?.also { addressInfo ->
                            icon = BitmapDrawable(
                                resources,
                                MapMarkers(requireContext()).getIconForPOI(poi)
                                                 )
                            position = GeoPoint(addressInfo.latitude, addressInfo.longitude)
                            setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER)
                        }

                    }
                    mapView.overlayManager.add(nxtMarker)
                }
            }
        }
    }

    @Composable
    fun helloWorld(text: String) {
        Text(text = text)
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

    /*
        fun showOpenChargeMapMarkers(box: BoundingBox) {
            // remove all polygons first
            mapView.overlayManager.removeAll {
                it is Polygon || it is Polyline
            }
            val polyLine = Polyline().apply {
                setPoints(
                    listOf(
                        GeoPoint(box.latNorth, box.lonWest),
                        GeoPoint(box.latNorth, box.lonEast),
                        GeoPoint(box.latSouth, box.lonEast)
                          )
                         )
                outlinePaint.strokeWidth = 500f
                outlinePaint.color = Color.Black.toArgb()
            }
            val polygon = Polygon().apply {
                val points = listOf(
                    GeoPoint(box.latNorth, box.lonWest),
                    GeoPoint(box.latNorth, box.lonEast),
                    GeoPoint(box.latSouth, box.lonEast),
                    GeoPoint(box.latSouth, box.lonWest)
                                   )
                addPoint(points[0])
                fillPaint.color = Color.Green.toArgb()
                fillPaint.alpha = 50
                fillPaint.style = android.graphics.Paint.Style.FILL_AND_STROKE
                setPoints(points)
                title = "A sample polygon"
            }


            mapView.overlayManager.add(polygon)
            //scope.sendBlocking(GeoPoint(box.centerLatitude,box.centerLongitude))
            //relocateOpenChargeMapPins(GeoPoint(box.centerLatitude, box.centerLongitude), true)
        }
    */
    fun listenForMapScrollEvents() {
        if (::scrollEventsJob.isInitialized && scrollEventsJob.isActive) {
            scrollEventsJob.cancel()
        }
        scrollEventsJob = lifecycleScope.launch {
            mapScrollFlow()
                .debounce(2 * 1000)
                .collect {
                    relocateOpenChargeMapPins(GeoPoint(it.centerLatitude, it.centerLongitude), true)
                }
        }
    }

    private var myMapListener: MapAdapter? = null

    @ExperimentalCoroutinesApi
    fun mapScrollFlow() = callbackFlow<BoundingBox> {
        myMapListener = object : MapAdapter() {
            override fun onScroll(event: ScrollEvent?): Boolean {
                mapView.boundingBox.apply {
                    sendBlocking(this)
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
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic fun newInstance(param1: String, param2: String) =
            MainMapFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}