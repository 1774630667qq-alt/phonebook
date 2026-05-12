package com.phonebook.senior.ui.admin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.data.model.Contact
import com.phonebook.senior.ui.pages.SettingsActivity
import com.phonebook.senior.ui.theme.FontSizeMode
import com.phonebook.senior.util.MediaUriStore
import kotlinx.coroutines.launch

class ContactEditActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etRelationship: EditText
    private lateinit var tvTitle: TextView
    private lateinit var tvContactMediaSummary: TextView
    private lateinit var btnDelete: Button

    private var contactId: Long = 0
    private var isEditMode = false
    private var selectedMediaUri: String? = null
    private var selectedVoiceUri: String? = null
    private var selectedMediaMode: String? = null
    private var selectedImageUris: String? = null
    private var selectedVideoUri: String? = null
    private var selectedPhotoDisplayMode: String? = null
    private var selectedCarouselIntervalSeconds: Int? = null
    private var existingMediaUri: String = ""
    private var existingVoiceUri: String = ""
    private var existingMediaMode: String = Contact.MEDIA_MODE_PHOTO
    private var existingImageUris: String = ""
    private var existingVideoUri: String = ""
    private var existingPhotoDisplayMode: String = Contact.PHOTO_DISPLAY_CAROUSEL
    private var existingCarouselIntervalSeconds: Int = DEFAULT_CAROUSEL_INTERVAL_SECONDS
    private var originalName: String = ""
    private var originalPhone: String = ""
    private var originalRelationship: String = ""
    private var originalMediaUri: String = ""
    private var originalVoiceUri: String = ""
    private var originalMediaMode: String = Contact.MEDIA_MODE_PHOTO
    private var originalImageUris: String = ""
    private var originalVideoUri: String = ""
    private var originalPhotoDisplayMode: String = Contact.PHOTO_DISPLAY_CAROUSEL
    private var originalCarouselIntervalSeconds: Int = DEFAULT_CAROUSEL_INTERVAL_SECONDS
    private var autoRecoveryEnabled = true
    private var timeoutSeconds = DEFAULT_TIMEOUT_SECONDS

    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { finishForReset() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FontSizeMode.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_contact)

        db = AppDatabase.getInstance(this)

        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etRelationship = findViewById(R.id.etRelationship)
        tvTitle = findViewById(R.id.tvTitle)
        tvContactMediaSummary = findViewById(R.id.tvContactMediaSummary)
        btnDelete = findViewById(R.id.btnDeleteContact)

        contactId = intent.getLongExtra(EXTRA_CONTACT_ID, 0)
        isEditMode = contactId > 0

        if (isEditMode) {
            tvTitle.setText(R.string.edit_contact)
            btnDelete.visibility = View.VISIBLE
            loadContact()
        } else {
            tvTitle.setText(R.string.add_contact)
            btnDelete.visibility = View.GONE
            captureOriginalState()
        }

        findViewById<Button>(R.id.btnOpenContactMedia).setOnClickListener {
            openContactMediaSettings()
        }

        findViewById<Button>(R.id.btnCloseEditor).setOnClickListener {
            confirmExitIfNeeded()
        }

        findViewById<Button>(R.id.btnSaveContact).setOnClickListener {
            saveContact()
        }

        btnDelete.setOnClickListener {
            confirmDelete()
        }

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
        updateMediaSummary()
    }

    private fun loadContact() {
        lifecycleScope.launch {
            val contact = db.contactDao().getContactById(contactId)
            contact?.let {
                runOnUiThread {
                    etName.setText(it.name)
                    etPhone.setText(it.phone)
                    etRelationship.setText(it.relationship)
                    existingMediaUri = it.photoUri
                    existingVoiceUri = it.voiceUri
                    existingMediaMode = it.mediaMode
                    existingImageUris = it.imageUris
                    existingVideoUri = it.videoUri
                    existingPhotoDisplayMode = it.photoDisplayMode
                    existingCarouselIntervalSeconds = it.photoCarouselIntervalSeconds
                    captureOriginalState()
                    updateMediaSummary()
                }
            }
        }
    }

    private fun captureOriginalState() {
        originalName = etName.text.toString()
        originalPhone = etPhone.text.toString()
        originalRelationship = etRelationship.text.toString()
        originalMediaUri = existingMediaUri
        originalVoiceUri = existingVoiceUri
        originalMediaMode = existingMediaMode
        originalImageUris = existingImageUris
        originalVideoUri = existingVideoUri
        originalPhotoDisplayMode = existingPhotoDisplayMode
        originalCarouselIntervalSeconds = existingCarouselIntervalSeconds
    }

    private fun openContactMediaSettings() {
        startActivityForResult(
            Intent(this, ContactMediaActivity::class.java).apply {
                putExtra(ContactMediaActivity.EXTRA_MEDIA_URI, currentMediaUri())
                putExtra(ContactMediaActivity.EXTRA_MEDIA_MODE, currentMediaMode())
                putExtra(ContactMediaActivity.EXTRA_IMAGE_URIS, currentImageUris())
                putExtra(ContactMediaActivity.EXTRA_VIDEO_URI, currentVideoUri())
                putExtra(ContactMediaActivity.EXTRA_VOICE_URI, currentVoiceUri())
                putExtra(ContactMediaActivity.EXTRA_PHOTO_DISPLAY_MODE, currentPhotoDisplayMode())
                putExtra(ContactMediaActivity.EXTRA_CAROUSEL_INTERVAL_SECONDS, currentCarouselIntervalSeconds())
            },
            REQUEST_CONTACT_MEDIA
        )
    }

    private fun currentMediaUri(): String {
        return selectedMediaUri ?: existingMediaUri
    }

    private fun currentVoiceUri(): String {
        return selectedVoiceUri ?: existingVoiceUri
    }

    private fun currentMediaMode(): String {
        return selectedMediaMode ?: existingMediaMode
    }

    private fun currentImageUris(): String {
        return selectedImageUris ?: existingImageUris
    }

    private fun currentVideoUri(): String {
        return selectedVideoUri ?: existingVideoUri
    }

    private fun currentPhotoDisplayMode(): String {
        return selectedPhotoDisplayMode ?: existingPhotoDisplayMode
    }

    private fun currentCarouselIntervalSeconds(): Int {
        return selectedCarouselIntervalSeconds ?: existingCarouselIntervalSeconds
    }

    private fun updateMediaSummary() {
        val mode = currentMediaMode()
        val images = currentContactImages()
        val videoUri = currentVideoUri().ifBlank { currentMediaUri().takeIf { isVideoMedia(it) }.orEmpty() }
        val voiceUri = currentVoiceUri()
        val summaryRes = when {
            mode == Contact.MEDIA_MODE_VIDEO && videoUri.isNotBlank() -> R.string.contact_media_summary_video
            images.isEmpty() && voiceUri.isBlank() -> R.string.contact_media_summary_empty
            images.size > 1 && voiceUri.isNotBlank() -> 0
            images.size > 1 -> -1
            voiceUri.isNotBlank() -> R.string.contact_media_summary_photo_audio
            else -> R.string.contact_media_summary_photo
        }
        tvContactMediaSummary.text = when (summaryRes) {
            0 -> getString(R.string.contact_media_summary_photo_audio_count, images.size)
            -1 -> getString(R.string.contact_media_summary_photo_count, images.size)
            else -> getString(summaryRes)
        }
    }

    private fun currentContactImages(): List<String> {
        val storedImages = MediaUriStore.decode(currentImageUris())
            .filterNot { isVideoMedia(it) }
        if (storedImages.isNotEmpty()) return storedImages
        val legacyUri = currentMediaUri()
        return if (legacyUri.isNotBlank() && !isVideoMedia(legacyUri)) listOf(legacyUri) else emptyList()
    }

    private fun hasUnsavedChanges(): Boolean {
        return etName.text.toString() != originalName ||
            etPhone.text.toString() != originalPhone ||
            etRelationship.text.toString() != originalRelationship ||
            currentMediaUri() != originalMediaUri ||
            currentVoiceUri() != originalVoiceUri ||
            currentMediaMode() != originalMediaMode ||
            currentImageUris() != originalImageUris ||
            currentVideoUri() != originalVideoUri ||
            currentPhotoDisplayMode() != originalPhotoDisplayMode ||
            currentCarouselIntervalSeconds() != originalCarouselIntervalSeconds
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
                saveContact()
            }
            .setNegativeButton(R.string.discard_changes) { _, _ ->
                finish()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun isVideoMedia(mediaUri: String): Boolean {
        if (mediaUri.isBlank()) return false
        return try {
            contentResolver.getType(Uri.parse(mediaUri)).orEmpty().startsWith("video/")
        } catch (_: Exception) {
            false
        }
    }

    private fun saveContact() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val relationship = etRelationship.text.toString().trim()

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, R.string.name_phone_required, Toast.LENGTH_SHORT).show()
            return
        }

        val voiceUri = currentVoiceUri()
        val mediaMode = currentMediaMode()
        val images = currentContactImages()
        val imageUris = MediaUriStore.encode(images)
        val videoUri = currentVideoUri().ifBlank {
            currentMediaUri().takeIf { isVideoMedia(it) }.orEmpty()
        }
        val carouselIntervalSeconds = currentCarouselIntervalSeconds()
        val mediaUri = if (mediaMode == Contact.MEDIA_MODE_VIDEO) {
            videoUri
        } else {
            images.firstOrNull().orEmpty()
        }
        val photoDisplayMode = currentPhotoDisplayMode()

        lifecycleScope.launch {
            if (isEditMode) {
                db.contactDao().getContactById(contactId)?.let { existing ->
                    db.contactDao().update(
                        existing.copy(
                            name = name,
                            phone = phone,
                            relationship = relationship,
                            photoUri = mediaUri,
                            voiceUri = voiceUri,
                            mediaMode = mediaMode,
                            imageUris = imageUris,
                            videoUri = videoUri,
                            photoDisplayMode = photoDisplayMode,
                            photoCarouselIntervalSeconds = carouselIntervalSeconds
                        )
                    )
                }
            } else {
                val count = db.contactDao().getCount()
                db.contactDao().insert(
                    Contact(
                        name = name,
                        phone = phone,
                        relationship = relationship,
                        photoUri = mediaUri,
                        voiceUri = voiceUri,
                        orderIndex = count,
                        mediaMode = mediaMode,
                        imageUris = imageUris,
                        videoUri = videoUri,
                        photoDisplayMode = photoDisplayMode,
                        photoCarouselIntervalSeconds = carouselIntervalSeconds
                    )
                )
            }

            runOnUiThread {
                Toast.makeText(this@ContactEditActivity, R.string.save_success, Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK, Intent().putExtra(SettingsActivity.EXTRA_RESET_TO_GUIDE, false))
                finish()
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_contact)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.yes) { _, _ ->
                lifecycleScope.launch {
                    db.contactDao().getContactById(contactId)?.let { db.contactDao().delete(it) }
                    runOnUiThread {
                        Toast.makeText(this@ContactEditActivity, R.string.deleted, Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK, Intent().putExtra(SettingsActivity.EXTRA_RESET_TO_GUIDE, false))
                        finish()
                    }
                }
            }
            .setNegativeButton(R.string.no, null)
            .show()
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
    }

    override fun onPause() {
        super.onPause()
        resetHandler.removeCallbacks(resetRunnable)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        confirmExitIfNeeded()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONTACT_MEDIA) return

        val shouldReset = data?.getBooleanExtra(SettingsActivity.EXTRA_RESET_TO_GUIDE, false) == true
        if (shouldReset) {
            setResult(resultCode, Intent().putExtra(SettingsActivity.EXTRA_RESET_TO_GUIDE, true))
            finish()
            return
        }

        if (resultCode != Activity.RESULT_OK) return

        selectedMediaUri = data?.getStringExtra(ContactMediaActivity.EXTRA_MEDIA_URI).orEmpty()
        selectedVoiceUri = data?.getStringExtra(ContactMediaActivity.EXTRA_VOICE_URI).orEmpty()
        selectedMediaMode = data?.getStringExtra(ContactMediaActivity.EXTRA_MEDIA_MODE).orEmpty()
        selectedImageUris = data?.getStringExtra(ContactMediaActivity.EXTRA_IMAGE_URIS).orEmpty()
        selectedVideoUri = data?.getStringExtra(ContactMediaActivity.EXTRA_VIDEO_URI).orEmpty()
        selectedPhotoDisplayMode = data?.getStringExtra(ContactMediaActivity.EXTRA_PHOTO_DISPLAY_MODE).orEmpty()
        selectedCarouselIntervalSeconds = data?.getIntExtra(
            ContactMediaActivity.EXTRA_CAROUSEL_INTERVAL_SECONDS,
            DEFAULT_CAROUSEL_INTERVAL_SECONDS
        )
        updateMediaSummary()
    }

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
        private const val REQUEST_CONTACT_MEDIA = 4101
        private const val DEFAULT_TIMEOUT_SECONDS = 10
        private const val DEFAULT_CAROUSEL_INTERVAL_SECONDS = 9
    }
}
