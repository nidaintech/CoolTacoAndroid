package com.example.cooltacoandroid // Nama package harus sesuai [cite: 5]

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ActivityTaskItem(
    taskName: String,
    currentValue: Int,
    targetValue: Int
) {
    val progress = (currentValue.toFloat() / targetValue.toFloat()).coerceIn(0f, 1f)

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = taskName, style = MaterialTheme.typography.titleMedium)
            // Real-Time Feedback: Umpan balik visual instan saat sensor aktif [cite: 47, 48]
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}