package com.example.mycard

import android.content.Context
import android.os.Bundle
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

    // 앱 시작 시 저장된 데이터 불러오기
    LaunchedEffect(Unit) {
        memoText = prefs.getString("memo", "") ?: ""
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
                text = "카드그룹 (한 줄에 하나, 쉼표 구분: 발신번호,그룹ID[,카드끝4자리])",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = cardGroupText,
                onValueChange = { cardGroupText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("예:\n18001111,하나,1234\n18001111,하나,5678\n15776000,현대,9012") }
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
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        // SharedPreferences에 데이터 저장
                        prefs.edit()
                            .putString("memo", memoText)
                            .putString("cardGroup", cardGroupText)
                            .apply()
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