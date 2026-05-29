package com.citizeneye.ui

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.citizeneye.R

@Composable
fun CitizenEyeLoader(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    label: String? = "Chargement…",
    fullScreen: Boolean = false
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.eye))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (animationsEnabled) LottieConstants.IterateForever else 1,
        isPlaying = animationsEnabled
    )
    val content = @Composable {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.semantics {
                contentDescription = label ?: "Chargement"
                liveRegion = LiveRegionMode.Polite
            }
        ) {
            LottieAnimation(
                composition = composition,
                progress = { if (animationsEnabled) progress else 0f },
                modifier = Modifier.size(size)
            )
            if (label != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (fullScreen) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    } else {
        Box(modifier, contentAlignment = Alignment.Center) { content() }
    }
}
