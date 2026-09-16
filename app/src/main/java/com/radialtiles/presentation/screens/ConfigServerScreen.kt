package com.radialtiles.presentation.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.radialtiles.data.server.WebConfigServer
import com.radialtiles.presentation.theme.AmberAccent
import com.radialtiles.presentation.theme.Black

@Composable
fun ConfigServerScreen(
    server: WebConfigServer,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val ipAddress = remember { server.getLocalIpAddress(context) }
    val portalUrl = "http://$ipAddress:8080"

    val qrBitmap = remember(portalUrl) {
        generateQrBitmap(portalUrl, 200)
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp)
    ) {
        item {
            Text(
                text = "Phone Setup",
                style = MaterialTheme.typography.title2,
                color = AmberAccent,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Scan with phone or open:",
                style = MaterialTheme.typography.caption1,
                color = androidx.compose.ui.graphics.Color.LightGray
            )
        }

        item {
            Text(
                text = portalUrl,
                style = MaterialTheme.typography.caption1.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = androidx.compose.ui.graphics.Color.White,
                textAlign = TextAlign.Center
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            qrBitmap?.let { bmp ->
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Config QR Code",
                        modifier = Modifier.size(102.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(36.dp),
                colors = ButtonDefaults.primaryButtonColors(backgroundColor = AmberAccent)
            ) {
                Text(
                    text = "Done",
                    color = Black,
                    style = MaterialTheme.typography.button
                )
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
