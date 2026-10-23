package com.jarvis.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jarvis.ai.services.ScreenCaptureService
import com.jarvis.ai.ui.App
import com.jarvis.ai.ui.JarvisTheme
import com.jarvis.ai.ui.JarvisViewModel

class MainActivity : ComponentActivity() {

    private val vm: JarvisViewModel by viewModels()

    private val watchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val d = r.data
            if (r.resultCode == RESULT_OK && d != null) {
                ScreenCaptureService.timeoutSec = vm.store.timeoutSec
                val i = Intent(this, ScreenCaptureService::class.java)
                    .putExtra("code", r.resultCode)
                    .putExtra("data", d)
                ContextCompat.startForegroundService(this, i)
            } else {
                Toast.makeText(this, "Screen Watch cancelled - consent dialog was declined.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askPermissions()
        setContent {
            JarvisTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App(vm) { startScreenWatch() }
                }
            }
        }
    }

    private fun startScreenWatch() {
        val mgr = getSystemService(MediaProjectionManager::class.java)
        watchLauncher.launch(mgr.createScreenCaptureIntent())
    }

    private fun askPermissions() {
        val want = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) want.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = want.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 42)
    }

    override fun onDestroy() {
        vm.shutdownVoice()
        super.onDestroy()
    }
}
