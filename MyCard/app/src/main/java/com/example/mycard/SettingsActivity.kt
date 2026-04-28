package com.example.mycard

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mycard.ui.theme.MyCardTheme

private const val MEMO_FILE_NAME = "mycard_memo.txt"

private fun saveMemoToDocuments(context: Context, text: String) {
    try {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")

        // 기존 파일 삭제
        val cursor = resolver.query(
            collection,
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(MEMO_FILE_NAME, "${Environment.DIRECTORY_DOCUMENTS}%"),
            null
        )
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null)
            }
        }

        // 새 파일 생성
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, MEMO_FILE_NAME)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain")
            put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/")
        }
        val uri = resolver.insert(collection, values) ?: return
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun loadMemoFromDocuments(context: Context): String? {
    return try {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val cursor = resolver.query(
            collection,
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(MEMO_FILE_NAME, "${Environment.DIRECTORY_DOCUMENTS}%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val id = it.getLong(0)
                val uri = ContentUris.withAppendedId(collection, id)
                resolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                }
            } else null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyCardTheme {
                SettingsScreen(onSave = { finish() }, onCancel = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onSave: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("mycard_prefs", Context.MODE_PRIVATE)
    var memoText by remember { mutableStateOf("") }
    var cardGroupText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // Documents 파일 우선, 없으면 SharedPreferences에서 로드
        memoText = loadMemoFromDocuments(context)
            ?: prefs.getString("memo", "") ?: ""
        cardGroupText = prefs.getString("cardGroup", "") ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "카드그룹 (쉼표로 구분)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = cardGroupText,
                onValueChange = { cardGroupText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("예: 스타벅스\n쿠팡\n네이버") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "메모",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = memoText,
                onValueChange = { memoText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                placeholder = { Text("메모를 입력하세요...") }
            )
            Spacer(modifier = Modifier.height(24.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        prefs.edit()
                            .putString("memo", memoText)
                            .putString("cardGroup", cardGroupText)
                            .apply()
                        saveMemoToDocuments(context, memoText)
                        onSave()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("저장")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("취소")
                }
            }
        }
    }
}