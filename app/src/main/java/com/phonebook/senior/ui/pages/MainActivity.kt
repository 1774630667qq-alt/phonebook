package com.phonebook.senior.ui.pages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.phonebook.senior.R
import com.phonebook.senior.data.db.AppDatabase
import com.phonebook.senior.ui.admin.ContactEditActivity
import com.phonebook.senior.ui.theme.FontSizeMode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var pagerAdapter: ContactPagerAdapter
    private lateinit var swipeHintTop: View
    private lateinit var swipeHintBottom: View
    private lateinit var db: AppDatabase

    private var verticalSwipeThreshold = 0f
    private var pinchInThreshold = 0f
    private var directionLockThreshold = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var gestureDirection = GestureDirection.NONE
    private var verticalGestureTriggered = false
    private var easyModeEnabled = false
    private var easyModeSwipeHintEnabled = true
    private var pinchStartDistance = 0f
    private var pinchGestureConsumed = false
    private var easyModeSwipeHintShown = false
    private var easyModeSwipeToast: Toast? = null

    private enum class GestureDirection { NONE, HORIZONTAL, VERTICAL }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FontSizeMode.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enterImmersiveMode()

        db = AppDatabase.getInstance(this)
        verticalSwipeThreshold = 72f * resources.displayMetrics.density
        pinchInThreshold = 56f * resources.displayMetrics.density
        directionLockThreshold = 16f * resources.displayMetrics.density

        viewPager = findViewById(R.id.viewPager)
        swipeHintTop = findViewById(R.id.swipeHintTop)
        swipeHintBottom = findViewById(R.id.swipeHintBottom)

        pagerAdapter = ContactPagerAdapter(this)
        pagerAdapter.attachToViewPager(viewPager)
        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = 2
        viewPager.setCurrentItem(pagerAdapter.startPage(), false)
        pagerAdapter.setActivePage(viewPager.currentItem)

        setupPageHints()
        loadContacts()
        loadSettings()
    }

    private fun setupPageHints() {
        hideSwipeHints()
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                hideSwipeHints()
                pagerAdapter.setActivePage(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state != ViewPager2.SCROLL_STATE_IDLE) return
                val pos = viewPager.currentItem
                if (pagerAdapter.isNearLoopEdge(pos)) {
                    val slot = pagerAdapter.slotOfPosition(pos)
                    if (slot >= 0) {
                        val target = pagerAdapter.centerPageForSlot(slot)
                        if (target != pos) {
                            viewPager.setCurrentItem(target, false)
                        }
                    }
                }
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
        if (pagerAdapter.isGuidePage(currentPage)) {
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
                val structuralChange = pagerAdapter.setContacts(contacts)
                if (structuralChange) {
                    viewPager.post {
                        viewPager.setCurrentItem(pagerAdapter.startPage(), false)
                        pagerAdapter.setActivePage(viewPager.currentItem)
                    }
                } else {
                    pagerAdapter.setActivePage(viewPager.currentItem)
                }
            }
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            db.settingsDao().getSettings().collectLatest { settings ->
                easyModeEnabled = settings?.easyModeEnabled == true
                easyModeSwipeHintEnabled = settings?.easyModeSwipeHintEnabled != false
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
                viewPager.post {
                    val target = pagerAdapter.startPage()
                    if (viewPager.currentItem != target) {
                        viewPager.setCurrentItem(target, false)
                    }
                }
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
            val settings = db.settingsDao().getSettingsOnce()
            easyModeEnabled = settings?.easyModeEnabled == true
            easyModeSwipeHintEnabled = settings?.easyModeSwipeHintEnabled != false
            pagerAdapter.setGuideContent(settings)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                gestureDirection = GestureDirection.NONE
                verticalGestureTriggered = false
                pinchGestureConsumed = false
                pinchStartDistance = 0f
                easyModeSwipeHintShown = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (easyModeEnabled && event.pointerCount >= 2) {
                    pinchStartDistance = pointerDistance(event)
                    pinchGestureConsumed = false
                    viewPager.isUserInputEnabled = false
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (easyModeEnabled && !pinchGestureConsumed && event.pointerCount >= 2) {
                    handlePinchGesture(event)
                    return true
                }

                if (event.pointerCount == 1) {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY
                    val absX = abs(deltaX)
                    val absY = abs(deltaY)

                    // Lock the gesture direction as soon as either axis crosses the threshold.
                    if (gestureDirection == GestureDirection.NONE &&
                        (absX > directionLockThreshold || absY > directionLockThreshold)
                    ) {
                        gestureDirection = if (absY > absX * 1.2f) {
                            GestureDirection.VERTICAL
                        } else {
                            GestureDirection.HORIZONTAL
                        }
                        if (gestureDirection == GestureDirection.VERTICAL) {
                            // Cancel any in-progress horizontal scroll / pending click on children.
                            viewPager.isUserInputEnabled = false
                            cancelChildTouch(event)
                        }
                    }

                    if (gestureDirection == GestureDirection.VERTICAL) {
                        if (easyModeEnabled) {
                            if (!easyModeSwipeHintShown) {
                                easyModeSwipeHintShown = true
                                showEasyModeSwipeHintIfEnabled()
                            }
                        } else if (!verticalGestureTriggered && absY > verticalSwipeThreshold) {
                            verticalGestureTriggered = true
                            triggerVerticalGesture(deltaY)
                        }
                        return true
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (easyModeEnabled && event.pointerCount <= 2) {
                    viewPager.isUserInputEnabled = true
                    if (pinchGestureConsumed) {
                        pinchGestureConsumed = false
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasVertical = gestureDirection == GestureDirection.VERTICAL
                viewPager.isUserInputEnabled = true
                pinchStartDistance = 0f
                easyModeSwipeHintShown = false
                gestureDirection = GestureDirection.NONE
                verticalGestureTriggered = false
                if (pinchGestureConsumed) {
                    pinchGestureConsumed = false
                    return true
                }
                if (wasVertical) {
                    return true
                }
            }
        }

        return super.dispatchTouchEvent(event)
    }

    private fun cancelChildTouch(event: MotionEvent) {
        val cancel = MotionEvent.obtain(
            event.downTime,
            event.eventTime,
            MotionEvent.ACTION_CANCEL,
            event.x,
            event.y,
            event.metaState
        )
        try {
            super.dispatchTouchEvent(cancel)
        } finally {
            cancel.recycle()
        }
    }

    private fun showEasyModeSwipeHintIfEnabled() {
        if (!easyModeSwipeHintEnabled) return
        easyModeSwipeToast?.cancel()
        easyModeSwipeToast = Toast.makeText(
            this,
            R.string.easy_mode_swipe_toast,
            Toast.LENGTH_SHORT
        ).also { it.show() }
    }

    private fun handlePinchGesture(event: MotionEvent) {
        if (pinchStartDistance <= 0f) {
            pinchStartDistance = pointerDistance(event)
            return
        }

        val currentDistance = pointerDistance(event)
        val inwardDistance = pinchStartDistance - currentDistance
        val inwardRatio = if (pinchStartDistance > 0f) currentDistance / pinchStartDistance else 1f
        if (inwardDistance > pinchInThreshold && inwardRatio < 0.78f) {
            pinchGestureConsumed = true
            viewPager.isUserInputEnabled = false
            openSettingsPage()
        }
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val deltaX = event.getX(0) - event.getX(1)
        val deltaY = event.getY(0) - event.getY(1)
        return sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    private fun triggerVerticalGesture(deltaY: Float) {
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
