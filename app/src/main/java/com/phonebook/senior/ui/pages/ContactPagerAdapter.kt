package com.phonebook.senior.ui.pages

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.phonebook.senior.data.model.AppSettings
import com.phonebook.senior.data.model.Contact

class ContactPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private var contacts: List<Contact> = emptyList()
    private var guideSettings = AppSettings()
    private var guideContentVersion = 0L
    private var guideFragment: GuideFragment? = null
    private val contactFragments = mutableMapOf<Long, ContactFragment>()

    fun setContacts(newContacts: List<Contact>) {
        contacts = newContacts
        newContacts.forEach { contact ->
            contactFragments[contact.id]?.updateContact(
                mediaUriStr = contact.photoUri,
                voiceUriStr = contact.voiceUri,
                phone = contact.phone,
                name = contact.name
            )
        }
        notifyDataSetChanged()
    }

    fun setGuideContent(settings: AppSettings?) {
        val nextSettings = settings ?: AppSettings()
        val shouldRecreateGuide = guideSettings != nextSettings
        guideSettings = nextSettings
        guideFragment?.updateContent(
            enabled = guideSettings.guideContentEnabled,
            mode = guideSettings.guideContentMode,
            imageUri = guideSettings.guideImageUri,
            audioUri = guideSettings.guideAudioUri,
            videoUri = guideSettings.guideVideoUri
        )
        if (shouldRecreateGuide) {
            guideContentVersion += 1
            notifyDataSetChanged()
        } else {
            notifyItemChanged(0)
        }
    }

    fun getContactAtPage(pagePosition: Int): Contact? {
        return contacts.getOrNull(pagePosition - 1)
    }

    override fun getItemCount(): Int = contacts.size + 1

    override fun createFragment(position: Int): Fragment {
        if (position == 0) {
            return GuideFragment.newInstance(
                enabled = guideSettings.guideContentEnabled,
                mode = guideSettings.guideContentMode,
                imageUri = guideSettings.guideImageUri,
                audioUri = guideSettings.guideAudioUri,
                videoUri = guideSettings.guideVideoUri
            )
                .also { guideFragment = it }
        }

        val contact = contacts[position - 1]
        return ContactFragment.newInstance(
            mediaUri = contact.photoUri,
            voiceUri = contact.voiceUri,
            phone = contact.phone,
            name = contact.name
        ).also { contactFragments[contact.id] = it }
    }

    override fun getItemId(position: Int): Long {
        return if (position == 0) GUIDE_PAGE_ID_BASE - guideContentVersion else contacts[position - 1].id
    }

    override fun containsItem(itemId: Long): Boolean {
        return itemId == GUIDE_PAGE_ID_BASE - guideContentVersion || contacts.any { it.id == itemId }
    }

    companion object {
        private const val GUIDE_PAGE_ID_BASE = Long.MAX_VALUE
    }
}
