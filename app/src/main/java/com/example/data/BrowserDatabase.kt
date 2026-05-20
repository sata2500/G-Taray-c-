package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BrowserTab::class,
        HistoryItem::class,
        Bookmark::class,
        DownloadItem::class,
        BrowserExtension::class,
        AppSettings::class
    ],
    version = 1,
    exportSchema = false
)
abstract class BrowserDatabase : RoomDatabase() {

    abstract fun browserDao(): BrowserDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getDatabase(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "g_browser_database"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default values on database creation
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = database.browserDao()
                                
                                // Seed default settings
                                dao.insertSettings(AppSettings())

                                // Seed useful extensions / blockers
                                dao.insertExtension(
                                    BrowserExtension(
                                        name = "AdBlock & Cookie Dismiss",
                                        description = "Sayfalardaki popup reklamları, izleyicileri ve çerez uyarı pencerelerini otomatik olarak gizler.",
                                        scriptText = """
                                            (function() {
                                                const selectAndRemove = () => {
                                                    const selectors = [
                                                        '#cookie-banner', '.cookie-consent', '#cn-accept-cookie',
                                                        '.ad-box', '.adsbygoogle', 'iframe[src*="doubleclick"]',
                                                        '.sponsored-post', '#promoted-posts', '.banner-ads'
                                                    ];
                                                    selectors.forEach(sel => {
                                                        const el = document.querySelector(sel);
                                                        if (el) el.remove();
                                                    });
                                                };
                                                selectAndRemove();
                                                setTimeout(selectAndRemove, 1000);
                                                setTimeout(selectAndRemove, 3000);
                                            })();
                                        """.trimIndent(),
                                        isEnabled = true,
                                        isPredefined = true
                                    )
                                )

                                dao.insertExtension(
                                    BrowserExtension(
                                        name = "Akıllı Gece Modu (Dark Mode)",
                                        description = "Web sitelerinin arka planını göz yormayan siyah tonlarına çevirir ve metinleri optimize eder.",
                                        scriptText = """
                                            (function() {
                                                const css = `
                                                    html, body, div:not([class*="player"]), article, section, p, span, li, h1, h2, h3, h4, h5, h6 {
                                                        background-color: #121212 !important;
                                                        color: #e0e0e0 !important;
                                                        border-color: #2c2c2c !important;
                                                    }
                                                    a { color: #8ab4f8 !important; }
                                                    input, select, textarea {
                                                        background-color: #202124 !important;
                                                        color: #ffffff !important;
                                                        border: 1px solid #3c4043 !important;
                                                    }
                                                `;
                                                const style = document.createElement('style');
                                                style.type = 'text/css';
                                                style.appendChild(document.createTextNode(css));
                                                document.head.appendChild(style);
                                            })();
                                        """.trimIndent(),
                                        isEnabled = false,
                                        isPredefined = true
                                    )
                                )

                                dao.insertExtension(
                                    BrowserExtension(
                                        name = "Video Hız Kontrolü (1.5x / 2x)",
                                        description = "Tüm web sitelerindeki HTML5 video oynatıcılara hız ayar düğmesi ekler.",
                                        scriptText = """
                                            (function() {
                                                const speedUp = () => {
                                                    document.querySelectorAll('video').forEach(vid => {
                                                        if (!vid.dataset.speedController) {
                                                            vid.dataset.speedController = "true";
                                                            vid.playbackRate = 2.0; // Varsayılan 2x yap
                                                            
                                                            // Ekrana küçük bir hız düğmesi ekle
                                                            const btn = document.createElement('button');
                                                            btn.innerText = '2.0x ⏩';
                                                            btn.style.cssText = 'position: absolute; top: 10px; right: 10px; z-index: 2147483647; background: rgba(0,0,0,0.8); color: white; border: none; padding: 6px 12px; border-radius: 4px; font-size: 11px;';
                                                            btn.onclick = (e) => {
                                                                e.stopPropagation();
                                                                if (vid.playbackRate === 2.0) {
                                                                    vid.playbackRate = 1.0;
                                                                    btn.innerText = '1.0x ▶️';
                                                                } else if (vid.playbackRate === 1.0) {
                                                                    vid.playbackRate = 1.5;
                                                                    btn.innerText = '1.5x 🏎️';
                                                                } else {
                                                                    vid.playbackRate = 2.0;
                                                                    btn.innerText = '2.0x ⏩';
                                                                }
                                                            };
                                                            vid.parentElement.style.position = 'relative';
                                                            vid.parentElement.appendChild(btn);
                                                        }
                                                    });
                                                };
                                                setInterval(speedUp, 1500);
                                            })();
                                        """.trimIndent(),
                                        isEnabled = false,
                                        isPredefined = true
                                    )
                                )
                                
                                // Seed empty main tab
                                dao.insertTab(
                                    BrowserTab(
                                        title = "G-Tarayıcı",
                                        url = "https://www.google.com",
                                        isIncognito = false
                                    )
                                )
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
