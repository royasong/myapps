package com.arin.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EditSmsActivity : AppCompatActivity() {
    var TAG = "EDITSMS"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()
        setContentView(R.layout.activity_edit_sms)
        setEditSmsTextContent()
        var bSmsapply = findViewById<Button>(R.id.btn_smsApply)
        bSmsapply!!.setOnClickListener(View.OnClickListener {
            Log.e(TAG, "user sms text apply")
            if(EditSmsTextApply()) {
                var intent = Intent(this@EditSmsActivity,MainActivity::class.java);
                intent.putExtra("from", "editsms");
                setResult(RESULT_OK , intent)
                finish()
            }
        })
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "장아린 전용 앱"
        toolbar.setTitleTextColor(ContextCompat.getColor(this, android.R.color.black))
    }

    fun setEditSmsTextContent() {
        var smsArray = SmsTextValue.getInstance().getText();
        findViewById<EditText>(R.id.editText_sms0).setText(smsArray[0].toString())
        findViewById<EditText>(R.id.editText_sms1).setText(smsArray[1].toString())
        findViewById<EditText>(R.id.editText_sms2).setText(smsArray[2].toString())
        findViewById<EditText>(R.id.editText_sms3).setText(smsArray[3].toString())
    }
    fun EditSmsTextApply():Boolean {
        var sms0 = findViewById<EditText>(R.id.editText_sms0)
        var sms1 = findViewById<EditText>(R.id.editText_sms1)
        var sms2 = findViewById<EditText>(R.id.editText_sms2)
        var sms3 = findViewById<EditText>(R.id.editText_sms3)
        if(sms0.text.toString().isEmpty() ||
            sms1.text.toString().isEmpty()||
            sms2.text.toString().isEmpty()||
            sms3.text.toString().isEmpty()) {
            Toast.makeText( getApplicationContext(),"빈칸이 있어요..", Toast.LENGTH_SHORT).show()
            return false;
        }
        var smsArray = arrayOf(sms0.text.toString(),
                                  sms1.text.toString(),
                                  sms2.text.toString(),
                                  sms3.text.toString())

        SmsTextValue.getInstance().setSmsText(smsArray)
        return true;
    }
}