package com.wangzan101.mika.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaSwitch
import com.wangzan101.mika.ui.components.PageScaffold
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.voice.HeyOkaWakeWordService

@Composable
fun HeyOkaWakeWordSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, 0) }
    var enabled by rememberSaveable { mutableStateOf(prefs.getBoolean(PREF_ENABLED, false)) }

    fun applyEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean(PREF_ENABLED, value).apply()
        if (value) HeyOkaWakeWordService.start(context) else HeyOkaWakeWordService.stop(context)
    }

    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) applyEnabled(true) else applyEnabled(false)
    }

    PageScaffold("Aktivasi HEY OKA", onBack) {
        GroupLabel("Panggil Oka dengan suara")
        GroupCard {
            ListRow(MikaIcons.Spark, "Deteksi “Hey Oka”", "Mikrofon siaga sampai Anda mematikannya.", first = true) {
                MikaSwitch(
                    enabled,
                    { checked ->
                        if (!checked) {
                            applyEnabled(false)
                        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            applyEnabled(true)
                        } else {
                            permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
            }
        }
        Text(
            "Pertama kali, HEY OKA mengunduh model suara sekitar 40 MB. Setelah selesai, deteksi berjalan di perangkat tanpa internet. Android akan menampilkan notifikasi permanen saat mikrofon aktif.",
        )
        Text(
            "Catatan: mode ini memakai pengenalan suara umum dengan kosakata terbatas, bukan detektor wake-word khusus. Penggunaan baterai dan ketepatan kata “Oka” perlu diuji di perangkat Anda.",
        )
    }
}

private const val PREFS_NAME = "hey_oka_voice"
private const val PREF_ENABLED = "wake_word_enabled"
