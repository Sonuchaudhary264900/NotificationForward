package com.sonuchaudhary.notificationforward.ui.pairing

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.sonuchaudhary.notificationforward.ui.common.OnResumeEffect

@Composable
fun PairingCodeScreen(
    viewModel: PairingViewModel,
    onPaired: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.generateChildCode() }
    OnResumeEffect { viewModel.refreshStatusOnce() }
    LaunchedEffect(state.paired) { if (state.paired) onPaired() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Share this code with a parent",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Waiting for a parent device to enter it or scan the QR code below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        when {
            state.loading && state.code == null -> CircularProgressIndicator()
            state.error != null -> Text(
                state.error ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            state.code != null -> {
                val code = state.code!!
                Text(
                    code,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                val qrBitmap = remember(code) { encodeQr(code) }
                qrBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Pairing QR code",
                        modifier = Modifier.size(220.dp)
                    )
                }

                Text(
                    "This code expires in a few minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
    }
}

private fun encodeQr(content: String): Bitmap? {
    return runCatching {
        BarcodeEncoder().encodeBitmap(
            content,
            com.google.zxing.BarcodeFormat.QR_CODE,
            600,
            600
        )
    }.getOrNull()
}
