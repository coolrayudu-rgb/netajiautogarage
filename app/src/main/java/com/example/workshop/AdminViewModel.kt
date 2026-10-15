package com.example.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workshop.data.Admin
import com.example.workshop.data.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdminViewModel(
    private val repository: AdminRepository
) : ViewModel() {

    private val _loginResult = MutableStateFlow<Admin?>(null)

    val loginResult: StateFlow<Admin?> = _loginResult

    fun login(username: String, password: String) {

        viewModelScope.launch {

            _loginResult.value =
                repository.login(username, password)
        }
    }

    fun clearLoginResult() {
        _loginResult.value = null
    }
}