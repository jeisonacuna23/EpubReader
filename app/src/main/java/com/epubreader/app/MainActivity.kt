package com.epubreader.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var library: LinearLayout
    private val books = linkedMapOf<String, Uri>()

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
            text = "＋  Importar libro EPUB"
            setOnClickListener { selectBook() }
        }

        library = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 0)
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
        showEmptyLibrary()
    }

    private fun showEmptyLibrary() {
        library.removeAllViews()

        val empty = TextView(this).apply {
            text = "Todavía no tienes libros.\n\nImporta un archivo EPUB para añadirlo a tu biblioteca."
            textSize = 16f
            setTextColor(0xFFBBBBBB.toInt())
            setPadding(0, 16, 0, 16)
        }

        library.addView(empty)
    }

    private fun selectBook() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/epub+zip",
                    "application/octet-stream"
                )
            )
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
        val name = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?: "Libro EPUB"

        if (!name.lowercase().endsWith(".epub")) {
            Toast.makeText(
                this,
                "Selecciona un archivo con extensión .epub",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        books[name] = uri
        showBooks()
    }

    private fun showBooks() {
        library.removeAllViews()

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
