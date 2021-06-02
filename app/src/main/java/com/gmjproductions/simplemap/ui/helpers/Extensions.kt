package com.gmjproductions.simplemap.ui.helpers

import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

fun BoundingBox.BoundingGpsBox(): BoundingGpsBox {
    return BoundingGpsBox(
        Pair(latNorth, lonWest),
        Pair(latNorth, lonEast),
        Pair(latSouth, lonEast),
        Pair(latSouth, lonWest),
        Pair(centerLatitude,centerLongitude),
        GeoPoint(latNorth, lonWest).distanceToAsDouble(GeoPoint(latNorth, lonEast))
                         )
}

fun GeoPoint.OnLocationChangeInMeters(threshHold: Int,
                             locationTo: GeoPoint? = null,
                             thresholdMetListener: (Boolean, Double) -> Unit) {
    locationTo?.also {
        val deltaInMeters = this.distanceToAsDouble(it)
        val thresholdMet = deltaInMeters >= threshHold
        thresholdMetListener(thresholdMet, deltaInMeters)
    } ?: thresholdMetListener(false, -1.0)
}