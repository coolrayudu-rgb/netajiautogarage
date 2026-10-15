package com.example.workshop.data

class AdminRepository(
    private val adminDao: AdminDao
) {

    suspend fun login(
        username: String,
        password: String
    ): Admin? {
        return adminDao.login(username, password)
    }

    suspend fun addAdmin(admin: Admin) {
        adminDao.insert(admin)
    }
}