package com.gmjproductions.simplemap

import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.tooling.preview.Preview
import com.gmjproductions.simplemap.ui.theme.SimpleMapTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class MainActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1;

    lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_layout)
        osmSetup()
        findViewById<MapView>(R.id.map)?.also {
            mapView = it
            mapView.setTileSource(TileSourceFactory.MAPNIK);
            val mapController = mapView.controller
            mapController.setZoom(3.0)
            val startPoint = GeoPoint(39.9151, -73.9857);
            mapController.setCenter(startPoint);
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

    private fun osmSetup() {
        Configuration.getInstance()?.apply {
            userAgentValue = BuildConfig.APPLICATION_ID
            load(
                applicationContext,
                PreferenceManager.getDefaultSharedPreferences(applicationContext)
                )
        }
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

    @Composable
    fun helloWorld(text: String) {
        Text(text = text)
    }

    @Composable
    fun showMap(mapView: MapView) {

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
                                           ) {

        val permissionsToRequest = ArrayList<String>();
        var i = 0;
        while (i < grantResults.size) {
            permissionsToRequest.add(permissions[i]);
            i++;
        }
        if (permissionsToRequest.size > 0) {
            MainActivity@ this.requestPermissions(
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE
                                                 );
        }
        super.onRequestPermissionsResult(
            requestCode,
            permissionsToRequest.toTypedArray(),
            grantResults
                                        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SimpleMapTheme {
        //Greeting("Android")
    }
}