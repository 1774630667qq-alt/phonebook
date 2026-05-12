package com.phonebook.senior.ui.admin

import android.app.Activity
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.data.model.Contact
import com.phonebook.senior.ui.pages.SettingsActivity
import kotlinx.coroutines.launch

class ContactEditActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etRelationship: EditText
    private lateinit var tvTitle: TextView
    private lateinit var btnDelete: Button

    private var contactId: Long = 0
    private var isEditMode = false
    private var selectedMediaUri: Uri? = null
    private var selectedVoiceUri: Uri? = null
    private var existingMediaUri: String = ""
    private var existingVoiceUri: String = ""
    private var autoRecoveryEnabled = true
    private var timeoutSeconds = DEFAULT_TIMEOUT_SECONDS

    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { finishForReset() }

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            persistReadPermission(uri)
            Toast.makeText(this, R.string.photo_selected, Toast.LENGTH_SHORT).show()
        }
    }

    private val pickVoiceLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedVoiceUri = uri
            persistReadPermission(uri)
            Toast.makeText(this, R.string.audio_selected, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_contact)

        db = AppDatabase.getInstance(this)

        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etRelationship = findViewById(R.id.etRelationship)
        tvTitle = findViewById(R.id.tvTitle)
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
        }

        findViewById<Button>(R.id.btnSelectPhoto).setOnClickListener {
            pickMediaLauncher.launch(arrayOf("image/*", "video/*"))
        }

        findViewById<Button>(R.id.btnSelectVoice).setOnClickListener {
            pickVoiceLauncher.launch(arrayOf("audio/*"))
        }

        findViewById<Button>(R.id.btnCloseEditor).setOnClickListener {
            finish()
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
                autoRecoveryEnabled = it.autoRecoveryEnabled
                timeoutSeconds = it.timeoutSeconds
            }
        }
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
                }
            }
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

        val mediaUri = selectedMediaUri?.toString() ?: existingMediaUri
        val voiceUri = selectedVoiceUri?.toString() ?: existingVoiceUri

        lifecycleScope.launch {
            if (isEditMode) {
                db.contactDao().getContactById(contactId)?.let { existing ->
                    db.contactDao().update(
                        existing.copy(
                            name = name,
                            phone = phone,
                            relationship = relationship,
                            photoUri = mediaUri,
                            voiceUri = voiceUri
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
                        orderIndex = count
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

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers only grant temporary access.
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
    }

    override fun onPause() {
        super.onPause()
        resetHandler.removeCallbacks(resetRunnable)
    }

    companion object {
        const val EXTRA_CONTACT_ID = "contact_id"
        private const val DEFAULT_TIMEOUT_SECONDS = 10
    }
}
