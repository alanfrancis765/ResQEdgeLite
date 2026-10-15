package com.resqedge.lite.ui.responder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.resqedge.lite.model.BaseMapData
import com.resqedge.lite.model.parseGeoJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface BaseMapState {
    object Loading : BaseMapState
    data class Loaded(val data: BaseMapData) : BaseMapState
    object NoData : BaseMapState
}

@Composable
fun rememberBaseMapState(): State<BaseMapState> {
    val context = LocalContext.current
    return produceState<BaseMapState>(initialValue = BaseMapState.Loading) {
        value = withContext(Dispatchers.IO) {
            try {
                val assetManager = context.assets
                val files = assetManager.list("lite_map")
                    ?.filter { it.endsWith(".geojson", ignoreCase = true) }
                    ?: emptyList()

                if (files.isEmpty()) {
                    BaseMapState.NoData
                } else {
                    var merged = BaseMapData()
                    for (file in files) {
                        try {
                            val text = assetManager.open("lite_map/$file").bufferedReader().use { it.readText() }
                            val parsed = parseGeoJson(text)
                            merged = merged.merge(parsed)
                        } catch (_: Exception) {
                            // continue reading remaining files
                        }
                    }
                    if (merged.isEmpty) {
                        BaseMapState.NoData
                    } else {
                        BaseMapState.Loaded(merged)
                    }
                }
            } catch (_: Exception) {
                BaseMapState.NoData
            }
        }
    }
}
