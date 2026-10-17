package com.epubreader.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var library: LinearLayout
    private val books = linkedMapOf<String, Uri>()

    private val preferences by lazy {
        getSharedPreferences("epub_library", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 24)
            setBackgroundColor(0xFF121212.toInt())
        }

        val title = TextView(this).apply {
            text = "EpubReader"
            textSize = 30f
            setTextColor(0xFFFFFFFF.toInt())
        }

        val subtitle = TextView(this).apply {
            text = "Tu biblioteca personal"
            textSize = 16f
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(0, 8, 0, 24)
        }

        val importButton = Button(this).apply {
            text = "＋ Importar libro EPUB"
            setOnClickListener { selectBook() }
        }

        library = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(library)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(importButton)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        loadBooks()
        showBooks()
    }

    private fun selectBook() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/epub+zip",
                    "application/octet-stream",
                    "application/zip"
                )
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        startActivityForResult(intent, 100)
    }

    @Deprecated("Compatibilidad con el selector de documentos")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != 100 || resultCode != RESULT_OK) return

        val uri = data?.data ?: return
        val name = getFileName(uri) ?: "Libro EPUB"

        if (!name.trim().lowercase().endsWith(".epub")) {
            Toast.makeText(
                this,
                "Selecciona un archivo con extensión .epub",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val permissionFlags = data.flags and
                Intent.FLAG_GRANT_READ_URI_PERMISSION

            contentResolver.takePersistableUriPermission(
                uri,
                permissionFlags
            )

            books[name] = uri
            saveBooks()
            showBooks()

            Toast.makeText(
                this,
                "Libro guardado en la biblioteca",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "No se pudo guardar el acceso al libro: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )
                    if (index >= 0) return cursor.getString(index)
                }
            }
        }

        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun saveBooks() {
        val editor = preferences.edit().clear()
        editor.putInt("book_count", books.size)

        books.entries.forEachIndexed { index, entry ->
            editor.putString("name_$index", entry.key)
            editor.putString("uri_$index", entry.value.toString())
        }

        editor.apply()
    }

    private fun loadBooks() {
        books.clear()

        val count = preferences.getInt("book_count", 0)

        for (index in 0 until count) {
            val name = preferences.getString("name_$index", null)
            val uriString = preferences.getString("uri_$index", null)

            if (name != null && uriString != null) {
                books[name] = Uri.parse(uriString)
            }
        }
    }

    private fun showBooks() {
        library.removeAllViews()

        if (books.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Todavía no tienes libros.\n\nImporta un archivo EPUB para añadirlo a tu biblioteca."
                textSize = 16f
                setTextColor(0xFFBBBBBB.toInt())
                setPadding(0, 24, 0, 16)
            }

            library.addView(empty)
            return
        }

        for (name in books.keys) {
            val book = TextView(this).apply {
                text = "📖  $name"
                textSize = 17f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(16, 20, 16, 20)
                setBackgroundColor(0xFF242424.toInt())
            }

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            params.bottomMargin = 12
            library.addView(book, params)
        }
    }
}
