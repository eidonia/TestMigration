package com.example.testmigration

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.testmigration.databinding.ActivityMigratePageBinding
import java.io.File

class MigratePage : AppCompatActivity() {

    private lateinit var binding: ActivityMigratePageBinding
    private var isCreated = false
    private lateinit var path: String
    private val viewModel: MainActivityViewModel by viewModels()

    private val REQUEST_EXTERNAL_STORAGE = 1;
    private val PERMISSIONS_STORAGE = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMigratePageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initUI()

        val galleryAdapter = GalleryAdapter() {
            viewModel.deleteImage(it)
        }

        binding.rvList.apply {
            layoutManager = GridLayoutManager(this@MigratePage, 3)
            adapter = galleryAdapter
        }

        binding.btnCreateFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }

            createFolderLauncher.launch(intent)

        }

        viewModel.migrationDone.observe(this, {
            if (it) {
                binding.txtMigrationDone.visibility = View.VISIBLE
                showImages()
            }
        })

        viewModel.images.observe(this, {
            galleryAdapter.addList(it)
        })

        viewModel.permissionNeededForDelete.observe(this, {
            it?.let {
                deleteFiles.launch(IntentSenderRequest.Builder(it).build())
            }
        })

        binding.btnMigrateFolder.setOnClickListener {
            if (!isCreated) {
                Toast.makeText(this, "Create a folder first", Toast.LENGTH_LONG).show()
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                        flags =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    }
                    migrateFolder.launch(intent)
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        PERMISSIONS_STORAGE,
                        REQUEST_EXTERNAL_STORAGE
                    )
                }

            }
        }

    }

    private fun showImages() {
        viewModel.loadImages(path)
    }

    private fun initUI() {
        binding.txtFolderCreate.visibility = View.INVISIBLE
        binding.txtMigrationDone.visibility = View.INVISIBLE
    }

    private val createFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data.also { intent ->
                intent?.data.let { uri ->
                    uri?.let {
                        val docutree = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                        path = GetPath().getPath(this, docutree)
                        val takeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        applicationContext.contentResolver.takePersistableUriPermission(
                            uri,
                            takeFlags
                        )
                        binding.txtFolderCreate.visibility = View.VISIBLE
                        binding.txtFolderCreate.text =
                            binding.txtFolderCreate.text.toString() + uri.lastPathSegment
                        isCreated = true
                    }
                }

            }
        }
    }

    private val migrateFolder = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data.let { uri ->
                val docutree = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                val pathSource = GetPath().getPath(this, docutree)
                val source = File(pathSource)
                Log.d("Blop", source.path)
                for (f in source.listFiles()) {
                    Log.d("Blop", f.path)
                }
                viewModel.migrateData(pathSource, source, path, "image")

            }
        }
    }

    private val deleteFiles = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {

        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("blop", "blop")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }
        migrateFolder.launch(intent)
    }
}