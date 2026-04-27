package com.arin.app

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.PrintWriter

class SmsTextValue {

    var TAG = "SMSAPI"
    lateinit var context_: Context
    lateinit var smsTextFile_ : String
    var default_smsTextArray:Array<String> = arrayOf("엄마 나 일어났어",
                                     "엄마 어디야?",
                                     "엄마 휴대폰 시간 더 줘",
                                     "엄마 사랑해")
    companion object {
        private var instance: SmsTextValue? = null

        fun getInstance() =
            instance ?: SmsTextValue().also {
                instance = it
            }
    }
    fun initize(path: String , context: Context) {
        var file = path + "/sms" + ".txt"
        Log.d(TAG, "smsText file " + file)
        smsTextFile_ = file
        context_ = context
    }
    fun getText() : Array<out String?> {
        val imgFile = File(smsTextFile_)
        if (!imgFile.exists()) {
            return default_smsTextArray;
        }
        val smsArray = arrayOfNulls<String>(4)

        var reader: BufferedReader? = null
        var i : Int = 0
        try {
            reader = BufferedReader(FileReader(smsTextFile_))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                // Process each line
                Log.d(TAG, "smsText " + i + " : " + line)
                smsArray[i++] = line.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "smsText ERROR ${e.message}")
        } finally {
            try {
                reader?.close()
            } catch (e: Exception) {
                Log.e(TAG, "smsText ERROR: while closing the file: ${e.message}")
            }
        }
        if (i == 0)
            return default_smsTextArray;
        else
            return smsArray
    }
    fun setSmsText(smsArr :Array<String>) {
        val fos = context_.openFileOutput("sms" + ".txt", Context.MODE_PRIVATE)
        val out = PrintWriter(fos)
        for (s in smsArr) {
            out.println(s);
        }
        out.close();
    }
}