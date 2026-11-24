package com.kgyms.app.viewmodel.member

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.database.ValueEventListener
import com.kgyms.app.data.repository.ChatMessageDto
import com.kgyms.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessageDto>>(emptyList())
    val messages: StateFlow<List<ChatMessageDto>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val uid: String = repository.currentUid ?: "demo_user"
    private var listener: ValueEventListener? = null

    init {
        loadHistory()
        setupRealtimeObserver()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val history = repository.getHistory(uid)
            if (history.isEmpty()) {
                val welcomeText = "Xin chào! Tôi là **Bob** - Trợ lý AI thể hình của bạn tại KgyMs. Bạn cần tôi hỗ trợ gì về lịch tập, bài tập, mã QR hay gói thẻ hội viên hôm nay?"
                val saved = repository.saveMessage(uid, welcomeText, isUser = false)
                if (saved != null) {
                    _messages.value = listOf(saved)
                } else {
                    // Fallback local nếu DB chưa bật rules
                    _messages.value = listOf(ChatMessageDto(id = "1", text = welcomeText, isUser = false, timestamp = System.currentTimeMillis()))
                }
            } else {
                _messages.value = history
            }
        }
    }

    private fun setupRealtimeObserver() {
        listener = repository.observeHistory(uid) { list ->
            if (list.isNotEmpty()) {
                _messages.value = list
            }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return

        viewModelScope.launch {
            _isLoading.value = true
            
            // 1. Lưu tin nhắn user (hoặc thêm tạm vào local nếu DB lỗi)
            val userMsg = repository.saveMessage(uid, userText, isUser = true)
            if (userMsg == null && _messages.value.none { it.text == userText && it.isUser }) {
                val current = _messages.value.toMutableList()
                current.add(ChatMessageDto(id = System.currentTimeMillis().toString(), text = userText, isUser = true, timestamp = System.currentTimeMillis()))
                _messages.value = current
            }

            // 2. Gọi Gemini với ngữ cảnh lịch sử
            val replyText = generateGeminiResponse(userText)

            // 3. Lưu câu trả lời của Bob
            val botMsg = repository.saveMessage(uid, replyText, isUser = false)
            if (botMsg == null) {
                val current = _messages.value.toMutableList()
                current.add(ChatMessageDto(id = (System.currentTimeMillis() + 1).toString(), text = replyText, isUser = false, timestamp = System.currentTimeMillis()))
                _messages.value = current
            }

            _isLoading.value = false
        }
    }

    private suspend fun generateGeminiResponse(currentQuestion: String): String {
        return try {
            val model = Firebase.ai(
                backend = GenerativeBackend.googleAI()
            ).generativeModel(
                modelName = "gemini-3.5-flash-lite"
            )

            val currentList = _messages.value.filter { it.text != currentQuestion }.takeLast(15)
            val historyContent = currentList.map { msg ->
                content(role = if (msg.isUser) "user" else "model") {
                    text(msg.text)
                }
            }

            val chat = model.startChat(history = historyContent)
            val response = chat.sendMessage("Bạn là Bob, trợ lý AI thông minh của ứng dụng quản lý phòng gym KgyMs. Hãy trả lời ngắn gọn, đúng trọng tâm câu hỏi và thân thiện bằng tiếng Việt: $currentQuestion")
            response.text ?: simulateSmartGymReply(currentQuestion)
        } catch (e: Exception) {
            Log.e("BOB_CHAT_VM", "Gemini AI error with history", e)
            simulateSmartGymReply(currentQuestion)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory(uid)
            _messages.value = emptyList()
            val welcomeText = "Xin chào! Tôi là **Bob** - Trợ lý AI thể hình của bạn tại KgyMs. Lịch sử đã được làm mới. Bạn cần tôi hỗ trợ gì tiếp theo?"
            val saved = repository.saveMessage(uid, welcomeText, isUser = false)
            if (saved != null) {
                _messages.value = listOf(saved)
            } else {
                _messages.value = listOf(ChatMessageDto(id = System.currentTimeMillis().toString(), text = welcomeText, isUser = false, timestamp = System.currentTimeMillis()))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.removeListener(uid, listener)
    }
}

fun simulateSmartGymReply(question: String): String {
    val q = question.lowercase()
    return when {
        q.contains("gia hạn") || q.contains("nộp tiền") || q.contains("thanh toán") -> "Để gia hạn thẻ hội viên, bạn vui lòng vào mục **Quản lý thẻ**, chọn gói thẻ hiện tại của bạn và bấm nút **Gia hạn** để thực hiện thanh toán nhé!"
        q.contains("tạ") || q.contains("đơn") || q.contains("máy") -> "Tập tạ đơn hoặc máy đều rất tốt để phát triển cơ bắp! Bạn nên tập 3-4 hiệp, mỗi hiệp từ 10-12 reps, chú ý giữ lưng thẳng và siết cơ bụng nhé."
        q.contains("tư thế") || q.contains("cách tập") || q.contains("như thế nào") -> "Tư thế chuẩn là yếu tố quyết định để tránh chấn thương. Bạn cần kiểm soát nhịp thở và tập trung vào cơ đích."
        q.contains("buổi") || q.contains("mấy") || q.contains("lịch") || q.contains("tuần") -> "Để đạt hiệu quả tốt nhất, bạn nên duy trì lịch tập 4 buổi/tuần (kết hợp thân trên và thân dưới) kèm 1-2 buổi cardio nhé!"
        q.contains("ăn") || q.contains("dinh dưỡng") || q.contains("protein") -> "Dinh dưỡng chiếm 70% kết quả! Hãy bổ sung đủ Protein từ ức gà, trứng, thịt bò và uống đủ 2-3 lít nước mỗi ngày."
        q.contains("giảm cân") || q.contains("mỡ") -> "Để giảm mỡ hiệu quả, bạn cần duy trì thâm hụt calo kết hợp tập luyện đều đặn."
        q.contains("qr") || q.contains("check-in") || q.contains("vào cửa") || q.contains("ra vào") -> "Bạn có thể sử dụng tính năng mã QR trên thanh menu (tab QR) để quét mã check-in ra vào phòng tập nhanh chóng mà không cần mang thẻ cứng!"
        q.contains("đổi tên") || q.contains("avatar") || q.contains("hồ sơ") || q.contains("thön") || q.contains("thông tin cá nhân") -> "Bạn có thể dễ dàng đổi tên, ảnh đại diện (avatar) hoặc cập nhật thông tin cá nhân bằng cách vào tab **Hồ sơ (Profile)** và chọn chỉnh sửa nhé."
        q.contains("giao diện") || q.contains("dark mode") || q.contains("sáng") || q.contains("tối") -> "KgyMs hỗ trợ chuyển đổi giao diện linh hoạt giữa Chế độ sáng (Light Mode) và Chế độ tối (Dark Mode). Bạn có thể vào tab **Hồ sơ ➔ Cài đặt ➔ Giao diện** để thay đổi nha!"
        q.contains("admin") || q.contains("quản lý") || q.contains("nhân viên") || q.contains("doanh thu") -> "KgyMs có phân quyền hệ thống Admin CMS dành riêng cho quản lý để theo dõi doanh thu, quản lý hội viên, thiết bị và giải quyết khiếu nại."
        q.contains("thẻ") || q.contains("gói") || q.contains("đăng kí") || q.contains("đăng ký") || q.contains("thành viên") -> "KgyMs hiện có 3 gói hội viên siêu ưu đãi:\n1️⃣ **Thẻ Beginner (Người mới)** - 300k/tháng: Thiết bị cơ bản, tự do 6h-22h, gửi xe miễn phí.\n2️⃣ **Thẻ Intermediate (Trung cấp)** - 600k/tháng: Thêm lớp Group X (Yoga, Zumba), tặng 1 buổi PT.\n3️⃣ **Thẻ Expert (Chuyên nghiệp)** - 1.2M/tháng: Không giới hạn giờ, PT 3 buổi/tháng, khăn & nước miễn phí.\nBạn có thể vào mục Quản lý thẻ để xem chi tiết và vuốt chọn nhé!"
        q.contains("chào") || q.contains("lô") || q.contains("hi") || q.contains("alo") -> "Chào bạn! Bob là trợ lý AI thông minh của KgyMs. Tôi có thể giúp gì cho bạn về lịch tập, dinh dưỡng, mã QR hay các gói thẻ hội viên ạ?"
        else -> "Câu hỏi rất hay! Tại KgyMs, chúng tôi cung cấp đầy đủ hệ thống quản lý tập luyện, lịch tập cá nhân, check-in QR và tư vấn 24/7 từ Bob. Bạn cần tôi hỗ trợ chi tiết hơn về vấn đề gì không?"
        }
}
