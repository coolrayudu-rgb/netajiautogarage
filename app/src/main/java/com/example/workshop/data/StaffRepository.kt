package com.example.workshop.data

class StaffRepository(
    private val staffDao: StaffDao
) {

    suspend fun login(username: String, password: String): Staff? {
        return staffDao.login(username, password)
    }

    suspend fun getAllStaff(): List<Staff> {
        return staffDao.getAllStaff()
    }

    suspend fun getStaffById(id: Int): Staff? {
        return staffDao.getStaffById(id)
    }

    suspend fun addStaff(staff: Staff) {
        staffDao.insert(staff)
    }

    suspend fun updateStaff(staff: Staff) {
        staffDao.update(staff)
    }

    suspend fun deleteStaff(staff: Staff) {
        staffDao.delete(staff)
    }
}