package com.example.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workshop.data.Staff
import com.example.workshop.data.StaffRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WorkshopViewModel(
    private val repository: StaffRepository
) : ViewModel() {

    private val _loginResult = MutableStateFlow<Staff?>(null)
    val loginResult: StateFlow<Staff?> = _loginResult

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginResult.value = repository.login(username, password)
        }
    }

    fun clearLoginResult() {
        _loginResult.value = null
    }
}