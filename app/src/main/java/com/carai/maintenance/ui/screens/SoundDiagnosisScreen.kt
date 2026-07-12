package com.carai.maintenance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.carai.maintenance.audio.AudioCapture
import com.carai.maintenance.audio.ModelStatus
import com.carai.maintenance.audio.SoundClassifierProvider
import com.carai.maintenance.audio.SoundResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AnalysisMode { AI, FREQUENCY }

@Composable
fun SoundDiagnosisScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SoundResult?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // 앱 시작 시 assets에 AI 모델이 실제로 로드 가능한지 한 번 확인
    val modelStatus = remember { SoundClassifierProvider.checkStatus(context) }
    val aiAvailable = modelStatus is ModelStatus.Found

    var mode by remember { mutableStateOf(if (aiAvailable) AnalysisMode.AI else AnalysisMode.FREQUENCY) }

    val capture = remember { AudioCapture() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text("소리 진단", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "이상한 소리가 나는 부위에 마이크를 가까이 대고 녹음하세요.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))

        // 분석 방식 선택 토글
        ModeSelector(
            mode = mode,
            aiAvailable = aiAvailable,
            onModeChange = { mode = it }
        )
        Spacer(Modifier.height(8.dp))
        ModelStatusText(modelStatus)

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@Button
                    }
                    if (!isRecording) {
                        isRecording = true
                        result = null
                        scope.launch {
                            val started = withContext(Dispatchers.IO) { capture.start() }
                            if (!started) {
                                isRecording = false
                                return@launch
                            }
                            // 약 1.5초 분량(약 16 청크)을 모아 하나로 합쳐서 분석
                            val collected = ArrayList<Double>()
                            withContext(Dispatchers.IO) {
                                repeat(16) {
                                    capture.readChunk()?.let { chunk -> collected.addAll(chunk.toList()) }
                                }
                                capture.stop()
                            }
                            val samples = collected.toDoubleArray()
                            if (samples.isNotEmpty()) {
                                val classifier = if (mode == AnalysisMode.AI) {
                                    SoundClassifierProvider.createAiClassifier(context)
                                        ?: SoundClassifierProvider.createHeuristicClassifier()
                                } else {
                                    SoundClassifierProvider.createHeuristicClassifier()
                                }
                                result = withContext(Dispatchers.Default) {
                                    classifier.classify(samples, AudioCapture.SAMPLE_RATE)
                                }
                            }
                            isRecording = false
                        }
                    }
                },
                modifier = Modifier.size(120.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRecording) Color(0xFFB3261E) else Color.Black,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isRecording) "녹음 및 분석 중..." else "탭하여 녹음 시작 (약 1.5초)",
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(32.dp))

        result?.let { r ->
            SoundResultCard(r)
        }

        if (!hasPermission) {
            Spacer(Modifier.height(16.dp))
            Text(
                "마이크 권한이 필요합니다.",
                color = Color(0xFFB3261E),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.weight(1f))
        Text(
            "* 주파수 분석 모드는 경험칙 기반 참고용입니다. 실제 정밀 고장 진단은 정비소 점검을 권장합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9E9E9E)
        )
    }
}

@Composable
private fun ModeSelector(
    mode: AnalysisMode,
    aiAvailable: Boolean,
    onModeChange: (AnalysisMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF2F2F2), RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        ModeChip(
            label = "AI 모델",
            selected = mode == AnalysisMode.AI,
            enabled = aiAvailable,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(AnalysisMode.AI) }
        )
        ModeChip(
            label = "주파수 분석",
            selected = mode == AnalysisMode.FREQUENCY,
            enabled = true,
            modifier = Modifier.weight(1f),
            onClick = { onModeChange(AnalysisMode.FREQUENCY) }
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) Color.Black else Color.Transparent
    val fg = when {
        selected -> Color.White
        !enabled -> Color(0xFFBDBDBD)
        else -> Color.Black
    }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(10.dp))
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ModelStatusText(status: ModelStatus) {
    when (status) {
        is ModelStatus.Found -> {
            Column {
                Text(
                    "AI 모델 사용 가능: ${status.modelFile}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    status.debugInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9E9E9E)
                )
                status.warning?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB3261E))
                }
            }
        }
        is ModelStatus.LoadFailed -> {
            Text(
                "AI 모델 로드 실패 (${status.modelFile}): ${status.reason}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB3261E)
            )
        }
        ModelStatus.NotFound -> {
            Text(
                "assets 폴더에 sound_model.tflite / labels.txt가 없습니다. 주파수 분석 모드로 동작합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9E9E9E)
            )
        }
    }
}

@Composable
private fun SoundResultCard(result: SoundResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F2F2))
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                result.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(result.detail, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (result.dominantFrequencyHz > 0.0) {
                    InfoStat("주파수", "${result.dominantFrequencyHz.toInt()} Hz")
                }
                InfoStat("신뢰도", "${(result.confidence * 100).toInt()}%")
            }
        }
    }
}

@Composable
private fun InfoStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
