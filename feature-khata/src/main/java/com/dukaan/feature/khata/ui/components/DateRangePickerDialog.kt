package com.dukaan.feature.khata.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dukaan.core.ui.translation.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (startMillis: Long, endMillis: Long) -> Unit
) {
    val strings = LocalAppStrings.current
    val dateRangePickerState = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        onConfirm(start, end + 24 * 60 * 60 * 1000L - 1)
                    }
                },
                enabled = dateRangePickerState.selectedStartDateMillis != null
                        && dateRangePickerState.selectedEndDateMillis != null
            ) { Text(strings.ok) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = { Text(strings.selectDateRange, modifier = Modifier.padding(16.dp)) },
            modifier = Modifier.height(500.dp)
        )
    }
}
