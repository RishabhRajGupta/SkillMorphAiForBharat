
package com.example.skillmorph

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.skillmorph.presentation.main.DailyBriefingWorker
import com.example.skillmorph.presentation.navigation.AppNavigation
import com.example.skillmorph.ui.theme.SkillMorphTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        scheduleDailyBriefing()
        setContent {
            SkillMorphTheme {
                AppNavigation()
            }
        }
    }
    private fun scheduleDailyBriefing() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create the request
        val dailyWork = PeriodicWorkRequestBuilder<DailyBriefingWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(calculateDelayFor6AM(), TimeUnit.MILLISECONDS)
            .addTag("daily_briefing") // Useful for debugging
            .build()

        // Enqueue it
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyBriefing",
            ExistingPeriodicWorkPolicy.KEEP, // KEEP = If already scheduled, don't replace it
            dailyWork
        )
    }

    // Helper: How many milliseconds until the next 6:00 AM?
    private fun calculateDelayFor6AM(): Long {
        val now = LocalDateTime.now()
        var target = now.withHour(6).withMinute(0).withSecond(0).withNano(0)

        // If it is currently 8 AM, the next 6 AM is tomorrow.
        if (now.isAfter(target)) {
            target = target.plusDays(1)
        }

        return ChronoUnit.MILLIS.between(now, target)
    }
}



@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SkillMorphTheme {

    }
}
