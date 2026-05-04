package com.arin.app

import android.app.AlertDialog
import android.app.ComponentCaller
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arin.app.ui.theme.ComarinappTheme
import java.io.File
import java.io.FileNotFoundException
import java.io.BufferedReader
import java.io.FileReader
import kotlin.math.log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat

//import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    var TAG = "MAIN"
    lateinit var context_: Context
    var mom_number_: String = "01095444074"
    var dad_number_: String = "01093597899"
    lateinit var view_bg_image_: ImageView
    lateinit var smsText : SmsTextValue

    private lateinit var resultLauncher: ActivityResultLauncher<Intent>

    private fun setResultSignUp(){
        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
            if (result.resultCode == RESULT_OK) {
                val name = result.data?.getStringExtra("from") ?: ""
                Log.d(TAG, "setResultSignUp " + name)
                if(name == "editsms") {
                    setSmsTextValue()
                } else if (name == "setting") {
                    setImageViewImage(getContext().getFilesDir().getPath() + "/arin_bg.png")
                    setBgColor()
                    setBtnColor()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResultSignUp()
        setContentView(R.layout.main)
        context_ = getApplicationContext();
        SmsTextValue.getInstance().initize(getContext().getFilesDir().getPath() , context_)
        view_bg_image_ = findViewById(R.id.bg)
        setImageViewImage(getContext().getFilesDir().getPath() + "/arin_bg.png")
        setBgColor()
        setBtnColor()
        setCallButton()
        setSmsTextValue()

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "장아린 전용 앱"
        toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.black))
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_option, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item?.itemId) {
            R.id.menu_change_bg -> {
                val intent = Intent(this, SettingActivity::class.java)
                resultLauncher.launch(intent)
                return true
            }
            R.id.menu_change_sms_text -> {
                val intent = Intent(this, EditSmsActivity::class.java)
                resultLauncher.launch(intent)
                return true
            }
            R.id.menu_image_library -> {
                val image = ImageView(this)
                image.setImageResource(R.drawable.library_card);
                val builder: AlertDialog.Builder =
                    AlertDialog.Builder(this)
                builder.setView(image);
                builder.create().show();
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    fun setBgColor() {
        var main = findViewById(R.id.main_layout) as LinearLayout
        var color = getColor("bg")
        if(!color.isNullOrBlank()) {
            //Toast.makeText(applicationContext, color, Toast.LENGTH_SHORT).show()
            main.setBackgroundColor(color.toInt());
     //       var btn = findViewById<Button>(R.id.btn_sms1)
     //       btn.setBackgroundColor(color.toInt())
        }
    }
    fun setBtnColor() {
        var color = getColor("btn")
        if(!color.isNullOrBlank()) {
            //Toast.makeText(applicationContext, color, Toast.LENGTH_SHORT).show()
            //main.setBackgroundColor(color.toInt());
            var btn = findViewById<Button>(R.id.btn_sms0)
            btn.setTextColor(color.toInt())
            btn = findViewById<Button>(R.id.btn_sms1)
            btn.setTextColor(color.toInt())
            btn = findViewById<Button>(R.id.btn_sms2)
            btn.setTextColor(color.toInt())
            btn = findViewById<Button>(R.id.btn_sms3)
            btn.setTextColor(color.toInt())
            btn = findViewById<Button>(R.id.btn_call2mom)
            btn.setTextColor(color.toInt())
            btn = findViewById<Button>(R.id.btn_call2dad)
            btn.setTextColor(color.toInt())
        }
    }
    fun setCallButton() {
        var calldad = findViewById<Button>(R.id.btn_call2dad)
        calldad!!.setOnClickListener(View.OnClickListener {
            call(dad_number_)
        })
        var callmom = findViewById<Button>(R.id.btn_call2mom)
        callmom!!.setOnClickListener(View.OnClickListener {
            call(mom_number_)
        })
    }
    fun call(phonenum: String) {
        var intent = Intent(Intent.ACTION_CALL)
        intent.data = Uri.parse("tel:" + phonenum)
        startActivity(intent)
    }
    fun sendSms(message: String) {
        val SmsManager = SmsManager.getDefault()
        SmsManager.sendTextMessage(mom_number_, null, message + "[by mom app]", null, null)
        Toast.makeText(
            this@MainActivity,
            "예쁜 엄마에게 " + message + "라고 보라고 보냈어요",
            Toast.LENGTH_SHORT
        ).show()
    }
    fun setSmsTextValue() {
        var smsArray = SmsTextValue.getInstance().getText();
        var btn_sms0 = findViewById<Button>(R.id.btn_sms0)
        btn_sms0.setText(smsArray[0].toString())
        btn_sms0!!.setOnClickListener(View.OnClickListener {
            sendSms(btn_sms0.getText().toString())
        })

        var btn_sms1 = findViewById<Button>(R.id.btn_sms1)
        btn_sms1.setText(smsArray[1].toString())
        btn_sms1!!.setOnClickListener(View.OnClickListener {
            sendSms(btn_sms1.getText().toString())
        })
        var btn_sms2 = findViewById<Button>(R.id.btn_sms2)
        btn_sms2.setText(smsArray[2].toString())
        btn_sms2!!.setOnClickListener(View.OnClickListener {
            sendSms(btn_sms2.getText().toString())
        })
        var btn_sms3 = findViewById<Button>(R.id.btn_sms3)
        btn_sms3.setText(smsArray[3].toString())
        btn_sms3!!.setOnClickListener(View.OnClickListener {
            sendSms(btn_sms3.getText().toString())
        })
    }
    fun getColor(pos : String) : String {
        var fname = ""
        if(pos == "bg") {
            fname = "bg_color.txt";
        } else {
            fname = "btn_color.txt";
        }
        try {
            val inFs = getContext()!!.openFileInput(fname)

            val txt = ByteArray(inFs.available()) //byte[]형의 변수 txt를 선언
            inFs.read(txt) //읽어온 데이터를 저장
            val str = String(txt) //txt를 문자열로 변환
            //Toast.makeText(applicationContext, str, Toast.LENGTH_SHORT).show()
            Log.d(TAG, "color value " + str)//
            inFs.close()
            return str;
        } catch (e : FileNotFoundException) {
            e.printStackTrace();
        }
        return ""
    }

    fun setImageViewImage(filepath : String) {
        val imgFile = File(filepath)
        if (imgFile.exists()) {
            val imgBitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
            view_bg_image_.setImageBitmap(imgBitmap)
        }
    }
    fun getContext(): Context {
        return context_
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ComarinappTheme {
        Greeting("Android")
    }
}