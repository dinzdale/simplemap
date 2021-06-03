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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        osmSetup()
        setContentView(R.layout.activity_main_layout)
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