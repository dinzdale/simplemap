package com.gmjproductions.simplemap

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.gmjproductions.simplemap.ui.theme.SimpleMapTheme
import org.osmdroid.events.MapAdapter
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

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
    private lateinit var mapView: MapView

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
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.map_layout, container, false).apply {
            findViewById<MapView>(R.id.map)?.also {
                mapView = it
                mapView.setTileSource(TileSourceFactory.MAPNIK);
                val mapController = mapView.controller
                mapController.setZoom(3.0)
                val startPoint = GeoPoint(39.9151, -73.9857);
                mapController.setCenter(startPoint);
                mapView.addMapListener(myMapListener)
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

    @Composable
    fun helloWorld(text: String) {
        Text(text = text)
    }

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) {
            mapView.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
        }
    }

    private val myMapListener = object : MapAdapter() {
        override fun onScroll(event: ScrollEvent?): Boolean {
            mapView.boundingBox.apply {
                showOpenChargeMapMarkers(this)
            }
            return super.onScroll(event)
        }

        override fun onZoom(event: ZoomEvent?): Boolean {
            mapView.boundingBox.apply {

            }
            return super.onZoom(event)
        }
    }

    fun showOpenChargeMapMarkers(box: BoundingBox) {
        // remove all polygons first
        mapView.overlayManager.removeAll {
            it is Polygon || it is Polyline
        }
        val polyLine = Polyline().apply{
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
                GeoPoint(box.latNorth,box.lonWest),
                GeoPoint(box.latNorth,box.lonEast),
                GeoPoint(box.latSouth,box.lonEast),
                GeoPoint(box.latSouth,box.lonWest)
                               )
            addPoint(points[0])
            fillPaint.color = Color.Green.toArgb()
            fillPaint.alpha = 50
            fillPaint.style = android.graphics.Paint.Style.FILL_AND_STROKE
            setPoints(points)
            title = "A sample polygon"
        }


        mapView.overlayManager.add(polygon)
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