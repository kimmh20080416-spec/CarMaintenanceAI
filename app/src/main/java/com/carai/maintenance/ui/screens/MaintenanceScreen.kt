package com.carai.maintenance.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carai.maintenance.data.EngineType
import com.carai.maintenance.data.MaintenanceRecommendation
import com.carai.maintenance.data.MaintenanceSchedule

@Composable
fun MaintenanceScreen() {
    var carModel by remember { mutableStateOf("") }
    var mileageText by remember { mutableStateOf("") }
    var engineType by remember { mutableStateOf(EngineType.GASOLINE) }
    var recommendations by remember { mutableStateOf<List<MaintenanceRecommendation>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text("정비 추천", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "차종과 주행거리를 입력하면 교체가 필요한 소모품을 알려드려요.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = carModel,
            onValueChange = { carModel = it },
            label = { Text("차량 모델명 (예: 싼타페 DM)") },
            modifier = Modifier.fillMaxWidth(),
            colors = whiteFieldColors()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = mileageText,
            onValueChange = { v -> if (v.all { it.isDigit() }) mileageText = v },
            label = { Text("현재 주행거리 (km)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = whiteFieldColors()
        )
        Spacer(Modifier.height(12.dp))

        Text("연료 타입", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EngineType.entries.forEach { type ->
                FilterChip(
                    selected = engineType == type,
                    onClick = { engineType = type },
                    label = { Text(type.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.Black,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val km = mileageText.toIntOrNull() ?: 0
                recommendations = MaintenanceSchedule.recommend(km, engineType)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
            enabled = mileageText.isNotBlank()
        ) {
            Text("추천 받기", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))

        recommendations?.let { list ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list) { rec -> MaintenanceRow(rec) }
            }
        }
    }
}

@Composable
private fun whiteFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Black,
    unfocusedBorderColor = Color(0xFFBDBDBD),
    focusedLabelColor = Color.Black,
    cursorColor = Color.Black
)

@Composable
private fun MaintenanceRow(rec: MaintenanceRecommendation) {
    val (bg, badgeText, badgeColor) = when (rec.status) {
        MaintenanceRecommendation.Status.DUE_SOON -> Triple(Color(0xFFFDEDEC), "교체 시기", Color(0xFFB3261E))
        MaintenanceRecommendation.Status.OVERDUE -> Triple(Color(0xFFFDEDEC), "교체 시기", Color(0xFFB3261E))
        MaintenanceRecommendation.Status.OK -> Triple(Color(0xFFF2F2F2), "여유 있음", Color(0xFF616161))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(rec.item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "권장 주기: ${rec.item.intervalKm.formatKm()} / ${rec.item.intervalMonths}개월",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(rec.item.note, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeColor
                ) {
                    Text(
                        badgeText,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (rec.kmUntilDue <= 0) "지금 점검" else "${rec.kmUntilDue.formatKm()} 남음",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun Int.formatKm(): String = "%,dkm".format(this)
