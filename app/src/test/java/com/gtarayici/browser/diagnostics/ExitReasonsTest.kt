package com.gtarayici.browser.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class ExitReasonsTest {
    @Test
    fun summarizesMainThreadOfAnrTrace() {
        val trace = """
            ----- pid 1234 at 2026-11-22 -----
            "Signal Catcher" daemon prio=10 tid=2 Runnable
              at a.b.C.ignored(C.java:1)

            "main" prio=5 tid=1 Sleeping
              | group="main" sCount=1
              at java.lang.Thread.sleep(Native method)
              at com.gtarayici.browser.data.Foo.load(Foo.kt:42)
              at com.gtarayici.browser.MainActivity.onCreate(MainActivity.kt:90)
              at android.app.Activity.performCreate(Activity.java:8000)
              at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1)

            "Binder:1234_1" prio=5
        """.trimIndent()
        assertEquals(
            "java.lang.Thread.sleep ← com.gtarayici.browser.data.Foo.load ← com.gtarayici.browser.MainActivity.onCreate ← android.app.Activity.performCreate",
            ExitReasons.mainThreadFrames(trace),
        )
        assertEquals("", ExitReasons.mainThreadFrames("no threads here"))
    }
}
