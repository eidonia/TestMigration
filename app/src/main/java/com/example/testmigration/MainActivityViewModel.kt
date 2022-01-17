package com.example.testmigration

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.*
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.lang.Exception
import java.nio.channels.FileChannel

class MainActivityViewModel(application: Application): AndroidViewModel(application) {

    private val _images = MutableLiveData<List<MediaStoreImage>>()
    val images: LiveData<List<MediaStoreImage>> get() = _images

    private var contentObserver: ContentObserver? = null

    private var pendingDeleteImage: MediaStoreImage? = null

    private val _permissionNeededForDelete = MutableLiveData<IntentSender?>()
    val permissionNeededForDelete: LiveData<IntentSender?> = _permissionNeededForDelete

    private val _migrationDone = MutableLiveData<Boolean>()
    val migrationDone: LiveData<Boolean> = _migrationDone

    fun loadImages(uri: String) {
        viewModelScope.launch {
            val imageList = queryImage(uri)
            _images.postValue(imageList)

            if (contentObserver == null) {
                contentObserver = getApplication<Application>().contentResolver.registerObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                ) {
                    loadImages(uri)
                }
            }
        }
    }

    private suspend fun queryImage(uri: String): List<MediaStoreImage> {
        val images = mutableListOf<MediaStoreImage>()
        val plop = uri.substringAfter("/0/")
        Log.d("blop", plop)
        var folderPath = "%$plop%"

        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.RELATIVE_PATH,
                    MediaStore.Files.FileColumns.MEDIA_TYPE
                )

                val selection = "(${MediaStore.Files.FileColumns.RELATIVE_PATH} like ?) AND (${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

                val selectionArgs = arrayOf(folderPath)

                getApplication<Application>().contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val pathColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val displayName = cursor.getString(nameColumn)
                        val path = cursor.getString(pathColumn)

                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        val image = MediaStoreImage(
                            id,
                            displayName,
                            path,
                            contentUri,
                            MediaStore.Files.FileColumns.SIZE,
                            MediaStore.Files.FileColumns.WIDTH,
                            MediaStore.Files.FileColumns.HEIGHT
                        )
                        images += image
                    }
                }
            } else {
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.DATA
                )

                val selection = "${MediaStore.Files.FileColumns.DATA} like ?"

                val selectionArgs = arrayOf(folderPath)

                getApplication<Application>().contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val pathColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val displayName = cursor.getString(nameColumn)
                        val path = cursor.getString(pathColumn)

                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        val image = MediaStoreImage(
                            id,
                            displayName,
                            path,
                            contentUri,
                            MediaStore.Files.FileColumns.SIZE,
                            MediaStore.Files.FileColumns.WIDTH,
                            MediaStore.Files.FileColumns.HEIGHT
                        )
                        images += image
                    }
                }
            }
        }
        Log.d("blop", "${images.size}")
    return images
    }

    private fun ContentResolver.registerObserver(
        uri: Uri,
        observer: (selfChange: Boolean) -> Unit
    ): ContentObserver {
        val contentObserver = object : ContentObserver(Handler()) {
            override fun onChange(selfChange: Boolean) {
                observer(selfChange)
            }
        }
        registerContentObserver(uri, true, contentObserver)
        return contentObserver
    }

    fun deleteImage(image: MediaStoreImage) {
        viewModelScope.launch {
            performDeleteImage(image)
        }
    }

    private suspend fun performDeleteImage(image: MediaStoreImage) {
        withContext(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.delete(
                    image.contentUri,
                    "${MediaStore.Images.Media._ID} = ?",
                    arrayOf(image.id.toString())
                )
            }catch (securityException: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val recoverableSecurityException = securityException as RecoverableSecurityException

                    pendingDeleteImage = image
                    _permissionNeededForDelete.postValue(
                        recoverableSecurityException.userAction.actionIntent.intentSender
                    )
                } else {
                    throw  securityException
                }
            }
        }
    }

    fun deletePendingImage() {
        pendingDeleteImage?.let { image ->
            pendingDeleteImage = null
            deleteImage(image)
        }
    }


    fun migrateData(pathSource: String = "", source: File, path: String, FILE_TYPE: String) {
        viewModelScope.launch {
            if (FILE_TYPE == "image") {
                copyImage(pathSource, source, path)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    copyFile(source, path)
                }
            }
        }
    }

    private suspend fun copyFile(sourceFile: File, path: String) {
        withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var outputStream: OutputStream? = null
            val fileDest = File(path, sourceFile.name)

            try {
                inputStream = FileInputStream(sourceFile)
                outputStream = FileOutputStream(fileDest)

                val buffer = ByteArray(1024)
                var length: Int = inputStream.read(buffer)
                while (length > 0) {
                    outputStream.write(buffer, 0, length)
                }
            } catch (e: Exception) {
                Log.e("error", "error tototo : ${e.localizedMessage}")
            } finally {
                inputStream?.close()
                outputStream?.close()
            }
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch {
            deleteSuspenFile(file)
        }
    }

    private suspend fun deleteSuspenFile(file: File) {
        withContext(Dispatchers.IO) {
            try {
                file.delete()
            }catch (e: Exception){
                Log.e("uriri", "error roro : ${e.localizedMessage}")
            }

        }
    }

    private suspend fun copyImage(pathSource: String, source: File, path: String) {
        var listUris = mutableListOf<Uri>()
        for (img in queryImage(pathSource)) {
            listUris += img.contentUri
        }

        for (l in listUris) {
            Log.d("blop", "${l.path}")
        }

        withContext(Dispatchers.IO) {
            for (sourceFile in source.listFiles()) {
                val fileCreate = File(path, sourceFile.name)
                Log.d("uriFile", "${fileCreate.path} + ${fileCreate.exists()}")
                if (!fileCreate.exists()) {
                    var source: FileChannel? = null
                    var dest: FileChannel? = null
                    try {
                        source = FileInputStream(sourceFile).channel
                        dest = FileOutputStream(fileCreate).channel
                        dest.transferFrom(source, 0, source.size())

                        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also {
                            val f = File(fileCreate.path)
                            it.data = Uri.fromFile(f)
                            getApplication<Application>().sendBroadcast(it)
                        }
                    } catch (fileNotFoundExc: FileNotFoundException) {
                        Log.e("errorFile", "error : ${fileNotFoundExc.localizedMessage}")
                    } finally {
                        source?.close()
                        dest?.close()
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val editPendingIntent = MediaStore.createDeleteRequest(
                getApplication<Application>().contentResolver,
                listUris
            )
            _permissionNeededForDelete.postValue(editPendingIntent.intentSender)
        } else {
            for (sourceFile in source.listFiles()) {
                sourceFile.delete()
            }
        }
        _migrationDone.postValue(true)
    }

    fun deleteDocumentFile(applicationContext: Context, image: MediaStoreImage) {
        viewModelScope.launch {
            deleteImg(applicationContext, image)
        }
    }

    private suspend fun deleteImg(applicationContext: Context, image: MediaStoreImage) {
        withContext(Dispatchers.IO) {
            Log.d("fileUri", image.contentUri.toString())


            val docu = DocumentFile.fromSingleUri(applicationContext, image.contentUri)
            //docu?.delete()

        }
    }
}