package com.example.cooltacoandroid.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class ActivityVerificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString("TASK_ID") ?: return Result.failure()
        val targetSteps = inputData.getInt("TARGET_STEPS", 1000)

        Log.d("CoolTacoWorker", "Mulai verifikasi otomatis untuk Task: $taskId")

        return try {
            // [RESEARCH DEMO MODE]
            // Simulasi proses membaca data dari Health Connect / Smartwatch
            // Kita beri delay 2 detik agar terlihat natural seolah sedang mengambil data
            delay(2000)

            // Simulasi anak berhasil melakukan 850 langkah dari target 1000
            val simulatedStepsRead = 850

            Log.d("CoolTacoWorker", "Berhasil membaca sensor simulasi: $simulatedStepsRead langkah.")

            // Di sini nanti logika untuk menambah koin ke SharedPreferences / Firebase
            // ...

            // Beri tahu sistem Android bahwa Worker berhasil menjalankan tugasnya
            Result.success()

        } catch (e: Exception) {
            Log.e("CoolTacoWorker", "Gagal membaca sensor: ${e.message}")
            // Jika gagal, coba lagi nanti
            Result.retry()
        }
    }
}