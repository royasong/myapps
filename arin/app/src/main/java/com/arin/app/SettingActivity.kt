package com.arin.app
//TEST

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
import android.view.View
import android.widget.Button
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.arin.app.databinding.ActivitySettingBinding
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import yuku.ambilwarna.AmbilWarnaDialog

class SettingActivity : AppCompatActivity() {
    var TAG = "SETTING"
    val file_bg_color_ : String = "bg_color.txt"
    val file_btn_color_ : String = "btn_color.txt"
    lateinit var context_: Context
    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    lateinit var current_bg_image_: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context_ = getApplicationContext();
        //getActionBar()!!.setTitle("앱설정")
        setContentView(R.layout.activity_setting)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "장아린 전용 앱"
        toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.black))
        //Log.e(TAG, "save dir : " + getContext().getFilesDir().getPath().toString())
        current_bg_image_ = findViewById(R.id.imageView_bg)
        var bBgColor = findViewById<Button>(R.id.btn_bgColor)
        bBgColor!!.setOnClickListener(View.OnClickListener {
            Log.e(TAG, "bg color")
            openBgColorSelectView("bg");
        })
        var bBtnColor = findViewById<Button>(R.id.btn_buttonColor)
        bBtnColor!!.setOnClickListener(View.OnClickListener {
            Log.e(TAG, "button color")
            openBgColorSelectView("btn");
        })
        var bBgImage = findViewById<Button>(R.id.btn_bgImage)
        bBgImage!!.setOnClickListener(View.OnClickListener {
            Log.e(TAG, "bg image")
            initImageViewProfile();
        })
        var bClose = findViewById<Button>(R.id.btn_close)
        bClose!!.setOnClickListener(View.OnClickListener {
            var res = Intent(this@SettingActivity,MainActivity::class.java);
            res.putExtra("from", "setting");
            setResult(RESULT_OK , res)
            finish();
        })
        setImageViewImage(getContext().getFilesDir().getPath() + "/arin_bg.png")
        activityResultActivityRauncher()
    }
    fun openBgColorSelectView(pos : String) {
        val color = Color.parseColor("#00B700")
        AmbilWarnaDialog(this, color,
            object : AmbilWarnaDialog.OnAmbilWarnaListener {
                override fun onCancel(dialog: AmbilWarnaDialog?) {
                }

                // 색상 변경 시 처리 내용
                override fun onOk(dialog: AmbilWarnaDialog?, color: Int) {
                    Log.d(TAG, "button background changed " + color)//
                    //btn_change_bgimage_.setBackgroundColor(color)
                    storeFileUsingStream(pos,color.toString())
                    //btn_change_bgimage_.setTextColor(color)
                }
            }).show()
    }
    fun storeFileUsingStream(pos : String,color : String?) {
        // API 24 이상에서, MODE_PRIVATE 사용 안하면, SecurityException 발생
        if (pos == "bg") {
            getContext()!!.openFileOutput(file_bg_color_, Context.MODE_PRIVATE).use {
                it.write(color!!.toByteArray())
            }
        } else {
            getContext()!!.openFileOutput(file_btn_color_, Context.MODE_PRIVATE).use {
                it.write(color!!.toByteArray())
            }
        }
    }
    fun setImageViewImage(filepath : String) {
        val imgFile = File(filepath)
        if (imgFile.exists()) {
            val imgBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
            current_bg_image_.setImageBitmap(imgBitmap)
        }
    }
    fun activityResultActivityRauncher() {
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = checkNotNull(result.data)
                var currentImageUri  = intent.data
                try {
                    currentImageUri?.let {
                        if(Build.VERSION.SDK_INT < 28) {
                            val bitmap = MediaStore.Images.Media.getBitmap(
                                this.contentResolver,
                                currentImageUri
                            )
                            current_bg_image_?.setImageBitmap(bitmap)
                        } else {
                            val source = ImageDecoder.createSource(this.contentResolver, currentImageUri)
                            val bitmap = ImageDecoder.decodeBitmap(source)
                            current_bg_image_?.setImageBitmap(bitmap)
                            val back_dir_: String = getContext().getFilesDir().getPath()
                            Log.e(TAG, "save dir : " + back_dir_)//
                            saveBitmapAsFile(bitmap,back_dir_+"/arin_bg.png")
                        }
                    }
                } catch(e : Exception) {
                    Log.e(TAG, "ERROR " + e)//
                    e.printStackTrace()
                }
            }
        }
    }
    fun getContext(): Context {
        return context_
    }
    private fun saveBitmapAsFile(bitmap: Bitmap, filepath: String) {
        val file = File(filepath)
        var os: OutputStream? = null

        try {
            file.createNewFile()
            os = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            os.close()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
    private fun navigateGallery() {
        //val intent = Intent(this, SubActivity::class.java)
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        activityResultLauncher.launch(intent)
    }
    private fun showPermissionContextPopup() {
        Log.d(TAG, "navigateGalleryshowPermissionContextPopup")
        AlertDialog.Builder(this)
            .setTitle("권한이 필요합니다.")
            .setMessage("프로필 이미지를 바꾸기 위해서는 갤러리 접근 권한이 필요합니다.")
            .setPositiveButton("동의하기") { _, _ ->
                requestPermissions(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES), 1000)
            }
            .setNegativeButton("취소하기") { _, _ -> }
            .create()
            .show()
    }
    private fun initImageViewProfile() {
        Log.d(TAG, "change_bgimage.setOnClickListener execute")
        when {
            // 갤러리 접근 권한이 있는 경우
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            -> {
                Log.d(TAG, "ivProfile.setOnClickListener access gallary")
                navigateGallery()
            }

            // 갤러리 접근 권한이 없는 경우 & 교육용 팝업을 보여줘야 하는 경우
            shouldShowRequestPermissionRationale(android.Manifest.permission.READ_MEDIA_IMAGES)
            -> {
                Log.d(TAG, "ivProfile.setOnClickListener can't access gallary")
                showPermissionContextPopup()
            }
            // 권한 요청 하기(requestPermissions) -> 갤러리 접근(onRequestPermissionResult)
            else -> {
                Log.d(TAG, "ivProfile.setOnClickListener requestPermissions")
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES),
                    1000

                )
            }
        }
    }
    fun registerBackgroundBgPicker() {
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = checkNotNull(result.data)
                var currentImageUri  = intent.data
                try {
                    currentImageUri?.let {
                        if(Build.VERSION.SDK_INT < 28) {
                            val bitmap = MediaStore.Images.Media.getBitmap(
                                this.contentResolver,
                                currentImageUri
                            )
                            current_bg_image_?.setImageBitmap(bitmap)
                        } else {
                            val source = ImageDecoder.createSource(this.contentResolver, currentImageUri)
                            val bitmap = ImageDecoder.decodeBitmap(source)
                            current_bg_image_?.setImageBitmap(bitmap)
                            val back_dir_: String = getContext().getFilesDir().getPath()
                            Log.e(TAG, "save dir : " + back_dir_)//
                            saveBitmapAsFile(bitmap,back_dir_+"/arin_bg.png")
                        }
                    }
                } catch(e : Exception) {
                    Log.e(TAG, "ERROR " + e)//
                    e.printStackTrace()
                }
            }
        }
    }
}