package com.phonebook.senior.ui.pages

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.fragment.app.Fragment
import com.phonebook.senior.R
import com.phonebook.senior.data.model.AppSettings
import com.phonebook.senior.util.MediaUriStore

class GuideFragment : Fragment() {

    private var videoView: VideoView? = null
    private var imageView: ImageView? = null
    private var defaultView: TextView? = null
    private var videoPlayer: MediaPlayer? = null
    private var audioPlayer: MediaPlayer? = null

    private var guideEnabled = false
    private var guideMode = AppSettings.GUIDE_MODE_PHOTO
    private var guideImageUri: Uri? = null
    private var guideImageUriStrings: List<String> = emptyList()
    private var guideAudioUri: Uri? = null
    private var guideVideoUri: Uri? = null
    private var guidePhotoDisplayMode = AppSettings.GUIDE_PHOTO_DISPLAY_CAROUSEL
    private var guidePhotoCarouselIntervalSeconds = DEFAULT_GUIDE_CAROUSEL_INTERVAL_SECONDS
    private var isVideoPrepared = false
    private var isAudioPrepared = false
    private var pageActive = false
    private var carouselIndex = 0
    private var lastVisualSignature: String? = null
    private var lastAudioSignature: String? = null
    private val carouselHandler = Handler(Looper.getMainLooper())
    private val carouselRunnable = object : Runnable {
        override fun run() {
            val images = currentGuidePhotoUris()
            if (!pageActive ||
                images.size <= 1 ||
                guidePhotoDisplayMode != AppSettings.GUIDE_PHOTO_DISPLAY_CAROUSEL
            ) {
                return
            }
            carouselIndex = (carouselIndex + 1) % images.size
            showImage(images[carouselIndex])
            carouselHandler.postDelayed(this, guideCarouselIntervalMillis())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        readArguments()
        return inflater.inflate(R.layout.fragment_guide, container, false).also { view ->
            videoView = view.findViewById(R.id.guideVideoView)
            imageView = view.findViewById(R.id.guideImageView)
            defaultView = view.findViewById(R.id.tvGuideDefault)
            view.isClickable = false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lastVisualSignature = null
        lastAudioSignature = null
        applyVisualIfChanged()
        applyAudioIfChanged()
    }

    fun updateContent(
        enabled: Boolean,
        mode: String,
        imageUri: String,
        imageUris: String,
        audioUri: String,
        videoUri: String,
        photoDisplayMode: String,
        photoCarouselIntervalSeconds: Int
    ) {
        guideEnabled = enabled
        guideMode = mode
        guideImageUri = imageUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        guideImageUriStrings = MediaUriStore.decode(imageUris)
        guideAudioUri = audioUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        guideVideoUri = videoUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        guidePhotoDisplayMode = photoDisplayMode.ifBlank { AppSettings.GUIDE_PHOTO_DISPLAY_CAROUSEL }
        guidePhotoCarouselIntervalSeconds = photoCarouselIntervalSeconds
            .coerceIn(MIN_GUIDE_CAROUSEL_INTERVAL_SECONDS, MAX_GUIDE_CAROUSEL_INTERVAL_SECONDS)
        if (view != null) {
            applyVisualIfChanged()
            applyAudioIfChanged()
        }
    }

    fun setPageActive(active: Boolean) {
        if (pageActive == active) return
        pageActive = active
        if (active) {
            resumeActiveMedia()
        } else {
            pauseInactiveMedia()
        }
    }

    private fun readArguments() {
        val args = arguments
        guideEnabled = args?.getBoolean(ARG_ENABLED) ?: false
        guideMode = args?.getString(ARG_MODE) ?: AppSettings.GUIDE_MODE_PHOTO
        guideImageUri = args?.getString(ARG_IMAGE_URI)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        guideImageUriStrings = MediaUriStore.decode(args?.getString(ARG_IMAGE_URIS).orEmpty())
        guideAudioUri = args?.getString(ARG_AUDIO_URI)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        guideVideoUri = args?.getString(ARG_VIDEO_URI)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        guidePhotoDisplayMode = args?.getString(ARG_PHOTO_DISPLAY_MODE)
            ?.takeIf { it.isNotBlank() }
            ?: AppSettings.GUIDE_PHOTO_DISPLAY_CAROUSEL
        guidePhotoCarouselIntervalSeconds = args?.getInt(ARG_PHOTO_CAROUSEL_INTERVAL_SECONDS)
            ?.coerceIn(MIN_GUIDE_CAROUSEL_INTERVAL_SECONDS, MAX_GUIDE_CAROUSEL_INTERVAL_SECONDS)
            ?: DEFAULT_GUIDE_CAROUSEL_INTERVAL_SECONDS
    }

    private fun visualSignature(): String {
        return buildString {
            append(guideEnabled).append('|')
            append(guideMode).append('|')
            append(guideVideoUri?.toString().orEmpty()).append('|')
            append(guideImageUri?.toString().orEmpty()).append('|')
            append(guideImageUriStrings.joinToString(",")).append('|')
            append(guidePhotoDisplayMode).append('|')
            append(guidePhotoCarouselIntervalSeconds)
        }
    }

    private fun audioSignature(): String {
        return buildString {
            append(guideEnabled).append('|')
            append(guideMode).append('|')
            append(guideAudioUri?.toString().orEmpty())
        }
    }

    private fun applyVisualIfChanged() {
        val signature = visualSignature()
        if (signature == lastVisualSignature) return
        lastVisualSignature = signature
        setupVisual()
    }

    private fun applyAudioIfChanged() {
        val signature = audioSignature()
        if (signature == lastAudioSignature) return
        lastAudioSignature = signature
        setupAudio()
    }

    private fun setupVisual() {
        stopPhotoCarousel()
        videoView?.let {
            try { it.stopPlayback() } catch (_: Exception) { }
        }
        videoPlayer = null
        isVideoPrepared = false

        videoView?.visibility = View.GONE
        imageView?.visibility = View.GONE
        defaultView?.visibility = View.VISIBLE

        if (!guideEnabled) return

        if (guideMode == AppSettings.GUIDE_MODE_VIDEO) {
            setupVideo()
        } else {
            setupImage()
        }
    }

    private fun setupVideo() {
        val uri = guideVideoUri ?: return
        videoView?.apply {
            visibility = View.VISIBLE
            defaultView?.visibility = View.GONE
            setVideoURI(uri)
            setOnPreparedListener { player ->
                videoPlayer = player
                isVideoPrepared = true
                player.isLooping = true
                applyVideoVolume(player)
                if (pageActive) {
                    start()
                } else {
                    pause()
                }
            }
            setOnErrorListener { _, _, _ ->
                isVideoPrepared = false
                videoPlayer = null
                visibility = View.GONE
                defaultView?.visibility = View.VISIBLE
                true
            }
        }
    }

    private fun applyVideoVolume(player: MediaPlayer) {
        val volume = if (pageActive) 1f else 0f
        try {
            player.setVolume(volume, volume)
        } catch (_: IllegalStateException) {
            // ignore
        }
    }

    private fun setupImage() {
        val images = currentGuidePhotoUris()
        if (images.isEmpty()) return
        carouselIndex = if (guidePhotoDisplayMode == AppSettings.GUIDE_PHOTO_DISPLAY_RANDOM) {
            images.indices.random()
        } else {
            0
        }
        showImage(images[carouselIndex])
        if (guidePhotoDisplayMode == AppSettings.GUIDE_PHOTO_DISPLAY_CAROUSEL && images.size > 1) {
            startPhotoCarouselIfActive()
        }
    }

    private fun setupAudio() {
        audioPlayer?.let {
            try { it.release() } catch (_: Exception) { }
        }
        audioPlayer = null
        isAudioPrepared = false

        if (!guideEnabled || guideMode == AppSettings.GUIDE_MODE_VIDEO) return
        val audioUri = guideAudioUri ?: return
        val context = context ?: return

        try {
            audioPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, audioUri)
                setOnPreparedListener { player ->
                    isAudioPrepared = true
                    player.isLooping = true
                    if (pageActive && isResumed) {
                        try {
                            player.start()
                        } catch (_: IllegalStateException) {
                            // ignore
                        }
                    }
                }
                setOnErrorListener { _, _, _ ->
                    isAudioPrepared = false
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            isAudioPrepared = false
            audioPlayer = null
        }
    }

    private fun currentGuidePhotoUris(): List<Uri> {
        val storedImages = guideImageUriStrings.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        if (storedImages.isNotEmpty()) return storedImages
        return guideImageUri?.let { listOf(it) }.orEmpty()
    }

    private fun showImage(uri: Uri) {
        imageView?.setImageURI(uri)
        imageView?.visibility = View.VISIBLE
        defaultView?.visibility = View.GONE
        videoView?.visibility = View.GONE
    }

    private fun stopPhotoCarousel() {
        carouselHandler.removeCallbacks(carouselRunnable)
    }

    private fun guideCarouselIntervalMillis(): Long {
        return guidePhotoCarouselIntervalSeconds * 1000L
    }

    private fun startPhotoCarouselIfActive() {
        if (!pageActive ||
            !guideEnabled ||
            guideMode == AppSettings.GUIDE_MODE_VIDEO ||
            guidePhotoDisplayMode != AppSettings.GUIDE_PHOTO_DISPLAY_CAROUSEL ||
            currentGuidePhotoUris().size <= 1
        ) {
            return
        }
        stopPhotoCarousel()
        carouselHandler.postDelayed(carouselRunnable, guideCarouselIntervalMillis())
    }

    private fun resumeActiveMedia() {
        if (!pageActive) {
            pauseInactiveMedia()
            return
        }
        if (!guideEnabled) return

        if (guideMode == AppSettings.GUIDE_MODE_VIDEO) {
            videoPlayer?.let { applyVideoVolume(it) }
            if (isVideoPrepared) {
                videoView?.let { vv ->
                    try {
                        if (!vv.isPlaying) vv.start()
                    } catch (_: IllegalStateException) {
                        // ignore
                    }
                }
            }
            return
        }

        startPhotoCarouselIfActive()
        if (isAudioPrepared) {
            audioPlayer?.let { player ->
                try {
                    if (!player.isPlaying) player.start()
                } catch (_: IllegalStateException) {
                    // ignore
                }
            }
        }
    }

    private fun pauseInactiveMedia() {
        stopPhotoCarousel()
        videoPlayer?.let {
            try { it.setVolume(0f, 0f) } catch (_: IllegalStateException) { }
        }
        videoView?.let {
            try { it.pause() } catch (_: IllegalStateException) { }
        }
        if (isAudioPrepared) {
            audioPlayer?.let { player ->
                try {
                    if (player.isPlaying) player.pause()
                } catch (_: IllegalStateException) {
                    // ignore
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeActiveMedia()
    }

    override fun onPause() {
        super.onPause()
        pauseInactiveMedia()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPhotoCarousel()
        videoView?.let {
            try { it.stopPlayback() } catch (_: Exception) { }
        }
        videoPlayer = null
        videoView = null
        imageView = null
        defaultView = null
        audioPlayer?.let {
            try { it.release() } catch (_: Exception) { }
        }
        audioPlayer = null
        isAudioPrepared = false
        isVideoPrepared = false
        lastVisualSignature = null
        lastAudioSignature = null
    }

    companion object {
        private const val ARG_ENABLED = "enabled"
        private const val ARG_MODE = "mode"
        private const val ARG_IMAGE_URI = "image_uri"
        private const val ARG_IMAGE_URIS = "image_uris"
        private const val ARG_AUDIO_URI = "audio_uri"
        private const val ARG_VIDEO_URI = "video_uri"
        private const val ARG_PHOTO_DISPLAY_MODE = "photo_display_mode"
        private const val ARG_PHOTO_CAROUSEL_INTERVAL_SECONDS = "photo_carousel_interval_seconds"
        private const val DEFAULT_GUIDE_CAROUSEL_INTERVAL_SECONDS = 9
        private const val MIN_GUIDE_CAROUSEL_INTERVAL_SECONDS = 3
        private const val MAX_GUIDE_CAROUSEL_INTERVAL_SECONDS = 120

        fun newInstance(
            enabled: Boolean,
            mode: String,
            imageUri: String,
            imageUris: String,
            audioUri: String,
            videoUri: String,
            photoDisplayMode: String,
            photoCarouselIntervalSeconds: Int
        ): GuideFragment {
            return GuideFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_ENABLED, enabled)
                    putString(ARG_MODE, mode)
                    putString(ARG_IMAGE_URI, imageUri)
                    putString(ARG_IMAGE_URIS, imageUris)
                    putString(ARG_AUDIO_URI, audioUri)
                    putString(ARG_VIDEO_URI, videoUri)
                    putString(ARG_PHOTO_DISPLAY_MODE, photoDisplayMode)
                    putInt(ARG_PHOTO_CAROUSEL_INTERVAL_SECONDS, photoCarouselIntervalSeconds)
                }
            }
        }
    }
}
