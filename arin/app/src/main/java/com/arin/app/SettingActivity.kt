package com.arin.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import yuku.ambilwarna.AmbilWarnaDialog

class SettingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SETTING"
        private const val FILE_BG_COLOR = "bg_color.txt"
        private const val FILE_BTN_COLOR = "btn_color.txt"
        private const val PERMISSION_REQUEST_CODE = 1000
    }

    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var bgImageView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "장아린 전용 앱"
        toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.black))

        bgImageView = findViewById(R.id.imageView_bg)

        findViewById<Button>(R.id.btn_bgColor).setOnClickListener {
            openColorPickerDialog("bg")
        }
        findViewById<Button>(R.id.btn_buttonColor).setOnClickListener {
            openColorPickerDialog("btn")
        }
        findViewById<Button>(R.id.btn_bgImage).setOnClickListener {
            initImageViewProfile()
        }
        findViewById<Button>(R.id.btn_close).setOnClickListener {
            val result = Intent(this, MainActivity::class.java)
            result.putExtra("from", "setting")
            setResult(RESULT_OK, result)
            finish()
        }

        setBackgroundImage(filesDir.path + "/arin_bg.png")
        initActivityResultLauncher()
    }

    private fun openColorPickerDialog(pos: String) {
        val savedColor = loadSavedColor(if (pos == "bg") FILE_BG_COLOR else FILE_BTN_COLOR)
        AmbilWarnaDialog(this, savedColor, object : AmbilWarnaDialog.OnAmbilWarnaListener {
            override fun onCancel(dialog: AmbilWarnaDialog?) {}
            override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                Log.d(TAG, "색상 변경: $color")
                storeFileUsingStream(pos, color.toString())
            }
        }).show()
    }

    private fun loadSavedColor(filename: String): Int {
        return try {
            String(openFileInput(filename).readBytes()).toInt()
        } catch (e: Exception) {
            Color.parseColor("#00B700")
        }
    }

    private fun storeFileUsingStream(pos: String, color: String) {
        val filename = if (pos == "bg") FILE_BG_COLOR else FILE_BTN_COLOR
        openFileOutput(filename, Context.MODE_PRIVATE).use {
            it.write(color.toByteArray())
        }
    }

    private fun setBackgroundImage(filepath: String) {
        val imgFile = File(filepath)
        if (imgFile.exists()) {
            bgImageView.setImageBitmap(BitmapFactory.decodeFile(imgFile.absolutePath))
        }
    }

    private fun initActivityResultLauncher() {
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = checkNotNull(result.data).data
                try {
                    uri?.let {
                        val bitmap = if (Build.VERSION.SDK_INT < 28) {
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(contentResolver, uri)
                        } else {
                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri))
                        }
                        bgImageView.setImageBitmap(bitmap)
                        saveBitmapAsFile(bitmap, filesDir.path + "/arin_bg.png")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "이미지 로드 실패: $e")
                }
            }
        }
    }

    private fun saveBitmapAsFile(bitmap: Bitmap, filepath: String) {
        try {
            FileOutputStream(File(filepath)).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun navigateGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        activityResultLauncher.launch(intent)
    }

    private fun showPermissionContextPopup() {
        AlertDialog.Builder(this)
            .setTitle("권한이 필요합니다.")
            .setMessage("프로필 이미지를 바꾸기 위해서는 갤러리 접근 권한이 필요합니다.")
            .setPositiveButton("동의하기") { _, _ ->
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("취소하기") { _, _ -> }
            .create()
            .show()
    }

    private fun initImageViewProfile() {
        when {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED -> {
                navigateGallery()
            }
            shouldShowRequestPermissionRationale(android.Manifest.permission.READ_MEDIA_IMAGES) -> {
                showPermissionContextPopup()
            }
            else -> {
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            navigateGallery()
        }
    }
}
