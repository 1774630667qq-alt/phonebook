package com.phonebook.senior.ui.pages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.data.model.AppSettings
import com.phonebook.senior.ui.admin.PhotoDrawerActivity
import com.phonebook.senior.ui.theme.FontSizeMode
import com.phonebook.senior.util.MediaUriStore
import kotlinx.coroutines.launch

class GuideContentActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var switchGuideContent: SwitchCompat
    private lateinit var layoutGuideOptions: LinearLayout
    private lateinit var layoutPhotoMode: LinearLayout
    private lateinit var layoutVideoMode: LinearLayout
    private lateinit var layoutGuideMediaPreview: View
    private lateinit var radioGuidePhoto: RadioButton
    private lateinit var radioGuideVideo: RadioButton
    private lateinit var radioPhotoDisplayMode: RadioGroup
    private lateinit var imgGuidePreview: ImageView
    private lateinit var videoGuidePreview: VideoView
    private lateinit var tvGuidePreviewPlaceholder: TextView
    private lateinit var tvGuidePhotoCount: TextView
    private lateinit var tvCarouselIntervalLabel: TextView
    private lateinit var etCarouselInterval: EditText
    private lateinit var btnPlayGuideAudio: Button

    private var settings = AppSettings()
    private var imageUris: MutableList<String> = mutableListOf()
    private var photoColumnCount = DEFAULT_PHOTO_COLUMNS
    private var isLoading = true
    private var audioPlayer: MediaPlayer? = null

    private var originalGuideContentEnabled = false
    private var originalGuideContentMode = AppSettings.GUIDE_MODE_PHOTO
    private var originalGuideImageUri = ""
    private var originalGuideImageUris: List<String> = emptyList()
    private var originalGuideAudioUri = ""
    private var originalGuideVideoUri = ""
    private var originalGuidePhotoDisplayMode = AppSettings.GUIDE_PHOTO_DISPLAY_CAROUSEL
    private var originalGuidePhotoCarouselIntervalSeconds = DEFAULT_CAROUSEL_INTERVAL_SECONDS

    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { finishForReset() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FontSizeMode.wrap(newBase))
    }

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { persistReadPermission(it) }
            imageUris = (imageUris + uris.map { it.toString() }).distinct().toMutableList()
            settings = settings.copy(
                guideContentEnabled = true,
                guideContentMode = AppSettings.GUIDE_MODE_PHOTO
            )
            renderSettings()
            openPhotoDrawer()
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
            renderSettings()
        }
    }

    private val photoDrawerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            imageUris = MediaUriStore.decode(
                result.data?.getStringExtra(PhotoDrawerActivity.EXTRA_IMAGE_URIS).orEmpty()
            ).toMutableList()
            photoColumnCount = result.data?.getIntExtra(
                PhotoDrawerActivity.EXTRA_PHOTO_COLUMNS,
                photoColumnCount
            ) ?: photoColumnCount
            settings = settings.copy(
                guideContentEnabled = true,
                guideContentMode = AppSettings.GUIDE_MODE_PHOTO,
                guideImageUris = MediaUriStore.encode(imageUris)
            )
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
        layoutGuideMediaPreview = findViewById(R.id.layoutGuideMediaPreview)
        radioGuidePhoto = findViewById(R.id.radioGuidePhoto)
        radioGuideVideo = findViewById(R.id.radioGuideVideo)
        radioPhotoDisplayMode = findViewById(R.id.radioGuidePhotoDisplayMode)
        imgGuidePreview = findViewById(R.id.imgGuidePreview)
        videoGuidePreview = findViewById(R.id.videoGuidePreview)
        tvGuidePreviewPlaceholder = findViewById(R.id.tvGuidePreviewPlaceholder)
        tvGuidePhotoCount = findViewById(R.id.tvGuidePhotoCount)
        tvCarouselIntervalLabel = findViewById(R.id.tvGuideCarouselIntervalLabel)
        etCarouselInterval = findViewById(R.id.etGuideCarouselInterval)
        btnPlayGuideAudio = findViewById(R.id.btnPlayGuideAudio)

        findViewById<Button>(R.id.btnCloseGuideContent).setOnClickListener {
            confirmExitIfNeeded()
        }
        findViewById<Button>(R.id.btnDoneGuideContent).setOnClickListener {
            saveGuideSettingsAndFinish()
        }

        switchGuideContent.setOnCheckedChangeListener { _, isChecked ->
            if (isLoading) return@setOnCheckedChangeListener
            settings = settings.copy(guideContentEnabled = isChecked)
            renderSettings()
        }

        findViewById<RadioGroup>(R.id.radioGuideMode).setOnCheckedChangeListener { _, checkedId ->
            if (isLoading) return@setOnCheckedChangeListener
            settings = settings.copy(
                guideContentMode = if (checkedId == R.id.radioGuideVideo) {
                    AppSettings.GUIDE_MODE_VIDEO
                } else {
                    AppSettings.GUIDE_MODE_PHOTO
                },
                guideContentEnabled = true
            )
            renderSettings()
        }

        radioPhotoDisplayMode.setOnCheckedChangeListener { _, checkedId ->
            if (isLoading) return@setOnCheckedChangeListener
            settings = settings.copy(
                guidePhotoDisplayMode = if (checkedId == R.id.radioGuidePhotoRandom) {
                    AppSettings.GUIDE_PHOTO_DISPLAY_RANDOM
                } else {
                    AppSettings.GUIDE_PHOTO_DISPLAY_CAROUSEL
                }
            )
            renderSettings()
        }

        etCarouselInterval.doAfterTextChanged {
            if (!isLoading) {
                settings = settings.copy(
                    guidePhotoCarouselIntervalSeconds = it.toString().toIntOrNull()
                        ?.coerceIn(MIN_CAROUSEL_INTERVAL_SECONDS, MAX_CAROUSEL_INTERVAL_SECONDS)
                        ?: DEFAULT_CAROUSEL_INTERVAL_SECONDS
                )
            }
        }

        findViewById<Button>(R.id.btnSelectGuideImages).setOnClickListener {
            launchImagePicker()
        }
        findViewById<Button>(R.id.btnOpenGuidePhotoDrawer).setOnClickListener {
            openPhotoDrawer()
        }
        findViewById<Button>(R.id.btnSelectGuideAudio).setOnClickListener {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        }
        findViewById<Button>(R.id.btnSelectGuideVideo).setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }
        findViewById<Button>(R.id.btnClearGuideContent).setOnClickListener {
            clearGuideContent()
        }
        btnPlayGuideAudio.setOnClickListener {
            playGuideAudio()
        }

        loadSettings()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            settings = db.settingsDao().getSettingsOnce() ?: AppSettings()
            imageUris = readGuideImages(settings).toMutableList()
            runOnUiThread {
                captureOriginalState()
                isLoading = false
                renderSettings()
            }
        }
    }

    private fun readGuideImages(source: AppSettings): List<String> {
        val storedImages = MediaUriStore.decode(source.guideImageUris)
        if (storedImages.isNotEmpty()) return storedImages
        return source.guideImageUri.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty()
    }

    private fun captureOriginalState() {
        originalGuideContentEnabled = settings.guideContentEnabled
        originalGuideContentMode = settings.guideContentMode
        originalGuideImageUri = settings.guideImageUri
        originalGuideImageUris = imageUris.toList()
        originalGuideAudioUri = settings.guideAudioUri
        originalGuideVideoUri = settings.guideVideoUri
        originalGuidePhotoDisplayMode = settings.guidePhotoDisplayMode
        originalGuidePhotoCarouselIntervalSeconds = settings.guidePhotoCarouselIntervalSeconds
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
        radioPhotoDisplayMode.check(
            if (settings.guidePhotoDisplayMode == AppSettings.GUIDE_PHOTO_DISPLAY_RANDOM) {
                R.id.radioGuidePhotoRandom
            } else {
                R.id.radioGuidePhotoCarousel
            }
        )
        etCarouselInterval.setText(
            settings.guidePhotoCarouselIntervalSeconds
                .coerceIn(MIN_CAROUSEL_INTERVAL_SECONDS, MAX_CAROUSEL_INTERVAL_SECONDS)
                .toString()
        )
        isLoading = false

        val photoMode = isPhotoMode()
        layoutGuideOptions.visibility = if (settings.guideContentEnabled) View.VISIBLE else View.GONE
        layoutPhotoMode.visibility = if (photoMode) View.VISIBLE else View.GONE
        layoutVideoMode.visibility = if (photoMode) View.GONE else View.VISIBLE
        layoutGuideMediaPreview.visibility = if (!photoMode && settings.guideContentEnabled) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val showCarouselInterval = photoMode &&
            settings.guidePhotoDisplayMode == AppSettings.GUIDE_PHOTO_DISPLAY_CAROUSEL
        tvCarouselIntervalLabel.visibility = if (showCarouselInterval) View.VISIBLE else View.GONE
        etCarouselInterval.visibility = if (showCarouselInterval) View.VISIBLE else View.GONE
        btnPlayGuideAudio.isEnabled = settings.guideAudioUri.isNotBlank()
        btnPlayGuideAudio.alpha = if (settings.guideAudioUri.isNotBlank()) 1f else 0.45f

        tvGuidePhotoCount.text = if (imageUris.isEmpty()) {
            getString(R.string.contact_media_default_preview)
        } else {
            getString(R.string.photo_count, imageUris.size)
        }

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

    private fun launchImagePicker() {
        pickImagesLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun openPhotoDrawer() {
        photoDrawerLauncher.launch(
            Intent(this, PhotoDrawerActivity::class.java)
                .putExtra(PhotoDrawerActivity.EXTRA_IMAGE_URIS, MediaUriStore.encode(imageUris))
                .putExtra(PhotoDrawerActivity.EXTRA_PHOTO_COLUMNS, photoColumnCount)
        )
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

    private fun clearGuideContent() {
        imageUris.clear()
        settings = settings.copy(
            guideContentEnabled = false,
            guideContentMode = AppSettings.GUIDE_MODE_PHOTO,
            guideImageUri = "",
            guideImageUris = "",
            guideAudioUri = "",
            guideVideoUri = "",
            guidePhotoDisplayMode = AppSettings.GUIDE_PHOTO_DISPLAY_CAROUSEL,
            guidePhotoCarouselIntervalSeconds = DEFAULT_CAROUSEL_INTERVAL_SECONDS
        )
        renderSettings()
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers only expose temporary read access.
        }
    }

    private fun isPhotoMode(): Boolean {
        return settings.guideContentMode != AppSettings.GUIDE_MODE_VIDEO
    }

    private fun hasUnsavedChanges(): Boolean {
        return settings.guideContentEnabled != originalGuideContentEnabled ||
            settings.guideContentMode != originalGuideContentMode ||
            settings.guideImageUri != originalGuideImageUri ||
            imageUris != originalGuideImageUris ||
            settings.guideAudioUri != originalGuideAudioUri ||
            settings.guideVideoUri != originalGuideVideoUri ||
            settings.guidePhotoDisplayMode != originalGuidePhotoDisplayMode ||
            settings.guidePhotoCarouselIntervalSeconds != originalGuidePhotoCarouselIntervalSeconds
    }

    private fun confirmExitIfNeeded() {
        if (!hasUnsavedChanges()) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.save_changes_title)
            .setMessage(R.string.save_changes_message)
            .setPositiveButton(R.string.save_and_exit) { _, _ ->
                saveGuideSettingsAndFinish()
            }
            .setNegativeButton(R.string.discard_changes) { _, _ ->
                finish()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun saveGuideSettingsAndFinish() {
        val guideImages = imageUris.filter { it.isNotBlank() }.distinct()
        lifecycleScope.launch {
            val currentSettings = db.settingsDao().getSettingsOnce() ?: AppSettings()
            db.settingsDao().insertOrUpdate(
                currentSettings.copy(
                    guideContentEnabled = settings.guideContentEnabled,
                    guideContentMode = settings.guideContentMode,
                    guideImageUri = guideImages.firstOrNull().orEmpty(),
                    guideImageUris = MediaUriStore.encode(guideImages),
                    guideAudioUri = settings.guideAudioUri,
                    guideVideoUri = settings.guideVideoUri,
                    guidePhotoDisplayMode = settings.guidePhotoDisplayMode,
                    guidePhotoCarouselIntervalSeconds = settings.guidePhotoCarouselIntervalSeconds
                        .coerceIn(MIN_CAROUSEL_INTERVAL_SECONDS, MAX_CAROUSEL_INTERVAL_SECONDS)
                )
            )
            runOnUiThread {
                finish()
            }
        }
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        confirmExitIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPreviewMedia()
    }

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 10
        private const val DEFAULT_CAROUSEL_INTERVAL_SECONDS = 9
        private const val MIN_CAROUSEL_INTERVAL_SECONDS = 3
        private const val MAX_CAROUSEL_INTERVAL_SECONDS = 120
        private const val DEFAULT_PHOTO_COLUMNS = 3
        private const val MAX_PICKED_IMAGES = 50
    }
}
