package com.example.domain.usecase

import com.example.domain.repository.CloudRepository

class SignInUseCase(private val repository: CloudRepository) {
    suspend operator fun invoke(email: String, password: String) {
        repository.signIn(email, password)
    }
}
