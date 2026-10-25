package com.example.domain.usecase

import com.example.domain.repository.CloudRepository

class RegisterAccountUseCase(private val repository: CloudRepository) {
    suspend operator fun invoke(email: String, password: String) {
        repository.register(email, password)
    }
}
