package com.phonebook.senior.ui.admin

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.phonebook.senior.R

class PhotoDrawerAdapter(
    private var photos: List<String>,
    private val onPhotoClick: (String) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<PhotoDrawerAdapter.ViewHolder>() {

    private val selectedPhotos = linkedSetOf<String>()

    fun updatePhotos(newPhotos: List<String>) {
        photos = newPhotos
        selectedPhotos.retainAll(newPhotos.toSet())
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun selected(): List<String> = selectedPhotos.toList()

    fun clearSelection() {
        selectedPhotos.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact_photo_drawer, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val photo = photos[position]
        holder.image.setImageURI(Uri.parse(photo))
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedPhotos.contains(photo)
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedPhotos.add(photo)
            } else {
                selectedPhotos.remove(photo)
            }
            onSelectionChanged()
        }
        holder.itemView.setOnClickListener { onPhotoClick(photo) }
        holder.itemView.setOnLongClickListener {
            if (selectedPhotos.contains(photo)) {
                selectedPhotos.remove(photo)
            } else {
                selectedPhotos.add(photo)
            }
            notifyItemChanged(position)
            onSelectionChanged()
            true
        }
    }

    override fun getItemCount(): Int = photos.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imgDrawerPhoto)
        val checkBox: CheckBox = view.findViewById(R.id.checkDrawerPhoto)
    }
}
