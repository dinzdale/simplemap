package com.gmjproductions.simplemap.ui.helpers

import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.GeometryMath
import org.osmdroid.views.MapView

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