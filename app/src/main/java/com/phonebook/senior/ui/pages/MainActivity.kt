package com.phonebook.senior.ui.pages

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.ui.admin.ContactEditActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: ContactPagerAdapter
    private lateinit var swipeHintTop: View
    private lateinit var swipeHintBottom: View
    private lateinit var db: AppDatabase

    private var verticalSwipeThreshold = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var verticalGestureConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enterImmersiveMode()

        db = AppDatabase.getInstance(this)
        verticalSwipeThreshold = 72f * resources.displayMetrics.density

        viewPager = findViewById(R.id.viewPager)
        swipeHintTop = findViewById(R.id.swipeHintTop)
        swipeHintBottom = findViewById(R.id.swipeHintBottom)

        pagerAdapter = ContactPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = 2

        setupPageHints()
        loadContacts()
        loadSettings()
    }

    private fun setupPageHints() {
        hideSwipeHints()
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                hideSwipeHints()
            }
        })
    }

    private fun hideSwipeHints() {
        swipeHintTop.visibility = View.GONE
        swipeHintBottom.visibility = View.GONE
    }

    private fun openSettingsPage() {
        startActivityForResult(Intent(this, SettingsActivity::class.java), REQUEST_SETTINGS)
    }

    private fun openContactEditorForCurrentPage() {
        val currentPage = viewPager.currentItem
        if (currentPage == 0) {
            startActivityForResult(Intent(this, ContactEditActivity::class.java), REQUEST_EDIT_CONTACT)
            return
        }

        pagerAdapter.getContactAtPage(currentPage)?.let { contact ->
            startActivityForResult(
                Intent(this, ContactEditActivity::class.java).apply {
                    putExtra(ContactEditActivity.EXTRA_CONTACT_ID, contact.id)
                },
                REQUEST_EDIT_CONTACT
            )
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            db.contactDao().getAllContacts().collectLatest { contacts ->
                pagerAdapter.setContacts(contacts)
                if (viewPager.currentItem >= pagerAdapter.itemCount) {
                    viewPager.setCurrentItem(0, false)
                }
            }
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            db.settingsDao().getSettings().collectLatest { settings ->
                pagerAdapter.setGuideContent(settings)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SETTINGS || requestCode == REQUEST_EDIT_CONTACT) {
            val shouldReset = data?.getBooleanExtra(SettingsActivity.EXTRA_RESET_TO_GUIDE, false) == true
            if (shouldReset || resultCode == Activity.RESULT_OK) {
                viewPager.setCurrentItem(0, true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        refreshSettingsOnce()
    }

    private fun refreshSettingsOnce() {
        lifecycleScope.launch {
            pagerAdapter.setGuideContent(db.settingsDao().getSettingsOnce())
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                verticalGestureConsumed = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!verticalGestureConsumed && event.pointerCount == 1) {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY
                    val verticalDistance = abs(deltaY)
                    val isVerticalDominant = verticalDistance > abs(deltaX) * 1.2f

                    if (isVerticalDominant && verticalDistance > verticalSwipeThreshold) {
                        triggerVerticalGesture(deltaY)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                viewPager.isUserInputEnabled = true
                if (verticalGestureConsumed) {
                    verticalGestureConsumed = false
                    return true
                }
            }
        }

        return if (verticalGestureConsumed) true else super.dispatchTouchEvent(event)
    }

    private fun triggerVerticalGesture(deltaY: Float) {
        if (verticalGestureConsumed) return
        verticalGestureConsumed = true
        viewPager.isUserInputEnabled = false

        if (deltaY > 0f) {
            openSettingsPage()
        } else {
            openContactEditorForCurrentPage()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    companion object {
        const val REQUEST_SETTINGS = 1001
        const val REQUEST_EDIT_CONTACT = 1002
    }
}
