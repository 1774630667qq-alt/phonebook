package com.phonebook.senior.ui.pages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.phonebook.senior.R

class ContactFragment : Fragment() {

    private var videoView: VideoView? = null
    private var imageView: ImageView? = null
    private var audioPlayer: MediaPlayer? = null

    private var mediaUri: Uri? = null
    private var voiceUri: Uri? = null
    private var phoneNumber: String = ""
    private var contactName: String = ""
    private var isVideoPrepared = false
    private var isAudioPrepared = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        readArguments()
        return inflater.inflate(R.layout.fragment_contact, container, false).also { view ->
            videoView = view.findViewById(R.id.contactVideoView)
            imageView = view.findViewById(R.id.contactImageView)
            view.setOnClickListener { onPageClick() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMedia()
    }

    fun updateContact(
        mediaUriStr: String? = null,
        voiceUriStr: String? = null,
        phone: String = "",
        name: String = ""
    ) {
        mediaUri = mediaUriStr?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        voiceUri = voiceUriStr?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        phoneNumber = phone
        contactName = name
        setupMedia()
    }

    private fun readArguments() {
        mediaUri = arguments?.getString(ARG_MEDIA_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }
        voiceUri = arguments?.getString(ARG_VOICE_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }
        phoneNumber = arguments?.getString(ARG_PHONE).orEmpty()
        contactName = arguments?.getString(ARG_NAME).orEmpty()
    }

    private fun setupMedia() {
        setupVisualMedia()
        setupVoice()
    }

    private fun setupVisualMedia() {
        val context = context ?: return
        val uri = mediaUri

        videoView?.stopPlayback()
        isVideoPrepared = false
        videoView?.visibility = View.GONE
        imageView?.visibility = View.GONE

        if (uri == null) return

        val mimeType = context.contentResolver.getType(uri).orEmpty()
        if (mimeType.startsWith("image/")) {
            imageView?.setImageURI(uri)
            imageView?.visibility = View.VISIBLE
            return
        }

        videoView?.apply {
            visibility = View.VISIBLE
            setVideoURI(uri)
            setOnPreparedListener { player ->
                isVideoPrepared = true
                player.isLooping = true
                player.setVolume(0f, 0f)
                start()
            }
            setOnErrorListener { _, _, _ ->
                isVideoPrepared = false
                visibility = View.GONE
                true
            }
        }
    }

    private fun setupVoice() {
        val context = context ?: return
        val uri = voiceUri

        audioPlayer?.release()
        audioPlayer = null
        isAudioPrepared = false

        if (uri == null) return

        try {
            audioPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
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
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.fromParts("tel", phoneNumber, null)
            }
            startActivity(intent)
        } catch (_: SecurityException) {
            Toast.makeText(requireContext(), R.string.permission_required, Toast.LENGTH_SHORT).show()
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
        audioPlayer?.release()
        audioPlayer = null
    }

    companion object {
        private const val REQUEST_CALL_PERMISSION = 100
        private const val ARG_MEDIA_URI = "media_uri"
        private const val ARG_VOICE_URI = "voice_uri"
        private const val ARG_PHONE = "phone"
        private const val ARG_NAME = "name"

        fun newInstance(
            mediaUri: String? = null,
            voiceUri: String? = null,
            phone: String = "",
            name: String = ""
        ): ContactFragment {
            return ContactFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEDIA_URI, mediaUri)
                    putString(ARG_VOICE_URI, voiceUri)
                    putString(ARG_PHONE, phone)
                    putString(ARG_NAME, name)
                }
            }
        }
    }
}
