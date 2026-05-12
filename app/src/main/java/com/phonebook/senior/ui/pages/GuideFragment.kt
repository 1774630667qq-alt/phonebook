package com.phonebook.senior.ui.pages

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.fragment.app.Fragment
import com.phonebook.senior.R
import com.phonebook.senior.data.model.AppSettings

class GuideFragment : Fragment() {

    private var videoView: VideoView? = null
    private var imageView: ImageView? = null
    private var defaultView: TextView? = null
    private var audioPlayer: MediaPlayer? = null

    private var guideEnabled = false
    private var guideMode = AppSettings.GUIDE_MODE_PHOTO
    private var guideImageUri: Uri? = null
    private var guideAudioUri: Uri? = null
    private var guideVideoUri: Uri? = null
    private var isVideoPrepared = false
    private var isAudioPrepared = false

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
        setupContent()
    }

    fun updateContent(
        enabled: Boolean,
        mode: String,
        imageUri: String,
        audioUri: String,
        videoUri: String
    ) {
        guideEnabled = enabled
        guideMode = mode
        guideImageUri = imageUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        guideAudioUri = audioUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        guideVideoUri = videoUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        setupContent()
    }

    private fun readArguments() {
        val args = arguments
        guideEnabled = args?.getBoolean(ARG_ENABLED) ?: false
        guideMode = args?.getString(ARG_MODE) ?: AppSettings.GUIDE_MODE_PHOTO
        guideImageUri = args?.getString(ARG_IMAGE_URI)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        guideAudioUri = args?.getString(ARG_AUDIO_URI)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        guideVideoUri = args?.getString(ARG_VIDEO_URI)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    }

    private fun setupContent() {
        videoView?.stopPlayback()
        audioPlayer?.release()
        audioPlayer = null
        isVideoPrepared = false
        isAudioPrepared = false

        videoView?.visibility = View.GONE
        imageView?.visibility = View.GONE
        defaultView?.visibility = View.VISIBLE

        if (!guideEnabled) return

        if (guideMode == AppSettings.GUIDE_MODE_VIDEO) {
            setupVideo()
        } else {
            setupImageAndAudio()
        }
    }

    private fun setupVideo() {
        val uri = guideVideoUri ?: return
        videoView?.apply {
            visibility = View.VISIBLE
            defaultView?.visibility = View.GONE
            setVideoURI(uri)
            setOnPreparedListener { player ->
                isVideoPrepared = true
                player.isLooping = true
                player.setVolume(1f, 1f)
                start()
            }
            setOnErrorListener { _, _, _ ->
                isVideoPrepared = false
                visibility = View.GONE
                defaultView?.visibility = View.VISIBLE
                true
            }
        }
    }

    private fun setupImageAndAudio() {
        val imageUri = guideImageUri
        if (imageUri != null) {
            imageView?.setImageURI(imageUri)
            imageView?.visibility = View.VISIBLE
            defaultView?.visibility = View.GONE
        }

        val audioUri = guideAudioUri ?: return
        val context = context ?: return
        try {
            audioPlayer = MediaPlayer().apply {
                setDataSource(context, audioUri)
                setOnPreparedListener {
                    isAudioPrepared = true
                    isLooping = true
                    start()
                }
                setOnErrorListener { _, _, _ ->
                    isAudioPrepared = false
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            isAudioPrepared = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (isVideoPrepared && videoView?.isPlaying == false) {
            videoView?.start()
        }
        if (isAudioPrepared && audioPlayer?.isPlaying == false) {
            audioPlayer?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        videoView?.pause()
        audioPlayer?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        videoView?.stopPlayback()
        videoView = null
        imageView = null
        defaultView = null
        audioPlayer?.release()
        audioPlayer = null
    }

    companion object {
        private const val ARG_ENABLED = "enabled"
        private const val ARG_MODE = "mode"
        private const val ARG_IMAGE_URI = "image_uri"
        private const val ARG_AUDIO_URI = "audio_uri"
        private const val ARG_VIDEO_URI = "video_uri"

        fun newInstance(
            enabled: Boolean,
            mode: String,
            imageUri: String,
            audioUri: String,
            videoUri: String
        ): GuideFragment {
            return GuideFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_ENABLED, enabled)
                    putString(ARG_MODE, mode)
                    putString(ARG_IMAGE_URI, imageUri)
                    putString(ARG_AUDIO_URI, audioUri)
                    putString(ARG_VIDEO_URI, videoUri)
                }
            }
        }
    }
}
