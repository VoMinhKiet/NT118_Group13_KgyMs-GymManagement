package com.kgyms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kgyms.app.ui.admin.AdminNavHost
import com.kgyms.app.ui.theme.KgyMsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * ============================================================
         * EDGE TO EDGE
         * ============================================================
         */

        enableEdgeToEdge()


        /*
         * ============================================================
         * IMMERSIVE MODE
         * ============================================================
         *
         * Ẩn:
         * - Navigation bar
         * - Status bar
         *
         * Người dùng vẫn có thể vuốt từ cạnh màn hình
         * để hiện lại system bars.
         */

        hideSystemBars()


        /*
         * ============================================================
         * COMPOSE
         * ============================================================
         */

        setContent {

            KgyMsTheme {

                AdminNavHost()
            }
        }
    }


    /*
     * ================================================================
     * HIDE SYSTEM BARS
     * ================================================================
     */

    private fun hideSystemBars() {

        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).apply {

            /*
             * Ẩn Navigation Bar
             */

            hide(
                WindowInsetsCompat.Type.navigationBars()
            )


            /*
             * Cho phép người dùng vuốt để hiện lại.
             *
             * Ví dụ:
             *
             * Vuốt từ dưới lên
             *        ↓
             * Navigation bar hiện
             */

            systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}