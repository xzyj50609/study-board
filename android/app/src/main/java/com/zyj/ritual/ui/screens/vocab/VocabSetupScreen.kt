package com.zyj.ritual.ui.screens.vocab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyj.ritual.data.repository.VocabRepository
import com.zyj.ritual.domain.vocab.VocabConfig
import com.zyj.ritual.ui.components.PrimaryPill
import com.zyj.ritual.ui.theme.RitualColors
import com.zyj.ritual.ui.theme.RitualTypography
import kotlinx.coroutines.launch

@Composable
fun VocabSetupScreen(
    currentConfig: VocabConfig,
    vocabRepository: VocabRepository,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    var bookName by remember { mutableStateOf(currentConfig.bookName) }
    var totalWordsStr by remember { mutableStateOf(currentConfig.totalWords.toString()) }
    var initialDoneStr by remember { mutableStateOf(currentConfig.initialDone.toString()) }
    var dailyWordsStr by remember { mutableStateOf(currentConfig.dailyWords.toString()) }
    var examDate by remember { mutableStateOf(currentConfig.examDate) }

    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = RitualColors.bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "背单词设置",
                style = RitualTypography.headlineMedium,
                color = RitualColors.onBg,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = RitualColors.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("词书名称", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RitualColors.onBg)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = bookName,
                        onValueChange = { bookName = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("词书总词数", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RitualColors.onBg)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = totalWordsStr,
                        onValueChange = { totalWordsStr = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("起点存量（以前已背）", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RitualColors.onBg)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = initialDoneStr,
                        onValueChange = { initialDoneStr = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("每日计划新词", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RitualColors.onBg)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = dailyWordsStr,
                        onValueChange = { dailyWordsStr = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("考试日期 (YYYY-MM-DD)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RitualColors.onBg)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = examDate,
                        onValueChange = { examDate = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            PrimaryPill(
                text = if (isSaving) "保存中…" else "保存背词设置",
                enabled = !isSaving,
                onClick = {
                    val total = totalWordsStr.toIntOrNull() ?: currentConfig.totalWords
                    val init = initialDoneStr.toIntOrNull() ?: currentConfig.initialDone
                    val daily = dailyWordsStr.toIntOrNull() ?: currentConfig.dailyWords

                    val newConfig = currentConfig.copy(
                        bookName = bookName.ifBlank { currentConfig.bookName },
                        totalWords = total,
                        initialDone = init,
                        dailyWords = daily,
                        examDate = examDate.ifBlank { currentConfig.examDate }
                    )

                    isSaving = true
                    scope.launch {
                        vocabRepository.saveConfig(newConfig)
                        isSaving = false
                        onSaved()
                    }
                }
            )
        }
    }
}
