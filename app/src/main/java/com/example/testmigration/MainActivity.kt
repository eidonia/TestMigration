package com.example.testmigration

import android.Manifest
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.testmigration.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val REQUEST_PERMISSION = 123
    private val viewModel: MainActivityViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initLayout()

        grantPermission()

        val galleryAdapter = GalleryAdapter() { image ->
            deleteImage(image)
        }

        binding.gallery.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = galleryAdapter
        }

        viewModel.images.observe(this, {
            Log.d("blop", "${it.size}")
            galleryAdapter.addList(it)
        })

        viewModel.permissionNeededForDelete.observe(this, {
            it?.let {
                deleteLauncher.launch(IntentSenderRequest.Builder(it).build())
            }
        })

        binding.btnChooseFile.setOnClickListener {
            onOpenDocumentLauncher.launch(
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                })
        }

        binding.btnChangePage.setOnClickListener {
            startActivity(Intent(this, MigratePage::class.java))
        }

        binding.btnChangeFile.setOnClickListener {
            startActivity(Intent(this, MigrateOneFilePage::class.java))
        }

        binding.btnDeleteDoc.setOnClickListener {
            onOpenFileToDel.launch(arrayOf())
        }

    }

    private fun deleteImage(image: MediaStoreImage) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete ?")
            .setMessage("Delete ${image.displayName}")
            .setPositiveButton("Delete") { _: DialogInterface, _:Int ->
                //viewModel.deleteImage(image)
                viewModel.deleteDocumentFile(applicationContext, image)
            }
            .setNegativeButton("Nop") { dialog: DialogInterface, _:Int ->
                dialog.dismiss()
            }
            .show()
    }

    private fun initLayout() {
        binding.txtchoose.visibility = View.VISIBLE
        binding.gallery.visibility = View.GONE
    }

    private val onOpenFileToDel = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        Log.d("onOpenFileToDel", "$uri")
    }



    private val onOpenDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data.also { intent ->
                intent?.data.let { uri ->
                    Log.d("fileUri", "uriDoc : $uri")
                    val docutree = DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        DocumentsContract.getTreeDocumentId(uri)
                    )

                    Log.d("fileUri", "path : $docutree")

                    val path = GetPath().getPath(this, docutree)

                    Log.d("fileUri", "path : $path")
                    showImages(path)

                    Log.d("fileUri", "path : ${File(path).listFiles().size}")

                    val takeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    applicationContext.contentResolver.takePersistableUriPermission(
                        uri!!,
                        takeFlags
                    )
                }

            }
        }
    }

    private fun showImages(path: String) {
        viewModel.loadImages(path)
        binding.txtchoose.visibility = View.GONE
        binding.gallery.visibility = View.VISIBLE
    }

    private fun grantPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_PERMISSION
            )
        }
    }

    val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            viewModel.deletePendingImage()
        }
    }
}