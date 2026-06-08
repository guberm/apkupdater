package com.apkupdater.ui.component

import android.content.res.Configuration
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apkupdater.R
import com.apkupdater.prefs.Prefs
import org.koin.compose.koinInject

@Composable
fun LoadingGrid() {
    if (koinInject<Prefs>().androidTvUi.get()) {
        TvShimmeringGrid()
    } else {
        ShimmeringGrid()
    }
}

@Composable
fun ShimmeringGrid() = Box(Modifier.fillMaxSize()) {
    InstalledGrid(false) {
        items(16) { index ->
            LoadingTile(170.dp, index)
        }
    }
    RefreshLoadingBadge(Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
}

@Composable
fun TvShimmeringGrid() = Box(Modifier.fillMaxSize()) {
    TvInstalledGrid(false) {
        items(16) { index ->
            LoadingTile(155.dp, index)
        }
    }
    RefreshLoadingBadge(Modifier.align(Alignment.TopCenter).padding(top = 20.dp))
}

@Composable
fun LoadingTile(height: Dp, index: Int) {
    val transition = rememberInfiniteTransition("loading-tile-$index")
    val pulse by transition.animateFloat(
        label = "loading-tile-pulse-$index",
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 900,
                delayMillis = (index % 6) * 80,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        Modifier
            .height(height)
            .graphicsLayer {
                scaleX = 0.98f + pulse * 0.02f
                scaleY = 0.98f + pulse * 0.02f
            }
            .alpha(0.65f + pulse * 0.35f)
            .shimmering(true)
    )
}

@Composable
fun RefreshLoadingBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition("refresh-loading")
    val rotation by transition.animateFloat(
        label = "refresh-loading-rotation",
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing)
        )
    )
    val pulse by transition.animateFloat(
        label = "refresh-loading-pulse",
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier
            .size(52.dp)
            .graphicsLayer {
                scaleX = 0.95f + pulse * 0.05f
                scaleY = 0.95f + pulse * 0.05f
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f + pulse * 0.18f))
        )
        Icon(
            painter = painterResource(R.drawable.ic_refresh),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

@Composable
fun EmptyGrid(
    text: String = ""
) = Box(Modifier.fillMaxSize()) {
    if (text.isNotEmpty()) {
        MediumTitle(text, Modifier.align(Alignment.Center))
    }
    LazyColumn(Modifier.fillMaxSize()) {}
}

@Composable
fun InstalledGrid(
    scroll: Boolean = true,
    content: LazyGridScope.() -> Unit
) = LazyVerticalGrid(
    columns =  GridCells.Fixed(getNumColumns(LocalConfiguration.current.orientation)),
    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
    userScrollEnabled = scroll,
    modifier = Modifier.fillMaxSize()
)

@Composable
fun TvInstalledGrid(scroll: Boolean = true, content: LazyGridScope.() -> Unit) = LazyVerticalGrid(
    columns = GridCells.Fixed(getTvNumColumns()),
    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
    userScrollEnabled = scroll,
    modifier = Modifier.fillMaxSize()
)

@Composable
fun getNumColumns(orientation: Int): Int {
    val prefs = koinInject<Prefs>()
    return if(orientation == Configuration.ORIENTATION_PORTRAIT)
        prefs.portraitColumns.get()
    else
        prefs.landscapeColumns.get()
}

@Composable
fun getTvNumColumns(): Int {
    return if(LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT)
        1
    else
        2
}
