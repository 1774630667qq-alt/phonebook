package com.phonebook.senior.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.phonebook.senior.R
import com.phonebook.senior.data.model.Contact

class ContactsAdapter(
    private var contacts: List<Contact>,
    private val onEditClick: (Contact) -> Unit,
    private val onDeleteClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ViewHolder>() {

    fun updateContacts(newContacts: List<Contact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]
        holder.avatarText.text = contact.name.firstOrNull()?.toString().orEmpty()
        holder.nameText.text = contact.name
        holder.relationshipText.text = contact.relationship
        holder.relationshipText.visibility = if (contact.relationship.isBlank()) View.GONE else View.VISIBLE
        holder.phoneText.text = contact.phone
        holder.itemView.setOnClickListener { onEditClick(contact) }
        holder.editButton.setOnClickListener { onEditClick(contact) }
        holder.deleteButton.setOnClickListener { onDeleteClick(contact) }
    }

    override fun getItemCount(): Int = contacts.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val avatarText: TextView = view.findViewById(R.id.tvContactAvatar)
        val nameText: TextView = view.findViewById(R.id.tvContactName)
        val relationshipText: TextView = view.findViewById(R.id.tvContactRelationship)
        val phoneText: TextView = view.findViewById(R.id.tvContactPhone)
        val editButton: Button = view.findViewById(R.id.btnEditContact)
        val deleteButton: Button = view.findViewById(R.id.btnDeleteContactQuick)
    }
}
