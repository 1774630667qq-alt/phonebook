package com.phonebook.senior.ui.admin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.data.model.Contact
import com.phonebook.senior.ui.pages.SettingsActivity
import com.phonebook.senior.ui.theme.FontSizeMode
import kotlinx.coroutines.launch

class ContactsManageActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvContacts: RecyclerView
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var etContactSearch: EditText
    private lateinit var tvContactCount: TextView
    private lateinit var layoutEmptyContacts: LinearLayout
    private lateinit var tvEmptyContactsTitle: TextView
    private lateinit var tvEmptyContactsBody: TextView

    private var allContacts: List<Contact> = emptyList()
    private var autoRecoveryEnabled = true
    private var timeoutSeconds = DEFAULT_TIMEOUT_SECONDS

    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { finishForReset() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FontSizeMode.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contacts_manage)

        db = AppDatabase.getInstance(this)

        etContactSearch = findViewById(R.id.etContactSearch)
        tvContactCount = findViewById(R.id.tvContactCount)
        layoutEmptyContacts = findViewById(R.id.layoutEmptyContacts)
        tvEmptyContactsTitle = findViewById(R.id.tvEmptyContactsTitle)
        tvEmptyContactsBody = findViewById(R.id.tvEmptyContactsBody)
        rvContacts = findViewById(R.id.rvContacts)
        contactsAdapter = ContactsAdapter(
            emptyList(),
            onEditClick = { contact -> openEditor(contact) },
            onDeleteClick = { contact -> confirmDelete(contact) }
        )

        rvContacts.layoutManager = LinearLayoutManager(this)
        rvContacts.adapter = contactsAdapter

        findViewById<Button>(R.id.btnCloseContacts).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnAddContact).setOnClickListener {
            startActivityForResult(Intent(this, ContactEditActivity::class.java), REQUEST_EDIT_CONTACT)
        }

        etContactSearch.doAfterTextChanged {
            updateContactsView()
        }

        loadSettings()
        loadContacts()
    }

    private fun openEditor(contact: Contact) {
        startActivityForResult(
            Intent(this, ContactEditActivity::class.java).apply {
                putExtra(ContactEditActivity.EXTRA_CONTACT_ID, contact.id)
            },
            REQUEST_EDIT_CONTACT
        )
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

    private fun loadContacts() {
        lifecycleScope.launch {
            val contacts = db.contactDao().getAllContactsList()
            runOnUiThread {
                allContacts = contacts
                updateContactsView()
            }
        }
    }

    private fun updateContactsView() {
        val query = etContactSearch.text.toString().trim()
        val visibleContacts = if (query.isBlank()) {
            allContacts
        } else {
            allContacts.filter { contact ->
                contact.name.contains(query, ignoreCase = true) ||
                    contact.relationship.contains(query, ignoreCase = true) ||
                    contact.phone.contains(query, ignoreCase = true)
            }
        }

        contactsAdapter.updateContacts(visibleContacts)
        tvContactCount.text = if (query.isBlank()) {
            getString(R.string.contact_count, allContacts.size)
        } else {
            getString(R.string.contact_search_count, visibleContacts.size)
        }

        val isEmpty = visibleContacts.isEmpty()
        rvContacts.visibility = if (isEmpty) View.GONE else View.VISIBLE
        layoutEmptyContacts.visibility = if (isEmpty) View.VISIBLE else View.GONE
        tvEmptyContactsTitle.setText(
            if (allContacts.isEmpty()) R.string.contact_empty_title else R.string.contact_no_result_title
        )
        tvEmptyContactsBody.setText(
            if (allContacts.isEmpty()) R.string.contact_empty_body else R.string.contact_no_result_body
        )
    }

    private fun confirmDelete(contact: Contact) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_contact)
            .setMessage(getString(R.string.delete_contact_confirm, contact.name))
            .setPositiveButton(R.string.yes) { _, _ ->
                deleteContact(contact)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun deleteContact(contact: Contact) {
        lifecycleScope.launch {
            db.contactDao().delete(contact)
            runOnUiThread {
                loadContacts()
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
