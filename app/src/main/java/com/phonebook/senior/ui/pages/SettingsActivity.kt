package com.phonebook.senior.ui.pages

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.data.model.AppSettings
import com.phonebook.senior.ui.admin.ContactsManageActivity
import com.phonebook.senior.ui.theme.AppearanceMode
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var radioAppearanceMode: RadioGroup
    private lateinit var switchAutoRecovery: SwitchCompat
    private lateinit var layoutAutoRecoveryOptions: LinearLayout
    private lateinit var tvTimeoutLabel: TextView
    private lateinit var etTimeout: EditText

    private var autoRecoveryEnabled = true
    private var appearanceMode = AppSettings.APPEARANCE_LIGHT
    private var timeoutSeconds = DEFAULT_TIMEOUT_SECONDS
    private var isLoadingSettings = true

    private val resetHandler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { finishForReset() }
    private val realtimeUpdateHandler = Handler(Looper.getMainLooper())
    private var pendingTimeoutUpdate: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        db = AppDatabase.getInstance(this)
        radioAppearanceMode = findViewById(R.id.radioAppearanceMode)
        switchAutoRecovery = findViewById(R.id.switchAutoRecovery)
        layoutAutoRecoveryOptions = findViewById(R.id.layoutAutoRecoveryOptions)
        tvTimeoutLabel = findViewById(R.id.tvTimeoutLabel)
        etTimeout = findViewById(R.id.etTimeout)

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

        switchAutoRecovery.setOnCheckedChangeListener { _, isChecked ->
            if (isLoadingSettings) return@setOnCheckedChangeListener
            autoRecoveryEnabled = isChecked
            updateTimeoutControls()
            restartResetTimer()
            lifecycleScope.launch {
                db.settingsDao().updateAutoRecovery(isChecked)
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
            val settings = db.settingsDao().getSettingsOnce()
            runOnUiThread {
                appearanceMode = settings?.appearanceMode ?: AppSettings.APPEARANCE_LIGHT
                autoRecoveryEnabled = settings?.autoRecoveryEnabled ?: true
                timeoutSeconds = settings?.timeoutSeconds ?: DEFAULT_TIMEOUT_SECONDS
                radioAppearanceMode.check(
                    if (appearanceMode == AppSettings.APPEARANCE_DARK) {
                        R.id.radioDarkMode
                    } else {
                        R.id.radioLightMode
                    }
                )
                switchAutoRecovery.isChecked = autoRecoveryEnabled
                etTimeout.setText(timeoutSeconds.toString())
                updateTimeoutControls()
                isLoadingSettings = false
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
    }
}
