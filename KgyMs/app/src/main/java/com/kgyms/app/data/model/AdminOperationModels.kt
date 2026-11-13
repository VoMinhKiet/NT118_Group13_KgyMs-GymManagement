package com.kgyms.app.data.model

/*
 * ================================================================
 * Model nghiệp vụ cho các màn Admin chưa nối Firebase
 * (hiện chỉ còn Khiếu nại).
 * Dữ liệu hiện do AdminMockRepository cung cấp; khi nối Firebase chỉ cần
 * thay repository, ViewModel và màn hình giữ nguyên.
 * ================================================================
 */

enum class ComplaintStatus { New, InProgress }

data class AdminComplaint(
    /** Vd "#CS-208". */
    val id: String,
    val title: String,
    val memberName: String,
    /** dd/MM/yyyy */
    val date: String,
    val isHighPriority: Boolean,
    val status: ComplaintStatus,
    val description: String
)
