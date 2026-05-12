package com.phonebook.senior.ui.pages

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.data.model.AppSettings
import kotlinx.coroutines.launch

class GuideContentActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var switchGuideContent: SwitchCompat
    private lateinit var layoutGuideOptions: LinearLayout
    private lateinit var layoutPhotoMode: LinearLayout
    private lateinit var layoutVideoMode: LinearLayout
    private lateinit var radioGuidePhoto: RadioButton
    private lateinit var radioGuideVideo: RadioButton
    private lateinit var imgGuidePreview: ImageView
    private lateinit var videoGuidePreview: VideoView
    private lateinit var tvGuidePreviewPlaceholder: TextView
    private lateinit var btnPlayGuideAudio: Button

    private var settings = AppSettings()
    private var isLoading = true
    private var audioPlayer: MediaPlayer? = null

    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { finishForReset() }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(uri)
            settings = settings.copy(
                guideContentEnabled = true,
                guideContentMode = AppSettings.GUIDE_MODE_PHOTO,
                guideImageUri = uri.toString()
            )
            persistGuideSettings()
            renderSettings()
        }
    }

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(uri)
            settings = settings.copy(
                guideContentEnabled = true,
                guideContentMode = AppSettings.GUIDE_MODE_PHOTO,
                guideAudioUri = uri.toString()
            )
            persistGuideSettings()
            renderSettings()
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(uri)
            settings = settings.copy(
                guideContentEnabled = true,
                guideContentMode = AppSettings.GUIDE_MODE_VIDEO,
                guideVideoUri = uri.toString()
            )
            persistGuideSettings()
            renderSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide_content)

        db = AppDatabase.getInstance(this)
        switchGuideContent = findViewById(R.id.switchGuideContent)
        layoutGuideOptions = findViewById(R.id.layoutGuideOptions)
        layoutPhotoMode = findViewById(R.id.layoutPhotoMode)
        layoutVideoMode = findViewById(R.id.layoutVideoMode)
        radioGuidePhoto = findViewById(R.id.radioGuidePhoto)
        radioGuideVideo = findViewById(R.id.radioGuideVideo)
        imgGuidePreview = findViewById(R.id.imgGuidePreview)
        videoGuidePreview = findViewById(R.id.videoGuidePreview)
        tvGuidePreviewPlaceholder = findViewById(R.id.tvGuidePreviewPlaceholder)
        btnPlayGuideAudio = findViewById(R.id.btnPlayGuideAudio)

        findViewById<Button>(R.id.btnCloseGuideContent).setOnClickListener {
            finish()
        }

        switchGuideContent.setOnCheckedChangeListener { _, isChecked ->
            if (isLoading) return@setOnCheckedChangeListener
            settings = settings.copy(guideContentEnabled = isChecked)
            persistGuideSettings()
            renderSettings()
        }

        findViewById<RadioGroup>(R.id.radioGuideMode).setOnCheckedChangeListener { _, checkedId ->
            if (isLoading) return@setOnCheckedChangeListener
            val mode = if (checkedId == R.id.radioGuideVideo) {
                AppSettings.GUIDE_MODE_VIDEO
            } else {
                AppSettings.GUIDE_MODE_PHOTO
            }
            settings = settings.copy(guideContentMode = mode, guideContentEnabled = true)
            persistGuideSettings()
            renderSettings()
        }

        findViewById<Button>(R.id.btnSelectGuideImage).setOnClickListener {
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        findViewById<Button>(R.id.btnSelectGuideAudio).setOnClickListener {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        }

        findViewById<Button>(R.id.btnSelectGuideVideo).setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }

        btnPlayGuideAudio.setOnClickListener {
            playGuideAudio()
        }

        loadSettings()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            settings = db.settingsDao().getSettingsOnce() ?: AppSettings()
            runOnUiThread {
                isLoading = false
                renderSettings()
            }
        }
    }

    private fun renderSettings() {
        isLoading = true
        switchGuideContent.isChecked = settings.guideContentEnabled
        findViewById<RadioGroup>(R.id.radioGuideMode).check(
            if (settings.guideContentMode == AppSettings.GUIDE_MODE_VIDEO) {
                R.id.radioGuideVideo
            } else {
                R.id.radioGuidePhoto
            }
        )
        isLoading = false

        layoutGuideOptions.visibility = if (settings.guideContentEnabled) View.VISIBLE else View.GONE
        layoutPhotoMode.visibility = if (isPhotoMode()) View.VISIBLE else View.GONE
        layoutVideoMode.visibility = if (isPhotoMode()) View.GONE else View.VISIBLE
        btnPlayGuideAudio.isEnabled = settings.guideAudioUri.isNotBlank()
        btnPlayGuideAudio.alpha = if (settings.guideAudioUri.isNotBlank()) 1f else 0.45f

        renderPreview()
        restartResetTimer()
    }

    private fun renderPreview() {
        stopPreviewMedia()
        imgGuidePreview.visibility = View.GONE
        videoGuidePreview.visibility = View.GONE
        tvGuidePreviewPlaceholder.visibility = View.VISIBLE

        if (!settings.guideContentEnabled) return

        if (isPhotoMode()) {
            val imageUri = settings.guideImageUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            if (imageUri != null) {
                imgGuidePreview.setImageURI(imageUri)
                imgGuidePreview.visibility = View.VISIBLE
                tvGuidePreviewPlaceholder.visibility = View.GONE
            }
            return
        }

        val videoUri = settings.guideVideoUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        if (videoUri != null) {
            videoGuidePreview.visibility = View.VISIBLE
            tvGuidePreviewPlaceholder.visibility = View.GONE
            videoGuidePreview.setVideoURI(videoUri)
            videoGuidePreview.setOnPreparedListener { player ->
                player.isLooping = true
                player.setVolume(0.35f, 0.35f)
                videoGuidePreview.start()
            }
            videoGuidePreview.setOnErrorListener { _, _, _ ->
                videoGuidePreview.visibility = View.GONE
                tvGuidePreviewPlaceholder.visibility = View.VISIBLE
                true
            }
        }
    }

    private fun playGuideAudio() {
        val audioUri = settings.guideAudioUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) } ?: return
        audioPlayer?.release()
        audioPlayer = MediaPlayer().apply {
            setDataSource(this@GuideContentActivity, audioUri)
            setOnPreparedListener { start() }
            setOnCompletionListener {
                release()
                audioPlayer = null
            }
            setOnErrorListener { _, _, _ ->
                release()
                audioPlayer = null
                true
            }
            prepareAsync()
        }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers only expose temporary read access.
        }
    }

    private fun persistGuideSettings() {
        lifecycleScope.launch {
            db.settingsDao().insertOrUpdate(settings)
        }
    }

    private fun isPhotoMode(): Boolean {
        return settings.guideContentMode != AppSettings.GUIDE_MODE_VIDEO
    }

    private fun stopPreviewMedia() {
        videoGuidePreview.stopPlayback()
        audioPlayer?.release()
        audioPlayer = null
    }

    private fun finishForReset() {
        if (!isFinishing) {
            setResult(Activity.RESULT_CANCELED, Intent().putExtra(SettingsActivity.EXTRA_RESET_TO_GUIDE, true))
            finish()
        }
    }

    private fun restartResetTimer() {
        resetHandler.removeCallbacks(resetRunnable)
        if (settings.autoRecoveryEnabled) {
            resetHandler.postDelayed(resetRunnable, settings.timeoutSeconds * 1000L)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        restartResetTimer()
    }

    override fun onResume() {
        super.onResume()
        restartResetTimer()
        if (settings.guideContentEnabled && !isPhotoMode() && settings.guideVideoUri.isNotBlank()) {
            renderPreview()
        }
    }

    override fun onPause() {
        super.onPause()
        resetHandler.removeCallbacks(resetRunnable)
        stopPreviewMedia()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPreviewMedia()
    }
}
