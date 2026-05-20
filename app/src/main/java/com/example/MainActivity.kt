package com.example

import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BrowserExtension
import com.example.viewmodel.VideoStreamOption
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BrowserViewModel
import com.example.viewmodel.LockPurpose

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                BrowserApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val viewModel: BrowserViewModel = viewModel()

    // State bindings
    val isIncognito by viewModel.isIncognitoMode.collectAsState()
    val activeWebUrl by viewModel.currentWebUrl.collectAsState()
    val activeWebTitle by viewModel.currentWebTitle.collectAsState()
    
    val normalTabs by viewModel.normalTabs.collectAsState()
    val incognitoTabs by viewModel.incognitoTabs.collectAsState()
    
    val currentNormalId by viewModel.currentNormalTabId.collectAsState()
    val currentIncognitoId by viewModel.currentIncognitoTabId.collectAsState()

    val extensions by viewModel.extensions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isLocked by viewModel.isContentLocked.collectAsState()
    val lockPurpose by viewModel.lockPurpose.collectAsState()

    // Detected video streams for yt-dlp simulation
    val detectedStreams by viewModel.detectedVideoStreams.collectAsState()
    val isDetectingVideo by viewModel.isDetectingVideo.collectAsState()

    // Active full screen in-app video player
    val activePlayerUrl by viewModel.activePlayerUrl.collectAsState()
    val activePlayerTitle by viewModel.activePlayerTitle.collectAsState()

    // Local controller references
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var inputUrlText by remember { mutableStateOf("https://www.google.com") }
    val isCurrentBookmarked by viewModel.isBookmarkedFlow(activeWebUrl).collectAsState(initial = false)

    // Modal view triggers
    var openPanel by remember { mutableStateOf(ActivePanel.NONE) }
    var showStreamPickerSheet by remember { mutableStateOf(false) }

    // Synchronize Input text on load URL changes
    LaunchedEffect(activeWebUrl) {
        inputUrlText = activeWebUrl
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = if (isIncognito) Color(0xFF0e0e11) else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            
            // 1. BASE LEVEL REVIEWS (WEBVIEW LAYER)
            Column(modifier = Modifier.fillMaxSize()) {
                
                // Top control status navigation Bar
                Surface(
                    tonalElevation = 8.dp,
                    color = if (isIncognito) Color(0xFF16161a) else MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Back Navigation button
                            IconButton(
                                onClick = {
                                    if (webViewRef?.canGoBack() == true) {
                                        webViewRef?.goBack()
                                    }
                                },
                                enabled = webViewRef?.canGoBack() == true,
                                modifier = Modifier.testTag("nav_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Geri",
                                    tint = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Address bar text input field
                            OutlinedTextField(
                                value = inputUrlText,
                                onValueChange = { inputUrlText = it },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        // Auto-generate search engine query if plain text
                                        val targetUrl = if (inputUrlText.startsWith("http://") || inputUrlText.startsWith("https://")) {
                                            inputUrlText
                                        } else if (inputUrlText.contains(".") && !inputUrlText.contains(" ")) {
                                            "https://$inputUrlText"
                                        } else {
                                            "https://www.google.com/search?q=${inputUrlText.replace(" ", "+")}"
                                        }
                                        webViewRef?.loadUrl(targetUrl)
                                    }
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 13.sp,
                                    color = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isIncognito) Icons.Default.Security else Icons.Default.Search,
                                        contentDescription = "Ara",
                                        tint = if (isIncognito) Color(0xFF8ab4f8) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Reload page
                                        IconButton(
                                            onClick = { webViewRef?.reload() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Yenile",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        // Bookmark icon toggler
                                        IconButton(
                                            onClick = { viewModel.toggleBookmark(activeWebUrl, activeWebTitle) },
                                            modifier = Modifier.size(32.dp).testTag("bookmark_toggle_btn")
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrentBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                                contentDescription = "Kaydet",
                                                tint = if (isCurrentBookmarked) Color(0xFFf4b400) else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.0f)
                                    .height(50.dp)
                                    .testTag("address_bar"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = if (isIncognito) Color(0xFF202124) else Color.Transparent,
                                    unfocusedContainerColor = if (isIncognito) Color(0xFF202124) else Color.Transparent,
                                    focusedBorderColor = if (isIncognito) Color(0xFF3c4043) else MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = if (isIncognito) Color(0xFF3c4043) else MaterialTheme.colorScheme.outline
                                )
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Incognito mask icon mode switch
                            IconButton(
                                onClick = {
                                    viewModel.toggleIncognitoMode(context) {
                                        // Callback to request PIN auth prompt visual
                                        focusManager.clearFocus()
                                    }
                                },
                                modifier = Modifier
                                    .background(
                                        color = if (isIncognito) Color(0x338ab4f8) else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .testTag("incognito_badge")
                            ) {
                                Icon(
                                    imageVector = if (isIncognito) Icons.Default.Security else Icons.Outlined.Security,
                                    contentDescription = "Gizli Sekme",
                                    tint = if (isIncognito) Color(0xFF8ab4f8) else Color.Gray
                                )
                            }
                        }
                    }
                }

                // If on Google or empty search landing, display a highly optimized custom home dial Grid
                val showHomeDial = activeWebUrl.contains("google.com") && !activeWebUrl.contains("q=")
                
                Box(modifier = Modifier.weight(1.0f)) {
                    // Embed standard browser view
                    BrowserWebView(
                        url = activeWebUrl,
                        onPageStarted = { url ->
                            inputUrlText = url
                        },
                        onPageFinished = { url, title ->
                            viewModel.handlePageLoad(url, title)
                        },
                        activeExtensions = extensions,
                        onVideoDetected = { videoUrl, videoTitle ->
                            // When a webpage embeds HTML5 media streams, bridge alerts View model
                            Toast.makeText(context, "Hızlı Video Akışı Yakalandı!", Toast.LENGTH_SHORT).show()
                        },
                        onWebViewCreated = { webViewRef = it }
                    )

                    // Overlay polished speed shortcut dials over Google landing
                    if (showHomeDial) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (isIncognito) Color(0xFF0e0e11) else MaterialTheme.colorScheme.background
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "G-Tarayıcı",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 32.sp,
                                    letterSpacing = 1.sp,
                                    color = if (isIncognito) Color.White else MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Güvenli, Eklenti Destekli ve Biyometrik Korumalı",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isIncognito) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                // Quick Shortcut Dial Layout
                                val dials = listOf(
                                    DialShortcut("Youtube", "https://www.youtube.com", Icons.Default.VideoLibrary, Color(0xFFff0000)),
                                    DialShortcut("Google", "https://www.google.com", Icons.Default.Search, Color(0xFF4285f4)),
                                    DialShortcut("Wikipedia", "https://www.wikipedia.org", Icons.Default.ImportContacts, Color(0xFFffffff)),
                                    DialShortcut("Twitter / X", "https://www.twitter.com", Icons.Default.Tag, Color(0xFF333333))
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    dials.forEach { dial ->
                                        Card(
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isIncognito) Color(0xFF16161a) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    webViewRef?.loadUrl(dial.url)
                                                }
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isIncognito) Color(0xFF24242a) else Color.Transparent,
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .testTag("dial_${dial.name}")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = dial.color.copy(alpha = 0.15f),
                                                    modifier = Modifier.size(46.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = dial.icon,
                                                            contentDescription = dial.name,
                                                            tint = dial.color,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = dial.name,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isIncognito) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Nav bar options
                Surface(
                    tonalElevation = 6.dp,
                    color = if (isIncognito) Color(0xFF16161a) else MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Forward Navigation
                        IconButton(
                            onClick = {
                                if (webViewRef?.canGoForward() == true) {
                                    webViewRef?.goForward()
                                }
                            },
                            enabled = webViewRef?.canGoForward() == true,
                            modifier = Modifier.testTag("nav_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "İleri",
                                tint = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                             )
                        }

                        // Home page loader
                        IconButton(
                            onClick = { webViewRef?.loadUrl("https://www.google.com") },
                            modifier = Modifier.testTag("home_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Home",
                                tint = if (isIncognito) Color(0xFF8ab4f8) else MaterialTheme.colorScheme.primary
                            )
                        }

                        // Open active panel buttons: Bookmarks, History, Downloads
                        IconButton(
                            onClick = {
                                viewModel.requestLockCheck(LockPurpose.DOWNLOADS, {
                                    // Requires auth
                                }) {
                                    openPanel = ActivePanel.DOWNLOADS
                                }
                            },
                            modifier = Modifier.testTag("downloads_badge")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Downloading,
                                contentDescription = "İndirmeler",
                                tint = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.requestLockCheck(LockPurpose.HISTORY, {
                                    // Requires PIN
                                }) {
                                    openPanel = ActivePanel.HISTORY
                                }
                            },
                            modifier = Modifier.testTag("history_badge")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Geçmiş",
                                tint = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Multi-tabs controller trigger
                        IconButton(
                            onClick = { openPanel = ActivePanel.TABS },
                            modifier = Modifier.testTag("tabs_badge")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = "Sekmeler",
                                    tint = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = (if (isIncognito) incognitoTabs.size else normalTabs.size).toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 1.dp)
                                )
                            }
                        }

                        // Plugin Extensions Store Drawer trigger
                        IconButton(
                            onClick = { openPanel = ActivePanel.EXTENSIONS },
                            modifier = Modifier.testTag("extension_badge")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = "Eklentiler",
                                tint = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Secure settings trigger
                        IconButton(
                            onClick = { openPanel = ActivePanel.SETTINGS },
                            modifier = Modifier.testTag("settings_badge")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ayarlar",
                                tint = if (isIncognito) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 2. MIDDLE FLOATING SPECIAL EXTENSION BUTTON (GLOWING FILM REEL POP-UP INDICATING ACTIVE COPIED VIDEOS)
            if (detectedStreams.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 96.dp, end = 20.dp)
                ) {
                    FloatingActionButton(
                        onClick = { showStreamPickerSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(58.dp)
                            .testTag("video_detector_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = "Video İndir (yt-dlp)",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }


            // 3. OVERLAYS PANEL DRAWERS (TABS, DOWNLOADS, EXTENSIONS, SETTINGS, HISTORY, BOOKMARKS)
            AnimatedVisibility(
                visible = openPanel != ActivePanel.NONE,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 12.dp
                ) {
                    when (openPanel) {
                        ActivePanel.TABS -> {
                            TabsManagerView(viewModel) { openPanel = ActivePanel.NONE }
                        }
                        ActivePanel.DOWNLOADS -> {
                            DownloadsPanel(
                                viewModel = viewModel,
                                onNavigateToUrl = { url ->
                                    webViewRef?.loadUrl(url)
                                },
                                onPlayLocalVideo = { path, title ->
                                    focusManager.clearFocus()
                                    viewModel.playVideoInInternalPlayer(path, title)
                                    openPanel = ActivePanel.NONE
                                }
                            ) { openPanel = ActivePanel.NONE }
                        }
                        ActivePanel.EXTENSIONS -> {
                            ExtensionsPanel(viewModel) { openPanel = ActivePanel.NONE }
                        }
                        ActivePanel.SETTINGS -> {
                            SettingsPanel(viewModel) { openPanel = ActivePanel.NONE }
                        }
                        ActivePanel.HISTORY -> {
                            HistoryPanel(viewModel, onNavigateToUrl = { webViewRef?.loadUrl(it) }) { openPanel = ActivePanel.NONE }
                        }
                        else -> {}
                    }
                }
            }


            // 4. EMBEDDED DYNAMIC VIDEO PROMPTS SELECTION (YT-DLP DOWNLOAD OPTION SELECTOR MODAL)
            if (showStreamPickerSheet) {
                var chosenFileName by remember { mutableStateOf(activeWebTitle.ifEmpty { "G_Browser_Video" }) }

                AlertDialog(
                    onDismissRequest = { showStreamPickerSheet = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = "Video",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("yt-dlp Çözümleme Motoru", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Web akışındaki video yakalandı. İndirmek istediğiniz kaliteyi ve dosya adını seçin:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            OutlinedTextField(
                                value = chosenFileName,
                                onValueChange = { chosenFileName = it },
                                label = { Text("Dosya Adı (.mp4/mp3)") },
                                modifier = Modifier.fillMaxWidth().testTag("video_name_field")
                            )

                            Text(
                                text = "Kullanılabilir Yayınlar:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            LazyColumn(
                                modifier = Modifier.height(180.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(detectedStreams) { opt ->
                                    Button(
                                        onClick = {
                                            viewModel.startVideoDownload(opt, chosenFileName)
                                            showStreamPickerSheet = false
                                            Toast.makeText(context, "İndirme sıraya eklendi!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("dl_option_${opt.resolution}"),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (opt.isMovie) Icons.Default.Videocam else Icons.Default.MusicNote,
                                                    contentDescription = "stream",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(opt.title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(
                                                text = opt.displaySize,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showStreamPickerSheet = false }) {
                            Text("İptal")
                        }
                    }
                )
            }


            // 5. SECURITY CHALLENGE VERIFICATION FRAME OVERLAY
            if (isLocked) {
                SecurityAuthView(
                    purpose = lockPurpose,
                    savedPin = settings.pinCode,
                    isBiometricEnabled = settings.isBiometricEnabled,
                    onSuccess = {
                        viewModel.unlockContentCompleted()
                        // Route to visual screens if valid path checked
                        if (lockPurpose == LockPurpose.DOWNLOADS) {
                            openPanel = ActivePanel.DOWNLOADS
                        } else if (lockPurpose == LockPurpose.HISTORY) {
                            openPanel = ActivePanel.HISTORY
                        }
                    },
                    onCancel = {
                        viewModel.cancelUnlock()
                    }
                )
            }


            // 6. ENTIRE FULL SCREEN INTEGRATED VIDEO PLAYER VIEW OVERLAY
            activePlayerUrl?.let { videoUrl ->
                MyVideoPlayer(
                    videoUrl = videoUrl,
                    title = activePlayerTitle,
                    onClose = {
                        viewModel.closePlayer()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

enum class ActivePanel {
    NONE,
    TABS,
    DOWNLOADS,
    EXTENSIONS,
    SETTINGS,
    HISTORY
}

data class DialShortcut(
    val name: String,
    val url: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)
