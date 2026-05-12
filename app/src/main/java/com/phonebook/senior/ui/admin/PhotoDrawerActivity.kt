package com.phonebook.senior.ui.admin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.phonebook.senior.R
import com.phonebook.senior.ui.theme.FontSizeMode
import com.phonebook.senior.util.MediaUriStore

class PhotoDrawerActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvColumns: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var btnDelete: Button
    private lateinit var photoAdapter: PhotoDrawerAdapter
    private lateinit var gridLayoutManager: GridLayoutManager

    private var imageUris: MutableList<String> = mutableListOf()
    private var photoColumnCount = DEFAULT_PHOTO_COLUMNS
    private var originalImageUris: List<String> = emptyList()
    private var originalPhotoColumnCount = DEFAULT_PHOTO_COLUMNS

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FontSizeMode.wrap(newBase))
    }

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_IMAGES)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { persistReadPermission(it) }
            imageUris = (imageUris + uris.map { it.toString() }).distinct().toMutableList()
            renderPhotos()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_drawer)

        imageUris = MediaUriStore.decode(intent.getStringExtra(EXTRA_IMAGE_URIS).orEmpty()).toMutableList()
        photoColumnCount = intent.getIntExtra(EXTRA_PHOTO_COLUMNS, DEFAULT_PHOTO_COLUMNS)
            .coerceIn(MIN_PHOTO_COLUMNS, MAX_PHOTO_COLUMNS)
        originalImageUris = imageUris.toList()
        originalPhotoColumnCount = photoColumnCount

        tvTitle = findViewById(R.id.tvPhotoDrawerPageTitle)
        tvColumns = findViewById(R.id.tvPhotoDrawerPageColumns)
        tvEmpty = findViewById(R.id.tvPhotoDrawerPageEmpty)
        btnDelete = findViewById(R.id.btnPhotoDrawerPageDelete)

        gridLayoutManager = GridLayoutManager(this, photoColumnCount)
        photoAdapter = PhotoDrawerAdapter(
            imageUris,
            onPhotoClick = { showPhotoDetail(it) },
            onSelectionChanged = { updateActions() }
        )
        findViewById<RecyclerView>(R.id.rvPhotoDrawerPage).apply {
            layoutManager = gridLayoutManager
            adapter = photoAdapter
        }

        findViewById<Button>(R.id.btnClosePhotoDrawerPage).setOnClickListener {
            confirmExitIfNeeded()
        }
        findViewById<Button>(R.id.btnDonePhotoDrawerPage).setOnClickListener {
            returnResult()
        }
        findViewById<Button>(R.id.btnPhotoDrawerPageAdd).setOnClickListener {
            launchImagePicker()
        }
        findViewById<Button>(R.id.btnPhotoDrawerPageColumnsMinus).setOnClickListener {
            changePhotoColumns(-1)
        }
        findViewById<Button>(R.id.btnPhotoDrawerPageColumnsPlus).setOnClickListener {
            changePhotoColumns(1)
        }
        btnDelete.setOnClickListener {
            confirmDeleteSelectedPhotos()
        }

        renderPhotos()
    }

    private fun launchImagePicker() {
        pickImagesLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun renderPhotos() {
        photoAdapter.updatePhotos(imageUris)
        tvTitle.text = getString(R.string.photo_drawer_title_count, imageUris.size)
        tvColumns.text = getString(R.string.photo_columns, photoColumnCount)
        tvEmpty.visibility = if (imageUris.isEmpty()) View.VISIBLE else View.GONE
        updateActions()
    }

    private fun updateActions() {
        val selectedCount = photoAdapter.selected().size
        btnDelete.isEnabled = selectedCount > 0
        btnDelete.alpha = if (btnDelete.isEnabled) 1f else 0.45f
        btnDelete.text = if (selectedCount > 0) {
            getString(R.string.delete_selected_photos_count, selectedCount)
        } else {
            getString(R.string.delete_selected_photos)
        }
    }

    private fun changePhotoColumns(delta: Int) {
        photoColumnCount = (photoColumnCount + delta).coerceIn(MIN_PHOTO_COLUMNS, MAX_PHOTO_COLUMNS)
        gridLayoutManager.spanCount = photoColumnCount
        tvColumns.text = getString(R.string.photo_columns, photoColumnCount)
    }

    private fun confirmDeleteSelectedPhotos() {
        val selected = photoAdapter.selected()
        if (selected.isEmpty()) return

        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_selected_photos_confirm, selected.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                imageUris = imageUris.filterNot { selected.contains(it) }.toMutableList()
                photoAdapter.clearSelection()
                renderPhotos()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showPhotoDetail(photo: String) {
        val photoIndex = imageUris.indexOf(photo).coerceAtLeast(0)
        val imageView = ImageView(this).apply {
            adjustViewBounds = true
            setImageURI(Uri.parse(photo))
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(0, 12, 0, 12)
            maxHeight = (resources.displayMetrics.heightPixels * 0.68f).toInt()
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.photo_detail_title, photoIndex + 1, imageUris.size.coerceAtLeast(1)))
            .setView(imageView)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers only expose temporary read access.
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        return imageUris != originalImageUris || photoColumnCount != originalPhotoColumnCount
    }

    private fun confirmExitIfNeeded() {
        if (!hasUnsavedChanges()) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.save_changes_title)
            .setMessage(R.string.save_changes_message)
            .setPositiveButton(R.string.save_and_exit) { _, _ ->
                returnResult()
            }
            .setNegativeButton(R.string.discard_changes) { _, _ ->
                finish()
            }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun returnResult() {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_IMAGE_URIS, MediaUriStore.encode(imageUris))
                .putExtra(EXTRA_PHOTO_COLUMNS, photoColumnCount)
        )
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        confirmExitIfNeeded()
    }

    companion object {
        const val EXTRA_IMAGE_URIS = "image_uris"
        const val EXTRA_PHOTO_COLUMNS = "photo_columns"
        private const val DEFAULT_PHOTO_COLUMNS = 3
        private const val MIN_PHOTO_COLUMNS = 2
        private const val MAX_PHOTO_COLUMNS = 5
        private const val MAX_PICKED_IMAGES = 50
    }
}
