package com.gmjproductions.simplemap.ui.helpers

data class BoundingGpsBox(
    val topleft: Pair<Double, Double>,
    val topRight: Pair<Double, Double>,
    val bottomRight: Pair<Double, Double>,
    val bottomLeft: Pair<Double, Double>,
    val center: Pair<Double, Double>
                         )