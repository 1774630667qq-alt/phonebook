package com.phonebook.senior.ui.pages

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.data.model.AppSettings
import com.phonebook.senior.ui.admin.ContactsManageActivity
import com.phonebook.senior.ui.theme.AppearanceMode
import com.phonebook.senior.ui.theme.FontSizeMode
import com.phonebook.senior.util.SimAccounts
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var radioAppearanceMode: RadioGroup
    private lateinit var radioFontSizeMode: RadioGroup
    private lateinit var switchAutoRecovery: SwitchCompat
    private lateinit var switchEasyMode: SwitchCompat
    private lateinit var switchEasyModeSwipeHint: SwitchCompat
    private lateinit var layoutAutoRecoveryOptions: LinearLayout
    private lateinit var layoutEasyModeOptions: LinearLayout
    private lateinit var tvTimeoutLabel: TextView
    private lateinit var etTimeout: EditText
    private lateinit var rowPreferredSim: View
    private lateinit var tvPreferredSimValue: TextView

    private var autoRecoveryEnabled = true
    private var appearanceMode = AppSettings.APPEARANCE_LIGHT
    private var fontSizeMode = AppSettings.FONT_SIZE_STANDARD
    private var easyModeEnabled = false
    private var easyModeSwipeHintEnabled = true
    private var preferredSimAccount: String = ""
    private var timeoutSeconds = DEFAULT_TIMEOUT_SECONDS
    private var isLoadingSettings = true

    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { finishForReset() }
    private val realtimeUpdateHandler = Handler(Looper.getMainLooper())
    private var pendingTimeoutUpdate: Runnable? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FontSizeMode.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        db = AppDatabase.getInstance(this)
        radioAppearanceMode = findViewById(R.id.radioAppearanceMode)
        radioFontSizeMode = findViewById(R.id.radioFontSizeMode)
        switchAutoRecovery = findViewById(R.id.switchAutoRecovery)
        switchEasyMode = findViewById(R.id.switchEasyMode)
        switchEasyModeSwipeHint = findViewById(R.id.switchEasyModeSwipeHint)
        layoutAutoRecoveryOptions = findViewById(R.id.layoutAutoRecoveryOptions)
        layoutEasyModeOptions = findViewById(R.id.layoutEasyModeOptions)
        tvTimeoutLabel = findViewById(R.id.tvTimeoutLabel)
        etTimeout = findViewById(R.id.etTimeout)
        rowPreferredSim = findViewById(R.id.rowPreferredSim)
        tvPreferredSimValue = findViewById(R.id.tvPreferredSimValue)

        rowPreferredSim.setOnClickListener {
            showPreferredSimDialog()
        }

        findViewById<View>(R.id.rowGuideContent).setOnClickListener {
            startActivityForResult(Intent(this, GuideContentActivity::class.java), REQUEST_GUIDE_CONTENT)
        }

        findViewById<Button>(R.id.btnCloseSettings).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.rowContactManagement).setOnClickListener {
            startActivityForResult(Intent(this, ContactsManageActivity::class.java), REQUEST_CONTACTS_MANAGE)
        }

        radioAppearanceMode.setOnCheckedChangeListener { _, checkedId ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            val selectedMode = if (checkedId == R.id.radioDarkMode) {
                AppSettings.APPEARANCE_DARK
            } else {
                AppSettings.APPEARANCE_LIGHT
            }
            if (selectedMode == appearanceMode) return@setOnCheckedChangeListener

            appearanceMode = selectedMode
            lifecycleScope.launch {
                db.settingsDao().updateAppearanceMode(selectedMode)
            }
            AppearanceMode.persistAndApply(this, selectedMode)
        }

        radioFontSizeMode.setOnCheckedChangeListener { _, checkedId ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            val selectedMode = if (checkedId == R.id.radioLargeText) {
                AppSettings.FONT_SIZE_LARGE
            } else {
                AppSettings.FONT_SIZE_STANDARD
            }
            if (selectedMode == fontSizeMode) return@setOnCheckedChangeListener

            fontSizeMode = selectedMode
            lifecycleScope.launch {
                db.settingsDao().updateFontSizeMode(selectedMode)
                FontSizeMode.persist(this@SettingsActivity, selectedMode)
                recreate()
            }
        }

        switchAutoRecovery.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            autoRecoveryEnabled = isChecked
            updateTimeoutControls()
            restartResetTimer()
            lifecycleScope.launch {
                db.settingsDao().updateAutoRecovery(isChecked)
            }
        }

        switchEasyMode.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            easyModeEnabled = isChecked
            updateEasyModeControls()
            lifecycleScope.launch {
                db.settingsDao().updateEasyMode(isChecked)
            }
        }

        switchEasyModeSwipeHint.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            easyModeSwipeHintEnabled = isChecked
            lifecycleScope.launch {
                db.settingsDao().updateEasyModeSwipeHint(isChecked)
            }
        }

        etTimeout.doAfterTextChanged {
            if (!isLoadingSettings && autoRecoveryEnabled) {
                scheduleTimeoutUpdate()
            }
        }

        loadSettingsData()
    }

    private fun loadSettingsData() {
        lifecycleScope.launch {
            val settings = db.settingsDao().getSettingsOnce() ?: AppSettings().also {
                db.settingsDao().insertOrUpdate(it)
            }
            runOnUiThread {
                appearanceMode = settings.appearanceMode
                fontSizeMode = settings.fontSizeMode
                FontSizeMode.persist(this@SettingsActivity, fontSizeMode)
                autoRecoveryEnabled = settings.autoRecoveryEnabled
                easyModeEnabled = settings.easyModeEnabled
                easyModeSwipeHintEnabled = settings.easyModeSwipeHintEnabled
                preferredSimAccount = settings.preferredSimAccount
                timeoutSeconds = settings.timeoutSeconds
                radioAppearanceMode.check(
                    if (appearanceMode == AppSettings.APPEARANCE_DARK) {
                        R.id.radioDarkMode
                    } else {
                        R.id.radioLightMode
                    }
                )
                radioFontSizeMode.check(
                    if (fontSizeMode == AppSettings.FONT_SIZE_LARGE) {
                        R.id.radioLargeText
                    } else {
                        R.id.radioStandardText
                    }
                )
                switchAutoRecovery.isChecked = autoRecoveryEnabled
                switchEasyMode.isChecked = easyModeEnabled
                switchEasyModeSwipeHint.isChecked = easyModeSwipeHintEnabled
                etTimeout.setText(timeoutSeconds.toString())
                updateTimeoutControls()
                updateEasyModeControls()
                updatePreferredSimLabel()
                isLoadingSettings = false
                restartResetTimer()
            }
        }
    }

    private fun scheduleTimeoutUpdate() {
        pendingTimeoutUpdate?.let { realtimeUpdateHandler.removeCallbacks(it) }
        val timeoutValue = etTimeout.text.toString().toIntOrNull()?.coerceIn(5, 120) ?: return

        pendingTimeoutUpdate = Runnable {
            timeoutSeconds = timeoutValue
            restartResetTimer()
            lifecycleScope.launch {
                db.settingsDao().updateTimeout(timeoutSeconds)
            }
        }.also {
            realtimeUpdateHandler.postDelayed(it, REALTIME_UPDATE_DELAY_MS)
        }
    }

    private fun finishForReset() {
        if (!isFinishing) {
            setResult(Activity.RESULT_CANCELED, Intent().putExtra(EXTRA_RESET_TO_GUIDE, true))
            finish()
        }
    }

    private fun restartResetTimer() {
        resetHandler.removeCallbacks(resetRunnable)
        if (autoRecoveryEnabled) {
            resetHandler.postDelayed(resetRunnable, timeoutSeconds * 1000L)
        }
    }

    private fun updateTimeoutControls() {
        layoutAutoRecoveryOptions.visibility = if (autoRecoveryEnabled) View.VISIBLE else View.GONE
    }

    private fun updateEasyModeControls() {
        layoutEasyModeOptions.visibility = if (easyModeEnabled) View.VISIBLE else View.GONE
    }

    private fun updatePreferredSimLabel() {
        val label = if (preferredSimAccount.isBlank()) {
            getString(R.string.preferred_sim_none)
        } else if (!SimAccounts.hasReadPhoneStatePermission(this)) {
            getString(R.string.preferred_sim_none)
        } else {
            val options = SimAccounts.listSimOptions(this)
            options.firstOrNull { it.serialized == preferredSimAccount }?.label
                ?: getString(R.string.preferred_sim_none)
        }
        tvPreferredSimValue.text = label
    }

    private fun showPreferredSimDialog() {
        if (!SimAccounts.hasReadPhoneStatePermission(this)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.preferred_sim)
                .setMessage(R.string.preferred_sim_unavailable)
                .setPositiveButton(R.string.preferred_sim_grant) { _, _ ->
                    requestPermissions(
                        arrayOf(Manifest.permission.READ_PHONE_STATE),
                        REQUEST_READ_PHONE_STATE
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }

        val options = SimAccounts.listSimOptions(this)
        if (options.isEmpty()) {
            Toast.makeText(this, R.string.preferred_sim_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val labels = buildList {
            add(getString(R.string.preferred_sim_none))
            addAll(options.map { it.label })
        }.toTypedArray()

        val values = buildList {
            add("")
            addAll(options.map { it.serialized })
        }

        val currentIndex = values.indexOf(preferredSimAccount).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.preferred_sim)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val picked = values[which]
                if (picked != preferredSimAccount) {
                    preferredSimAccount = picked
                    lifecycleScope.launch {
                        db.settingsDao().updatePreferredSimAccount(picked)
                    }
                    updatePreferredSimLabel()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_PHONE_STATE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showPreferredSimDialog()
            } else {
                Toast.makeText(this, R.string.preferred_sim_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        restartResetTimer()
    }

    override fun onResume() {
        super.onResume()
        restartResetTimer()
        if (!isLoadingSettings) {
            updatePreferredSimLabel()
        }
    }

    override fun onPause() {
        super.onPause()
        resetHandler.removeCallbacks(resetRunnable)
        pendingTimeoutUpdate?.let { realtimeUpdateHandler.removeCallbacks(it) }
        if (!isLoadingSettings && autoRecoveryEnabled) {
            etTimeout.text.toString().toIntOrNull()?.coerceIn(5, 120)?.let {
                timeoutSeconds = it
                lifecycleScope.launch {
                    db.settingsDao().updateTimeout(it)
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CONTACTS_MANAGE && requestCode != REQUEST_GUIDE_CONTENT) return

        val shouldReset = data?.getBooleanExtra(EXTRA_RESET_TO_GUIDE, false) == true
        if (shouldReset) {
            setResult(resultCode, Intent().putExtra(EXTRA_RESET_TO_GUIDE, true))
            finish()
        }
    }

    companion object {
        const val EXTRA_RESET_TO_GUIDE = "reset_to_guide"
        private const val REQUEST_CONTACTS_MANAGE = 2001
        private const val REQUEST_GUIDE_CONTENT = 2002
        private const val DEFAULT_TIMEOUT_SECONDS = 10
        private const val REALTIME_UPDATE_DELAY_MS = 350L
        private const val REQUEST_READ_PHONE_STATE = 4301
    }
}
