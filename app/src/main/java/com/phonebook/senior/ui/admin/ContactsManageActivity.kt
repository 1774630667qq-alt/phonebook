package com.phonebook.senior.ui.admin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.ui.pages.SettingsActivity
import kotlinx.coroutines.launch

class ContactsManageActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvContacts: RecyclerView
    private lateinit var contactsAdapter: ContactsAdapter

    private var autoRecoveryEnabled = true
    private var timeoutSeconds = DEFAULT_TIMEOUT_SECONDS

    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { finishForReset() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts_manage)

        db = AppDatabase.getInstance(this)

        rvContacts = findViewById(R.id.rvContacts)
        contactsAdapter = ContactsAdapter(emptyList()) { contact ->
            startActivityForResult(
                Intent(this, ContactEditActivity::class.java).apply {
                    putExtra(ContactEditActivity.EXTRA_CONTACT_ID, contact.id)
                },
                REQUEST_EDIT_CONTACT
            )
        }

        rvContacts.layoutManager = LinearLayoutManager(this)
        rvContacts.adapter = contactsAdapter

        findViewById<Button>(R.id.btnCloseContacts).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnAddContact).setOnClickListener {
            startActivityForResult(Intent(this, ContactEditActivity::class.java), REQUEST_EDIT_CONTACT)
        }

        loadSettings()
        loadContacts()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val settings = db.settingsDao().getSettingsOnce()
            settings?.let {
                autoRecoveryEnabled = it.autoRecoveryEnabled
                timeoutSeconds = it.timeoutSeconds
            }
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            val contacts = db.contactDao().getAllContactsList()
            runOnUiThread {
                contactsAdapter.updateContacts(contacts)
            }
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
        loadContacts()
        restartResetTimer()
    }

    override fun onPause() {
        super.onPause()
        resetHandler.removeCallbacks(resetRunnable)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EDIT_CONTACT) return

        val shouldReset = data?.getBooleanExtra(SettingsActivity.EXTRA_RESET_TO_GUIDE, false) == true
        if (shouldReset) {
            setResult(resultCode, Intent().putExtra(SettingsActivity.EXTRA_RESET_TO_GUIDE, true))
            finish()
            return
        }

        if (resultCode == Activity.RESULT_OK) {
            loadContacts()
        }
    }

    companion object {
        private const val REQUEST_EDIT_CONTACT = 3001
        private const val DEFAULT_TIMEOUT_SECONDS = 10
    }
}
