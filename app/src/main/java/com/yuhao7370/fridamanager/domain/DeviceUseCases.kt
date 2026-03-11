package com.yuhao7370.fridamanager.domain

import com.yuhao7370.fridamanager.data.AbiRepository
import com.yuhao7370.fridamanager.model.DeviceAbiInfo

class DetectDeviceAbiUseCase(private val repository: AbiRepository) {
    suspend operator fun invoke(): DeviceAbiInfo = repository.detectDeviceAbi()
}
