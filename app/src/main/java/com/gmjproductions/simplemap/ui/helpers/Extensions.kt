package com.gmjproductions.simplemap.ui.helpers

import org.osmdroid.util.BoundingBox

fun BoundingBox.BoundingGpsBox(): BoundingGpsBox {
    return BoundingGpsBox(
        Pair(latNorth, lonWest),
        Pair(latNorth, lonEast),
        Pair(latSouth, lonEast),
        Pair(latSouth, lonWest),
        Pair(centerLatitude,centerLongitude)
                         )
}