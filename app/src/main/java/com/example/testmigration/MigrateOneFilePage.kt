package com.example.testmigration

import android.Manifest
import android.app.Activity
import android.content.ContentProvider
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.testmigration.databinding.ActivityMigrateOneFilePageBinding
import java.io.File

class MigrateOneFilePage : AppCompatActivity() {

    private lateinit var binding: ActivityMigrateOneFilePageBinding
    private val viewModel: MainActivityViewModel by viewModels()
    private  lateinit var path: String
    private var isCreated = false

    private val REQUEST_EXTERNAL_STORAGE = 1;
    private val PERMISSIONS_STORAGE = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMigrateOneFilePageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initUI()


        binding.btnCreateFolder.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            onCreateFolder.launch(intent)
        }

        binding.btnMigrateFile.setOnClickListener {
            if (!isCreated) {
                Toast.makeText(this, "Create a folder first", Toast.LENGTH_LONG).show()
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "*/*"
                    }
                    onMigrateFile.launch(intent)

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

    private fun initUI() {
        binding.txtFolderCreate.visibility = View.INVISIBLE
        binding.txtMigrationDone.visibility = View.INVISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
        }
        onMigrateFile.launch(intent)

    }

    private val onCreateFolder = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { uri ->
                val docutree = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                Log.d("uriTree", docutree.toString())
                path = GetPath().getPath(this, docutree)

                val takeFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION

                contentResolver.takePersistableUriPermission(
                    uri,
                    takeFlags
                )
                binding.txtFolderCreate.visibility = View.VISIBLE
                binding.txtFolderCreate.text = binding.txtFolderCreate.text.toString() + uri.lastPathSegment
                isCreated = true

            }
        }
    }

    private val onMigrateFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        it.data?.data.let {
            val sourcePath = GetPath().getPath(this, it)
            val file = File(sourcePath)
            Log.d("uriFile", file.name)

            viewModel.deleteFile(file)
        }
    }

}