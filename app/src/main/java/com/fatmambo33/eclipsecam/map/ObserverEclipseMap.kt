package com.fatmambo33.eclipsecam.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/**
 * Provider-independent Position map.
 *
 * Eclipse geometry is always local. A reviewed [MapTileSource] may optionally
 * supply a basemap style; when it is absent or fails, the local path remains visible.
 */
@Composable
fun ObserverEclipseMap(
    scene: EclipseMapScene,
    modifier: Modifier = Modifier,
    tileSource: MapTileSource? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycleOwner, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                Lifecycle.Event.ON_DESTROY -> Unit
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }

    Box(modifier = modifier.testTag("eclipse-map")) {
        AndroidView(
            factory = { view },
            update = { current -> renderScene(current, scene, tileSource) },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = tileSource?.attribution ?: "Offline eclipse geometry • no basemap",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color(0xCC070A12))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("map-attribution"),
        )
    }
}

private fun renderScene(
    mapView: MapView,
    scene: EclipseMapScene,
    tileSource: MapTileSource?,
) {
    mapView.getMapAsync { map ->
        val builder = if (tileSource == null) {
            Style.Builder().fromJson(LOCAL_FALLBACK_STYLE)
        } else {
            Style.Builder().fromUri(tileSource.styleUrl)
        }
        map.setStyle(builder) { style -> addLocalGeometry(style, scene) }
    }
}

private fun addLocalGeometry(style: Style, scene: EclipseMapScene) {
    style.addSource(GeoJsonSource(CENTRELINE_SOURCE, lineFeature(scene.overlay.centreline)))
    style.addSource(GeoJsonSource(NORTH_SOURCE, lineFeature(scene.overlay.northernLimit)))
    style.addSource(GeoJsonSource(SOUTH_SOURCE, lineFeature(scene.overlay.southernLimit)))

    style.addLayer(
        LineLayer(NORTH_LAYER, NORTH_SOURCE).withProperties(
            lineColor("#CBD5E1"),
            lineWidth(2f),
            lineOpacity(0.9f),
        ),
    )
    style.addLayer(
        LineLayer(SOUTH_LAYER, SOUTH_SOURCE).withProperties(
            lineColor("#CBD5E1"),
            lineWidth(2f),
            lineOpacity(0.9f),
        ),
    )
    style.addLayer(
        LineLayer(CENTRELINE_LAYER, CENTRELINE_SOURCE).withProperties(
            lineColor("#FFC857"),
            lineWidth(6f),
            lineOpacity(1f),
        ),
    )

    val observer = scene.observer
    if (observer != null) {
        if (scene.accuracyRing.size >= 4) {
            style.addSource(GeoJsonSource(ACCURACY_SOURCE, polygonFeature(scene.accuracyRing)))
            style.addLayer(
                FillLayer(ACCURACY_LAYER, ACCURACY_SOURCE).withProperties(
                    fillColor("#60A5FA"),
                    fillOpacity(0.18f),
                ),
            )
        }
        style.addSource(GeoJsonSource(OBSERVER_SOURCE, Feature.fromGeometry(observer.toPoint())))
        style.addLayer(
            CircleLayer(OBSERVER_LAYER, OBSERVER_SOURCE).withProperties(
                circleColor("#60A5FA"),
                circleRadius(7f),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(2f),
            ),
        )
    }
}

private fun lineFeature(points: List<GeoPoint>): FeatureCollection = FeatureCollection.fromFeature(
    Feature.fromGeometry(LineString.fromLngLats(points.map(GeoPoint::toPoint))),
)

private fun polygonFeature(points: List<GeoPoint>): FeatureCollection = FeatureCollection.fromFeature(
    Feature.fromGeometry(Polygon.fromLngLats(listOf(points.map(GeoPoint::toPoint)))),
)

private fun GeoPoint.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private const val LOCAL_FALLBACK_STYLE = """
{
  "version": 8,
  "name": "EclipseCam local fallback",
  "sources": {},
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": { "background-color": "#070A12" }
    }
  ]
}
"""

private const val CENTRELINE_SOURCE = "eclipse-centreline-source"
private const val NORTH_SOURCE = "eclipse-north-source"
private const val SOUTH_SOURCE = "eclipse-south-source"
private const val OBSERVER_SOURCE = "observer-source"
private const val ACCURACY_SOURCE = "observer-accuracy-source"
private const val CENTRELINE_LAYER = "eclipse-centreline"
private const val NORTH_LAYER = "eclipse-north-limit"
private const val SOUTH_LAYER = "eclipse-south-limit"
private const val OBSERVER_LAYER = "observer"
private const val ACCURACY_LAYER = "observer-accuracy"
