package com.phonebook.senior.ui.pages

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.phonebook.senior.data.model.AppSettings
import com.phonebook.senior.data.model.Contact

/**
 * Layout:
 *  - position 0: the guide page (always present, never participates in the loop)
 *  - position 1 .. contacts.size * LOOP_COPIES: the repeating contact carousel
 *    where contact slot = (position - 1) % contacts.size
 */
class ContactPagerAdapter(private val activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private var contacts: List<Contact> = emptyList()
    private var guideSettings = AppSettings()
    private var activePosition = 0
    private var hostViewPager: ViewPager2? = null
    private var structureGeneration = 0L

    val contactCount: Int get() = contacts.size

    fun attachToViewPager(pager: ViewPager2) {
        hostViewPager = pager
    }

    fun startPage(): Int = 0

    /**
     * Returns true when [position] maps to the guide page.
     */
    fun isGuidePage(position: Int): Boolean = position == 0

    /**
     * Returns the contact slot (0 .. contactCount - 1) for a contact page,
     * or -1 when [position] is the guide page or there are no contacts.
     */
    fun slotOfPosition(position: Int): Int {
        if (position <= 0) return -1
        val size = contacts.size
        if (size == 0) return -1
        return ((position - 1) % size + size) % size
    }

    fun getContactAtPage(position: Int): Contact? {
        val slot = slotOfPosition(position)
        if (slot < 0) return null
        return contacts.getOrNull(slot)
    }

    /**
     * Page index at the middle of the loop band, displaying the same contact as [slot].
     */
    fun centerPageForSlot(slot: Int): Int {
        val size = contacts.size
        if (size == 0) return 0
        val bounded = ((slot % size) + size) % size
        return 1 + (LOOP_COPIES / 2) * size + bounded
    }

    /**
     * True when [position] is close enough to either loop edge that we should recenter
     * to avoid the user ever running into the hard boundary.
     */
    fun isNearLoopEdge(position: Int): Boolean {
        val size = contacts.size
        if (size < 2 || position <= 0) return false
        val buffer = size * EDGE_BUFFER_COPIES
        val minSafe = 1 + buffer
        val maxSafe = 1 + (LOOP_COPIES - EDGE_BUFFER_COPIES) * size - 1
        return position < minSafe || position > maxSafe
    }

    /**
     * @return true if the contacts list changed structurally (ids or order).
     */
    fun setContacts(newContacts: List<Contact>): Boolean {
        val oldIds = contacts.map { it.id }
        val newIds = newContacts.map { it.id }
        val structuralChange = oldIds != newIds

        contacts = newContacts
        newContacts.forEach { contact ->
            aliveContactFragments()
                .filter { it.contactId == contact.id }
                .forEach { f ->
                    f.updateContact(
                        mediaUriStr = contact.photoUri,
                        mediaMode = contact.mediaMode,
                        imageUris = contact.imageUris,
                        videoUri = contact.videoUri,
                        voiceUriStr = contact.voiceUri,
                        photoDisplayMode = contact.photoDisplayMode,
                        photoCarouselIntervalSeconds = contact.photoCarouselIntervalSeconds,
                        phone = contact.phone,
                        name = contact.name
                    )
                }
        }
        updateActiveFragments()
        if (structuralChange) {
            structureGeneration++
            notifySafely { notifyDataSetChanged() }
        }
        return structuralChange
    }

    fun setGuideContent(settings: AppSettings?) {
        val nextSettings = settings ?: AppSettings()
        if (guideSettings == nextSettings) {
            return
        }
        guideSettings = nextSettings
        aliveGuideFragments().forEach { f ->
            f.updateContent(
                enabled = guideSettings.guideContentEnabled,
                mode = guideSettings.guideContentMode,
                imageUri = guideSettings.guideImageUri,
                imageUris = guideSettings.guideImageUris,
                audioUri = guideSettings.guideAudioUri,
                videoUri = guideSettings.guideVideoUri,
                photoDisplayMode = guideSettings.guidePhotoDisplayMode,
                photoCarouselIntervalSeconds = guideSettings.guidePhotoCarouselIntervalSeconds
            )
        }
        updateActiveFragments()
    }

    fun setActivePage(position: Int) {
        activePosition = position
        updateActiveFragments()
    }

    private fun updateActiveFragments() {
        val guideActive = isGuidePage(activePosition)
        aliveGuideFragments().forEach { it.setPageActive(guideActive) }
        val activeContactId = contacts.getOrNull(slotOfPosition(activePosition))?.id
        aliveContactFragments().forEach { f ->
            f.setPageActive(activeContactId != null && f.contactId == activeContactId)
        }
    }

    private fun notifySafely(action: () -> Unit) {
        val pager = hostViewPager
        if (pager == null || pager.scrollState == ViewPager2.SCROLL_STATE_IDLE) {
            action()
        } else {
            pager.post { action() }
        }
    }

    private fun aliveGuideFragments(): List<GuideFragment> {
        return activity.supportFragmentManager.fragments.filterIsInstance<GuideFragment>()
    }

    private fun aliveContactFragments(): List<ContactFragment> {
        return activity.supportFragmentManager.fragments.filterIsInstance<ContactFragment>()
    }

    override fun getItemCount(): Int {
        return if (contacts.isEmpty()) 1 else 1 + contacts.size * LOOP_COPIES
    }

    override fun createFragment(position: Int): Fragment {
        if (isGuidePage(position)) {
            return GuideFragment.newInstance(
                enabled = guideSettings.guideContentEnabled,
                mode = guideSettings.guideContentMode,
                imageUri = guideSettings.guideImageUri,
                imageUris = guideSettings.guideImageUris,
                audioUri = guideSettings.guideAudioUri,
                videoUri = guideSettings.guideVideoUri,
                photoDisplayMode = guideSettings.guidePhotoDisplayMode,
                photoCarouselIntervalSeconds = guideSettings.guidePhotoCarouselIntervalSeconds
            ).also { it.setPageActive(position == activePosition) }
        }

        val contact = contacts[slotOfPosition(position)]
        return ContactFragment.newInstance(
            contactId = contact.id,
            mediaUri = contact.photoUri,
            mediaMode = contact.mediaMode,
            imageUris = contact.imageUris,
            videoUri = contact.videoUri,
            voiceUri = contact.voiceUri,
            photoDisplayMode = contact.photoDisplayMode,
            photoCarouselIntervalSeconds = contact.photoCarouselIntervalSeconds,
            phone = contact.phone,
            name = contact.name
        ).also { it.setPageActive(position == activePosition) }
    }

    override fun getItemId(position: Int): Long {
        return (structureGeneration shl GENERATION_SHIFT) or (position.toLong() and POSITION_MASK)
    }

    override fun containsItem(itemId: Long): Boolean {
        if ((itemId ushr GENERATION_SHIFT) != structureGeneration) return false
        val position = (itemId and POSITION_MASK).toInt()
        return position in 0 until itemCount
    }

    companion object {
        private const val LOOP_COPIES = 1000
        private const val EDGE_BUFFER_COPIES = 2
        private const val GENERATION_SHIFT = 40
        private const val POSITION_MASK = (1L shl GENERATION_SHIFT) - 1
    }
}
