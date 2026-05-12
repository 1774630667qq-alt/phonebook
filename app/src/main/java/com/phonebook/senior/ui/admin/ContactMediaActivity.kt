package com.phonebook.senior.ui.admin

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
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.data.model.Contact
import com.phonebook.senior.ui.pages.SettingsActivity
import com.phonebook.senior.ui.theme.FontSizeMode
import com.phonebook.senior.util.MediaUriStore
import kotlinx.coroutines.launch

class ContactMediaActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var radioContactPhotoMode: RadioButton
    private lateinit var radioContactVideoMode: RadioButton
    private lateinit var radioPhotoDisplayMode: RadioGroup
    private lateinit var layoutContactPhotoMode: LinearLayout
    private lateinit var layoutContactVideoMode: LinearLayout
    private lateinit var layoutContactMediaPreview: View
    private lateinit var imgContactMediaPreview: ImageView
    private lateinit var videoContactMediaPreview: VideoView
    private lateinit var tvContactMediaPreviewPlaceholder: TextView
    private lateinit var tvContactPhotoCount: TextView
    private lateinit var tvCarouselIntervalLabel: TextView
    private lateinit var etCarouselInterval: EditText
    private lateinit var btnOpenPhotoDrawer: Button
    private lateinit var btnPlayContactAudio: Button

    private var mode = Contact.MEDIA_MODE_PHOTO
    private var imageUris: MutableList<String> = mutableListOf()
    private var videoMediaUri = ""
    private var voiceUri = ""
    private var photoDisplayMode = Contact.PHOTO_DISPLAY_CAROUSEL
    private var photoCarouselIntervalSeconds = DEFAULT_CAROUSEL_INTERVAL_SECONDS
    private var originalMode = Contact.MEDIA_MODE_PHOTO
    private var originalImageUris: List<String> = emptyList()
    private var originalVideoMediaUri = ""
    private var originalVoiceUri = ""
    private var originalPhotoDisplayMode = Contact.PHOTO_DISPLAY_CAROUSEL
    private var originalPhotoCarouselIntervalSeconds = DEFAULT_CAROUSEL_INTERVAL_SECONDS
    private var photoColumnCount = DEFAULT_PHOTO_COLUMNS
    private var isLoading = true
    private var autoRecoveryEnabled = true
    private var timeoutSeconds = DEFAULT_TIMEOUT_SECONDS
    private var audioPlayer: MediaPlayer? = null

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
            mode = Contact.MEDIA_MODE_PHOTO
            renderSettings()
            openPhotoDrawer()
        }
    }

    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(uri)
            voiceUri = uri.toString()
            mode = Contact.MEDIA_MODE_PHOTO
            renderSettings()
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(uri)
            videoMediaUri = uri.toString()
            mode = Contact.MEDIA_MODE_VIDEO
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
            mode = Contact.MEDIA_MODE_PHOTO
            renderSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_media)

        db = AppDatabase.getInstance(this)
        radioContactPhotoMode = findViewById(R.id.radioContactPhotoMode)
        radioContactVideoMode = findViewById(R.id.radioContactVideoMode)
        radioPhotoDisplayMode = findViewById(R.id.radioPhotoDisplayMode)
        layoutContactPhotoMode = findViewById(R.id.layoutContactPhotoMode)
        layoutContactVideoMode = findViewById(R.id.layoutContactVideoMode)
        layoutContactMediaPreview = findViewById(R.id.layoutContactMediaPreview)
        imgContactMediaPreview = findViewById(R.id.imgContactMediaPreview)
        videoContactMediaPreview = findViewById(R.id.videoContactMediaPreview)
        tvContactMediaPreviewPlaceholder = findViewById(R.id.tvContactMediaPreviewPlaceholder)
        tvContactPhotoCount = findViewById(R.id.tvContactPhotoCount)
        tvCarouselIntervalLabel = findViewById(R.id.tvCarouselIntervalLabel)
        etCarouselInterval = findViewById(R.id.etCarouselInterval)
        btnOpenPhotoDrawer = findViewById(R.id.btnOpenPhotoDrawer)
        btnPlayContactAudio = findViewById(R.id.btnPlayContactAudio)

        readInitialMedia()
        captureOriginalState()

        findViewById<Button>(R.id.btnCloseContactMedia).setOnClickListener {
            confirmExitIfNeeded()
        }
        findViewById<Button>(R.id.btnDoneContactMedia).setOnClickListener {
            returnMediaResult()
        }
        findViewById<RadioGroup>(R.id.radioContactMediaMode).setOnCheckedChangeListener { _, checkedId ->
            if (isLoading) return@setOnCheckedChangeListener
            mode = if (checkedId == R.id.radioContactVideoMode) {
                Contact.MEDIA_MODE_VIDEO
            } else {
                Contact.MEDIA_MODE_PHOTO
            }
            renderSettings()
        }
        radioPhotoDisplayMode.setOnCheckedChangeListener { _, checkedId ->
            if (isLoading) return@setOnCheckedChangeListener
            photoDisplayMode = if (checkedId == R.id.radioPhotoRandom) {
                Contact.PHOTO_DISPLAY_RANDOM
            } else {
                Contact.PHOTO_DISPLAY_CAROUSEL
            }
            renderSettings()
        }
        etCarouselInterval.doAfterTextChanged {
            if (!isLoading) {
                photoCarouselIntervalSeconds = it.toString().toIntOrNull()
                    ?.coerceIn(MIN_CAROUSEL_INTERVAL_SECONDS, MAX_CAROUSEL_INTERVAL_SECONDS)
                    ?: DEFAULT_CAROUSEL_INTERVAL_SECONDS
            }
        }
        findViewById<Button>(R.id.btnSelectContactImage).setOnClickListener {
            launchImagePicker()
        }
        btnOpenPhotoDrawer.setOnClickListener {
            openPhotoDrawer()
        }
        findViewById<Button>(R.id.btnSelectContactAudio).setOnClickListener {
            pickAudioLauncher.launch(arrayOf("audio/*"))
        }
        findViewById<Button>(R.id.btnSelectContactVideo).setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }
        btnPlayContactAudio.setOnClickListener {
            playContactAudio()
        }
        findViewById<Button>(R.id.btnClearContactMedia).setOnClickListener {
            clearMedia()
        }
        isLoading = false
        renderSettings()
        loadSettings()
    }

    private fun readInitialMedia() {
        val mediaUri = intent.getStringExtra(EXTRA_MEDIA_URI).orEmpty()
        mode = intent.getStringExtra(EXTRA_MEDIA_MODE).orEmpty().ifBlank {
            if (isVideoMedia(mediaUri)) Contact.MEDIA_MODE_VIDEO else Contact.MEDIA_MODE_PHOTO
        }
        photoDisplayMode = intent.getStringExtra(EXTRA_PHOTO_DISPLAY_MODE).orEmpty().ifBlank {
            Contact.PHOTO_DISPLAY_CAROUSEL
        }
        photoCarouselIntervalSeconds = intent.getIntExtra(
            EXTRA_CAROUSEL_INTERVAL_SECONDS,
            DEFAULT_CAROUSEL_INTERVAL_SECONDS
        ).coerceIn(MIN_CAROUSEL_INTERVAL_SECONDS, MAX_CAROUSEL_INTERVAL_SECONDS)
        imageUris = MediaUriStore.decode(intent.getStringExtra(EXTRA_IMAGE_URIS).orEmpty()).toMutableList()
        videoMediaUri = intent.getStringExtra(EXTRA_VIDEO_URI).orEmpty()
        voiceUri = intent.getStringExtra(EXTRA_VOICE_URI).orEmpty()

        if (isVideoMedia(mediaUri)) {
            mode = Contact.MEDIA_MODE_VIDEO
            videoMediaUri = mediaUri
            imageUris = imageUris.filterNot { isVideoMedia(it) }.toMutableList()
        } else if (mediaUri.isNotBlank() && imageUris.isEmpty()) {
            imageUris = mutableListOf(mediaUri)
        }
    }

    private fun captureOriginalState() {
        originalMode = mode
        originalImageUris = imageUris.toList()
        originalVideoMediaUri = videoMediaUri
        originalVoiceUri = voiceUri
        originalPhotoDisplayMode = photoDisplayMode
        originalPhotoCarouselIntervalSeconds = photoCarouselIntervalSeconds
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val settings = db.settingsDao().getSettingsOnce()
            settings?.let {
                runOnUiThread {
                    autoRecoveryEnabled = it.autoRecoveryEnabled
                    timeoutSeconds = it.timeoutSeconds
                    restartResetTimer()
                }
            }
        }
    }

    private fun renderSettings() {
        isLoading = true
        findViewById<RadioGroup>(R.id.radioContactMediaMode).check(
            if (mode == Contact.MEDIA_MODE_VIDEO) R.id.radioContactVideoMode else R.id.radioContactPhotoMode
        )
        radioPhotoDisplayMode.check(
            if (photoDisplayMode == Contact.PHOTO_DISPLAY_RANDOM) {
                R.id.radioPhotoRandom
            } else {
                R.id.radioPhotoCarousel
            }
        )
        etCarouselInterval.setText(photoCarouselIntervalSeconds.toString())
        isLoading = false

        layoutContactPhotoMode.visibility = if (mode == Contact.MEDIA_MODE_PHOTO) View.VISIBLE else View.GONE
        layoutContactVideoMode.visibility = if (mode == Contact.MEDIA_MODE_VIDEO) View.VISIBLE else View.GONE
        layoutContactMediaPreview.visibility = if (mode == Contact.MEDIA_MODE_VIDEO) View.VISIBLE else View.GONE
        val showCarouselInterval = mode == Contact.MEDIA_MODE_PHOTO &&
            photoDisplayMode == Contact.PHOTO_DISPLAY_CAROUSEL
        tvCarouselIntervalLabel.visibility = if (showCarouselInterval) View.VISIBLE else View.GONE
        etCarouselInterval.visibility = if (showCarouselInterval) View.VISIBLE else View.GONE
        btnPlayContactAudio.isEnabled = mode == Contact.MEDIA_MODE_PHOTO && voiceUri.isNotBlank()
        btnPlayContactAudio.alpha = if (btnPlayContactAudio.isEnabled) 1f else 0.45f
        val photoImages = photoImages()
        tvContactPhotoCount.text = if (photoImages.isEmpty()) {
            getString(R.string.contact_media_default_preview)
        } else {
            getString(R.string.photo_count, photoImages.size)
        }

        renderPreview()
        restartResetTimer()
    }

    private fun renderPreview() {
        stopPreviewMedia()
        imgContactMediaPreview.visibility = View.GONE
        videoContactMediaPreview.visibility = View.GONE
        tvContactMediaPreviewPlaceholder.visibility = View.VISIBLE

        if (mode == Contact.MEDIA_MODE_PHOTO) {
            return
        }

        val videoUri = videoMediaUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        if (videoUri != null) {
            videoContactMediaPreview.visibility = View.VISIBLE
            tvContactMediaPreviewPlaceholder.visibility = View.GONE
            videoContactMediaPreview.setVideoURI(videoUri)
            videoContactMediaPreview.setOnPreparedListener { player ->
                player.isLooping = true
                player.setVolume(0.35f, 0.35f)
                videoContactMediaPreview.start()
            }
            videoContactMediaPreview.setOnErrorListener { _, _, _ ->
                videoContactMediaPreview.visibility = View.GONE
                tvContactMediaPreviewPlaceholder.visibility = View.VISIBLE
                true
            }
        }
    }

    private fun playContactAudio() {
        val audioUri = voiceUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) } ?: return
        audioPlayer?.release()
        audioPlayer = MediaPlayer().apply {
            setDataSource(this@ContactMediaActivity, audioUri)
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

    private fun photoImages(): List<String> {
        return imageUris.filterNot { isVideoMedia(it) }
    }

    private fun launchImagePicker() {
        pickImagesLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun openPhotoDrawer() {
        photoDrawerLauncher.launch(
            Intent(this, PhotoDrawerActivity::class.java)
                .putExtra(PhotoDrawerActivity.EXTRA_IMAGE_URIS, MediaUriStore.encode(photoImages()))
                .putExtra(PhotoDrawerActivity.EXTRA_PHOTO_COLUMNS, photoColumnCount)
        )
    }

    private fun clearMedia() {
        imageUris.clear()
        videoMediaUri = ""
        voiceUri = ""
        mode = Contact.MEDIA_MODE_PHOTO
        photoDisplayMode = Contact.PHOTO_DISPLAY_CAROUSEL
        photoCarouselIntervalSeconds = DEFAULT_CAROUSEL_INTERVAL_SECONDS
        renderSettings()
    }

    private fun hasUnsavedChanges(): Boolean {
        return mode != originalMode ||
            imageUris != originalImageUris ||
            videoMediaUri != originalVideoMediaUri ||
            voiceUri != originalVoiceUri ||
            photoDisplayMode != originalPhotoDisplayMode ||
            photoCarouselIntervalSeconds != originalPhotoCarouselIntervalSeconds
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
                returnMediaResult()
            }
            .setNegativeButton(R.string.discard_changes) { _, _ ->
                finish()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun returnMediaResult() {
        val photoImages = photoImages()
        val mediaUri = if (mode == Contact.MEDIA_MODE_VIDEO) videoMediaUri else photoImages.firstOrNull().orEmpty()
        val audioUri = voiceUri
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_MEDIA_URI, mediaUri)
                .putExtra(EXTRA_MEDIA_MODE, mode)
                .putExtra(EXTRA_IMAGE_URIS, MediaUriStore.encode(photoImages))
                .putExtra(EXTRA_VIDEO_URI, videoMediaUri)
                .putExtra(EXTRA_VOICE_URI, audioUri)
                .putExtra(EXTRA_PHOTO_DISPLAY_MODE, photoDisplayMode)
                .putExtra(EXTRA_CAROUSEL_INTERVAL_SECONDS, photoCarouselIntervalSeconds)
        )
        finish()
    }

    private fun isVideoMedia(mediaUri: String): Boolean {
        if (mediaUri.isBlank()) return false
        return try {
            contentResolver.getType(Uri.parse(mediaUri)).orEmpty().startsWith("video/")
        } catch (_: Exception) {
            false
        }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers only expose temporary read access.
        }
    }

    private fun finishForReset() {
        if (!isFinishing) {
            setResult(Activity.RESULT_CANCELED, Intent().putExtra(SettingsActivity.EXTRA_RESET_TO_GUIDE, true))
            finish()
        }
    }

    private fun restartResetTimer() {
        resetHandler.removeCallbacks(resetRunnable)
        if (autoRecoveryEnabled) {
            resetHandler.postDelayed(resetRunnable, timeoutSeconds * 1000L)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        restartResetTimer()
    }

    override fun onResume() {
        super.onResume()
        restartResetTimer()
        if (mode == Contact.MEDIA_MODE_VIDEO && videoMediaUri.isNotBlank()) {
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

    private fun stopPreviewMedia() {
        videoContactMediaPreview.stopPlayback()
        audioPlayer?.release()
        audioPlayer = null
    }

    companion object {
        const val EXTRA_MEDIA_URI = "media_uri"
        const val EXTRA_MEDIA_MODE = "media_mode"
        const val EXTRA_IMAGE_URIS = "image_uris"
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VOICE_URI = "voice_uri"
        const val EXTRA_PHOTO_DISPLAY_MODE = "photo_display_mode"
        const val EXTRA_CAROUSEL_INTERVAL_SECONDS = "carousel_interval_seconds"
        private const val DEFAULT_TIMEOUT_SECONDS = 10
        private const val DEFAULT_CAROUSEL_INTERVAL_SECONDS = 9
        private const val MIN_CAROUSEL_INTERVAL_SECONDS = 3
        private const val MAX_CAROUSEL_INTERVAL_SECONDS = 120
        private const val DEFAULT_PHOTO_COLUMNS = 3
        private const val MAX_PICKED_IMAGES = 50
    }
}
