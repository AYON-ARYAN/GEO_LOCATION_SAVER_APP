package com.techpuram.app.gpsmapcamera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techpuram.app.gpsmapcamera.ui.theme.GPSmapCameraTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            GPSmapCameraTheme {
                SettingsScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showTermsOfService by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // App Info Section
            item {
                SettingsSection(title = "App Information") {
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        title = "About GPS Map Camera",
                        subtitle = "Version 1.0.0",
                        onClick = { showAbout = true }
                    )
                    SettingsItem(
                        icon = Icons.Filled.Star,
                        title = "Rate This App",
                        subtitle = "Help us improve by rating the app",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                                context.startActivity(webIntent)
                            }
                        }
                    )
                }
            }

            // Legal Section
            item {
                SettingsSection(title = "Legal") {
                    SettingsItem(
                        icon = Icons.Filled.PrivacyTip,
                        title = "Privacy Policy",
                        subtitle = "How we handle your data",
                        onClick = { showPrivacyPolicy = true }
                    )
                    SettingsItem(
                        icon = Icons.Filled.Gavel,
                        title = "Terms of Service",
                        subtitle = "Terms and conditions of use",
                        onClick = { showTermsOfService = true }
                    )
                }
            }

            // Support Section
            item {
                SettingsSection(title = "Support") {
                    SettingsItem(
                        icon = Icons.Filled.Email,
                        title = "Contact Support",
                        subtitle = "Get help with the app",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:")
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@geogpscamera.in"))
                                putExtra(Intent.EXTRA_SUBJECT, "GPS Map Camera - Support Request")
                                putExtra(Intent.EXTRA_TEXT, "Please describe your issue here...")
                            }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            }
                        }
                    )
                    SettingsItem(
                        icon = Icons.Filled.Web,
                        title = "Visit Website",
                        subtitle = "geogpscamera.in",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://geogpscamera.in"))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Data Section
            item {
                SettingsSection(title = "Data & Storage") {
                    SettingsItem(
                        icon = Icons.Filled.Folder,
                        title = "Storage Location",
                        subtitle = "Pictures/GPSMapCamera",
                        onClick = { /* Could open file manager to the folder */ }
                    )
                    SettingsItem(
                        icon = Icons.Filled.Security,
                        title = "Verification Service",
                        subtitle = "Powered by geogpscamera.in",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://geogpscamera.in/verify"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    // Privacy Policy Dialog
    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "GPS Map Camera Privacy Policy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    item {
                        Text(
                            text = "Last updated: ${java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.US).format(java.util.Date())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Text(
                            text = "1. Information We Collect",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Location data: GPS coordinates are used to embed location information in your photos\n" +
                                    "• Photo data: Images and metadata for verification purposes\n" +
                                    "• Device information: Device model and system information for verification",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "2. How We Use Information",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Provide geo-tagging services for your photos\n" +
                                    "• Generate verification codes for authenticity\n" +
                                    "• Improve app functionality and user experience\n" +
                                    "• Provide customer support",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "3. Information Sharing",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "We do not sell, trade, or share your personal information with third parties except:\n" +
                                    "• When you explicitly choose to share verification data\n" +
                                    "• To comply with legal requirements\n" +
                                    "• To protect our rights and safety",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "4. Data Security",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "We implement appropriate security measures to protect your information using encryption and secure transmission protocols.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "5. Your Rights",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "You have the right to:\n" +
                                    "• Access your personal data\n" +
                                    "• Request data deletion\n" +
                                    "• Opt out of data collection\n" +
                                    "• Contact us with privacy concerns",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "Contact Us",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "For privacy concerns, contact us at: support@geogpscamera.in",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicy = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Terms of Service Dialog
    if (showTermsOfService) {
        AlertDialog(
            onDismissRequest = { showTermsOfService = false },
            title = { Text("Terms of Service", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "GPS Map Camera Terms of Service",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    item {
                        Text(
                            text = "Last updated: ${java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.US).format(java.util.Date())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        Text(
                            text = "1. Acceptance of Terms",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "By using GPS Map Camera, you agree to these terms and conditions. If you do not agree, please discontinue use of the app.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "2. App Usage",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Use the app only for legitimate purposes\n" +
                                    "• Do not attempt to circumvent security features\n" +
                                    "• Respect privacy and rights of others in your photos\n" +
                                    "• Do not use for illegal activities",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "3. Verification Service",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Verification codes are provided as-is\n" +
                                    "• We strive for accuracy but cannot guarantee 100% reliability\n" +
                                    "• Verification data may be stored for authenticity purposes\n" +
                                    "• Service availability may vary",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "4. User Responsibilities",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "• Ensure you have rights to photograph locations and subjects\n" +
                                    "• Comply with local laws and regulations\n" +
                                    "• Keep your app updated for security\n" +
                                    "• Use location services responsibly",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "5. Limitation of Liability",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "The app is provided 'as-is' without warranties. We are not liable for any damages arising from app usage.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "6. Changes to Terms",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "We may update these terms periodically. Continued use constitutes acceptance of updated terms.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text(
                            text = "Contact Us",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "For questions about these terms, contact us at: support@geogpscamera.in",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsOfService = false }) {
                    Text("Close")
                }
            }
        )
    }

    // About Dialog
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About GPS Map Camera", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "GPS Map Camera",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "A professional geo-tagging camera app that embeds GPS coordinates and location information directly into your photos with cryptographic verification.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Features:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• GPS coordinate embedding\n" +
                                "• Location address overlay\n" +
                                "• Cryptographic verification\n" +
                                "• PDF export support\n" +
                                "• Professional geo-tagging",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Developed by TechPuram",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}