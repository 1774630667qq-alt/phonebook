package com.phonebook.senior.ui.pages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.data.model.Contact
import com.phonebook.senior.util.MediaUriStore
import com.phonebook.senior.util.SimAccounts
import kotlinx.coroutines.launch

class ContactFragment : Fragment() {

    var contactId: Long = 0L
        private set

    private var videoView: VideoView? = null
    private var imageView: ImageView? = null
    private var defaultView: LinearLayout? = null
    private var defaultInitialText: TextView? = null
    private var defaultNameText: TextView? = null
    private var audioPlayer: MediaPlayer? = null
    private var videoPlayer: MediaPlayer? = null

    private var mediaUri: Uri? = null
    private var mediaMode: String = Contact.MEDIA_MODE_PHOTO
    private var imageUriStrings: List<String> = emptyList()
    private var videoUriString: String = ""
    private var voiceUri: Uri? = null
    private var photoDisplayMode: String = Contact.PHOTO_DISPLAY_CAROUSEL
    private var photoCarouselIntervalSeconds: Int = DEFAULT_PHOTO_CAROUSEL_INTERVAL_SECONDS
    private var phoneNumber: String = ""
    private var contactName: String = ""
    private var isVideoPrepared = false
    private var isAudioPrepared = false
    private var pageActive = false
    private var carouselIndex = 0
    private var lastVisualSignature: String? = null
    private var lastVoiceSignature: String? = null
    private val carouselHandler = Handler(Looper.getMainLooper())
    private val carouselRunnable = object : Runnable {
        override fun run() {
            val images = currentPhotoUris()
            if (!pageActive ||
                images.size <= 1 ||
                photoDisplayMode != Contact.PHOTO_DISPLAY_CAROUSEL
            ) {
                return
            }
            carouselIndex = (carouselIndex + 1) % images.size
            showImage(images[carouselIndex])
            carouselHandler.postDelayed(this, photoCarouselIntervalMillis())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        readArguments()
        return inflater.inflate(R.layout.fragment_contact, container, false).also { view ->
            videoView = view.findViewById(R.id.contactVideoView)
            imageView = view.findViewById(R.id.contactImageView)
            defaultView = view.findViewById(R.id.contactDefaultView)
            defaultInitialText = view.findViewById(R.id.tvContactDefaultInitial)
            defaultNameText = view.findViewById(R.id.tvContactDefaultName)
            view.setOnClickListener { onPageClick() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lastVisualSignature = null
        lastVoiceSignature = null
        applyVisualMediaIfChanged()
        applyVoiceIfChanged()
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

    fun updateContact(
        mediaUriStr: String? = null,
        mediaMode: String = Contact.MEDIA_MODE_PHOTO,
        imageUris: String = "",
        videoUri: String = "",
        voiceUriStr: String? = null,
        photoDisplayMode: String = Contact.PHOTO_DISPLAY_CAROUSEL,
        photoCarouselIntervalSeconds: Int = DEFAULT_PHOTO_CAROUSEL_INTERVAL_SECONDS,
        phone: String = "",
        name: String = ""
    ) {
        mediaUri = mediaUriStr?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        this.mediaMode = mediaMode.ifBlank { Contact.MEDIA_MODE_PHOTO }
        imageUriStrings = MediaUriStore.decode(imageUris)
        videoUriString = videoUri
        voiceUri = voiceUriStr?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        this.photoDisplayMode = photoDisplayMode.ifBlank { Contact.PHOTO_DISPLAY_CAROUSEL }
        this.photoCarouselIntervalSeconds = photoCarouselIntervalSeconds
            .coerceIn(MIN_PHOTO_CAROUSEL_INTERVAL_SECONDS, MAX_PHOTO_CAROUSEL_INTERVAL_SECONDS)
        phoneNumber = phone
        contactName = name
        if (view != null) {
            applyVisualMediaIfChanged()
            applyVoiceIfChanged()
        }
    }

    private fun readArguments() {
        contactId = arguments?.getLong(ARG_CONTACT_ID) ?: 0L
        mediaUri = arguments?.getString(ARG_MEDIA_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }
        mediaMode = arguments?.getString(ARG_MEDIA_MODE)
            ?.takeIf { it.isNotBlank() }
            ?: Contact.MEDIA_MODE_PHOTO
        imageUriStrings = MediaUriStore.decode(arguments?.getString(ARG_IMAGE_URIS).orEmpty())
        videoUriString = arguments?.getString(ARG_VIDEO_URI).orEmpty()
        voiceUri = arguments?.getString(ARG_VOICE_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }
        photoDisplayMode = arguments?.getString(ARG_PHOTO_DISPLAY_MODE)
            ?.takeIf { it.isNotBlank() }
            ?: Contact.PHOTO_DISPLAY_CAROUSEL
        photoCarouselIntervalSeconds = arguments?.getInt(ARG_PHOTO_CAROUSEL_INTERVAL_SECONDS)
            ?.coerceIn(MIN_PHOTO_CAROUSEL_INTERVAL_SECONDS, MAX_PHOTO_CAROUSEL_INTERVAL_SECONDS)
            ?: DEFAULT_PHOTO_CAROUSEL_INTERVAL_SECONDS
        phoneNumber = arguments?.getString(ARG_PHONE).orEmpty()
        contactName = arguments?.getString(ARG_NAME).orEmpty()
    }

    private fun visualSignature(): String {
        return buildString {
            append(mediaMode).append('|')
            append(mediaUri?.toString().orEmpty()).append('|')
            append(videoUriString).append('|')
            append(imageUriStrings.joinToString(",")).append('|')
            append(photoDisplayMode).append('|')
            append(photoCarouselIntervalSeconds)
        }
    }

    private fun voiceSignature(): String {
        return "${mediaMode}|${voiceUri?.toString().orEmpty()}"
    }

    private fun applyVisualMediaIfChanged() {
        val signature = visualSignature()
        if (signature == lastVisualSignature) return
        lastVisualSignature = signature
        setupVisualMedia()
    }

    private fun applyVoiceIfChanged() {
        val signature = voiceSignature()
        if (signature == lastVoiceSignature) return
        lastVoiceSignature = signature
        setupVoice()
    }

    private fun setupVisualMedia() {
        stopPhotoCarousel()

        videoView?.stopPlayback()
        videoPlayer = null
        isVideoPrepared = false
        videoView?.visibility = View.GONE
        imageView?.visibility = View.GONE
        defaultView?.visibility = View.GONE

        if (mediaMode == Contact.MEDIA_MODE_VIDEO) {
            val videoUri = videoUriString.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                ?: mediaUri?.takeIf { isVideoUri(it) }
            if (videoUri == null) {
                showDefaultVisual()
                return
            }
            showVideo(videoUri)
            return
        }

        val images = currentPhotoUris()
        if (images.isEmpty()) {
            mediaUri?.takeIf { isVideoUri(it) }?.let {
                showVideo(it)
                return
            }
            showDefaultVisual()
            return
        }

        carouselIndex = if (photoDisplayMode == Contact.PHOTO_DISPLAY_RANDOM) {
            images.indices.random()
        } else {
            0
        }
        showImage(images[carouselIndex])
        if (photoDisplayMode == Contact.PHOTO_DISPLAY_CAROUSEL && images.size > 1) {
            startPhotoCarouselIfActive()
        }
    }

    private fun currentPhotoUris(): List<Uri> {
        val storedImages = imageUriStrings.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
            .filterNot { isVideoUri(it) }
        if (storedImages.isNotEmpty()) return storedImages
        return mediaUri?.takeUnless { isVideoUri(it) }?.let { listOf(it) }.orEmpty()
    }

    private fun isVideoUri(uri: Uri): Boolean {
        val context = context ?: return false
        return context.contentResolver.getType(uri).orEmpty().startsWith("video/")
    }

    private fun showImage(uri: Uri) {
        imageView?.setImageURI(uri)
        imageView?.visibility = View.VISIBLE
        videoView?.visibility = View.GONE
        defaultView?.visibility = View.GONE
    }

    private fun showVideo(uri: Uri) {
        videoView?.apply {
            visibility = View.VISIBLE
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
                showDefaultVisual()
                true
            }
        }
    }

    private fun applyVideoVolume(player: MediaPlayer) {
        val volume = if (pageActive) 1f else 0f
        try {
            player.setVolume(volume, volume)
        } catch (_: IllegalStateException) {
            // Player released or not in a valid state; ignore.
        }
    }

    private fun stopPhotoCarousel() {
        carouselHandler.removeCallbacks(carouselRunnable)
    }

    private fun photoCarouselIntervalMillis(): Long {
        return photoCarouselIntervalSeconds * 1000L
    }

    private fun startPhotoCarouselIfActive() {
        if (!pageActive ||
            mediaMode != Contact.MEDIA_MODE_PHOTO ||
            photoDisplayMode != Contact.PHOTO_DISPLAY_CAROUSEL ||
            currentPhotoUris().size <= 1
        ) {
            return
        }
        stopPhotoCarousel()
        carouselHandler.postDelayed(carouselRunnable, photoCarouselIntervalMillis())
    }

    private fun resumeActiveMedia() {
        if (!pageActive) {
            pauseInactiveMedia()
            return
        }
        startPhotoCarouselIfActive()
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
        videoView?.let { vv ->
            try {
                vv.pause()
            } catch (_: IllegalStateException) {
                // ignore
            }
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

    private fun showDefaultVisual() {
        val displayName = contactName.ifBlank { getString(R.string.contact_default_name) }
        defaultInitialText?.text = contactName.firstOrNull()?.toString() ?: "+"
        defaultNameText?.text = displayName
        defaultView?.visibility = View.VISIBLE
    }

    private fun setupVoice() {
        val context = context ?: return
        val uri = voiceUri

        audioPlayer?.let {
            try { it.release() } catch (_: Exception) { }
        }
        audioPlayer = null
        isAudioPrepared = false

        if (mediaMode == Contact.MEDIA_MODE_VIDEO || uri == null) return

        try {
            audioPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, uri)
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

    private fun onPageClick() {
        if (phoneNumber.isBlank()) return

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            makeCall()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            makeCall()
        }
    }

    private fun makeCall() {
        val context = context ?: return
        lifecycleScope.launch {
            val preferred = try {
                AppDatabase.getInstance(context.applicationContext)
                    .settingsDao()
                    .getSettingsOnce()
                    ?.preferredSimAccount
                    .orEmpty()
            } catch (_: Exception) {
                ""
            }
            startCallIntent(preferred)
        }
    }

    private fun startCallIntent(preferredSim: String) {
        val activity = activity ?: return
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.fromParts("tel", phoneNumber, null)
            }
            if (preferredSim.isNotBlank()) {
                val handle = SimAccounts.findHandle(activity, preferredSim)
                if (handle != null) {
                    intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                } else {
                    // Some OEM ROMs do not expose a handle for this subscription id.
                    // Fall back to the legacy chooser extras that many dialers still honor.
                    intent.putExtra("com.android.phone.extra.slot", SimAccounts.slotForSerialized(activity, preferredSim))
                    intent.putExtra("simSlot", SimAccounts.slotForSerialized(activity, preferredSim))
                    intent.putExtra("subscription", SimAccounts.subIdForSerialized(preferredSim))
                }
            }
            startActivity(intent)
        } catch (_: SecurityException) {
            Toast.makeText(activity, R.string.permission_required, Toast.LENGTH_SHORT).show()
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
        defaultInitialText = null
        defaultNameText = null
        audioPlayer?.let {
            try { it.release() } catch (_: Exception) { }
        }
        audioPlayer = null
        isAudioPrepared = false
        isVideoPrepared = false
        lastVisualSignature = null
        lastVoiceSignature = null
    }

    companion object {
        private const val REQUEST_CALL_PERMISSION = 100
        private const val ARG_CONTACT_ID = "contact_id"
        private const val ARG_MEDIA_URI = "media_uri"
        private const val ARG_MEDIA_MODE = "media_mode"
        private const val ARG_IMAGE_URIS = "image_uris"
        private const val ARG_VIDEO_URI = "video_uri"
        private const val ARG_VOICE_URI = "voice_uri"
        private const val ARG_PHOTO_DISPLAY_MODE = "photo_display_mode"
        private const val ARG_PHOTO_CAROUSEL_INTERVAL_SECONDS = "photo_carousel_interval_seconds"
        private const val ARG_PHONE = "phone"
        private const val ARG_NAME = "name"
        private const val DEFAULT_PHOTO_CAROUSEL_INTERVAL_SECONDS = 9
        private const val MIN_PHOTO_CAROUSEL_INTERVAL_SECONDS = 3
        private const val MAX_PHOTO_CAROUSEL_INTERVAL_SECONDS = 120

        fun newInstance(
            contactId: Long = 0L,
            mediaUri: String? = null,
            mediaMode: String = Contact.MEDIA_MODE_PHOTO,
            imageUris: String = "",
            videoUri: String = "",
            voiceUri: String? = null,
            photoDisplayMode: String = Contact.PHOTO_DISPLAY_CAROUSEL,
            photoCarouselIntervalSeconds: Int = DEFAULT_PHOTO_CAROUSEL_INTERVAL_SECONDS,
            phone: String = "",
            name: String = ""
        ): ContactFragment {
            return ContactFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_CONTACT_ID, contactId)
                    putString(ARG_MEDIA_URI, mediaUri)
                    putString(ARG_MEDIA_MODE, mediaMode)
                    putString(ARG_IMAGE_URIS, imageUris)
                    putString(ARG_VIDEO_URI, videoUri)
                    putString(ARG_VOICE_URI, voiceUri)
                    putString(ARG_PHOTO_DISPLAY_MODE, photoDisplayMode)
                    putInt(ARG_PHOTO_CAROUSEL_INTERVAL_SECONDS, photoCarouselIntervalSeconds)
                    putString(ARG_PHONE, phone)
                    putString(ARG_NAME, name)
                }
            }
        }
    }
}
