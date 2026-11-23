package com.bddhy.pindou

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.bddhy.pindou.ui.PixelArtScreen
import com.bddhy.pindou.ui.theme.PixelTheme

class MainActivity : ComponentActivity() {

    /** 从相册「分享到」带进来的图片。 */
    private val sharedImage = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedImage.value = intent?.imageUri()
        setContent {
            PixelTheme {
                PixelArtScreen(sharedImage = sharedImage.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedImage.value = intent.imageUri()
    }
}

private fun Intent.imageUri(): Uri? = when (action) {
    Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    }

    else -> null
}
