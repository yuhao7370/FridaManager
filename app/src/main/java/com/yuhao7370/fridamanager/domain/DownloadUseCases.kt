package com.yuhao7370.fridamanager.domain

import com.yuhao7370.fridamanager.data.FridaDownloadManager
import com.yuhao7370.fridamanager.model.DownloadTask
import com.yuhao7370.fridamanager.model.RemoteFridaAsset
import kotlinx.coroutines.flow.StateFlow

class ObserveDownloadTasksUseCase(private val manager: FridaDownloadManager) {
    operator fun invoke(): StateFlow<List<DownloadTask>> = manager.tasks
}

class EnqueueFridaDownloadUseCase(private val manager: FridaDownloadManager) {
    operator fun invoke(version: String, asset: RemoteFridaAsset): DownloadTask {
        return manager.enqueue(version, asset)
    }
}

class CancelFridaDownloadUseCase(private val manager: FridaDownloadManager) {
    operator fun invoke(taskId: String) {
        manager.cancel(taskId)
    }
}

class RetryFridaDownloadUseCase(private val manager: FridaDownloadManager) {
    operator fun invoke(taskId: String): DownloadTask? {
        return manager.retry(taskId)
    }
}

class ClearFinishedDownloadsUseCase(private val manager: FridaDownloadManager) {
    operator fun invoke() {
        manager.clearFinished()
    }
}
