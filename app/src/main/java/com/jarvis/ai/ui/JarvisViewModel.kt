package com.jarvis.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.ai.ai.Boss
import com.jarvis.ai.ai.Gemini
import com.jarvis.ai.ai.GeminiException
import com.jarvis.ai.data.ChatMsg
import com.jarvis.ai.data.Store
import com.jarvis.ai.services.JarvisAccessibilityService
import com.jarvis.ai.services.ScreenCaptureService
import com.jarvis.ai.voice.VoiceInput
import com.jarvis.ai.voice.VoiceOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class JarvisViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    val messages = MutableStateFlow<List<ChatMsg>>(emptyList())
    val busy = MutableStateFlow(false)
    val activeAgent = MutableStateFlow("—")
    val pendingAction = MutableStateFlow<String?>(null)
    val tasks = MutableStateFlow<List<String>>(emptyList())
    val listening = MutableStateFlow(false)
    val voiceError = MutableStateFlow<String?>(null)
    val tts = VoiceOutput(app) { store.femaleVoice }
    private var voice: VoiceInput? = null

    init {
        messages.value = store.chat()
        tasks.value = store.tasks()
    }

    private fun add(role: String, text: String) {
        val list = (messages.value + ChatMsg(role, text)).takeLast(100)
        messages.value = list
        store.saveChat(list)
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        add("user", t)
        if (!store.bossOn) {
            add("ai", "Gemini Boss is OFF. I can only run local screen actions. Turn the Boss ON from Dashboard/Settings.")
            return
        }
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val agent = Boss.route(t)
                activeAgent.value = agent.name
                val reply = Gemini.generate(store.apiKey, store.model, Boss.systemPrompt(agent), t)
                val action = extractAction(reply)
                val clean = reply.substringBeforeLast("ACTION:").trim()
                if (action != null && isSensitive(action)) {
                    pendingAction.value = action
                    add("ai", clean + "\n\n⚠️ This action is sensitive (payment/message/delete-like). Please confirm.")
                } else if (action != null) {
                    add("ai", clean)
                    runAction(action)
                } else {
                    add("ai", reply)
                }
                tts.speak(reply.take(400))
            } catch (e: GeminiException) {
                add("ai", "⚠️ " + e.message)
            } catch (e: Exception) {
                add("ai", "⚠️ Network error: ${e.message ?: "unknown"}. Check your internet connection.")
            }
            busy.value = false
            activeAgent.value = "—"
        }
    }

    fun confirmAction(ok: Boolean) {
        val a = pendingAction.value
        pendingAction.value = null
        if (ok && a != null) runAction(a)
    }

    private fun runAction(a: String) {
        val s = JarvisAccessibilityService.inst
        if (s == null) {
            add("ai", "Accessibility service is OFF. Enable it: phone Settings → Accessibility → JARVIS AI → ON.")
            return
        }
        val result = when {
            a == "READ" -> "Screen text:\n" + s.readScreen()
            a == "SCROLL" -> s.scrollDown()
            a == "BACK" -> s.pressBack()
            a.startsWith("TAPTEXT:") -> s.tapLabel(a.removePrefix("TAPTEXT:"))
            a.startsWith("TYPE:") -> s.typeIntoFocus(a.removePrefix("TYPE:"))
            else -> "Unknown action: $a"
        }
        add("task", result)
        add("ai", "Done. Verified result: $result")
    }

    fun analyzeScreen(prompt: String) {
        if (ScreenCaptureService.status.value != "ON") {
            add("ai", "Screen Watch is OFF. Start it from the Dashboard (it will ask for Android's consent dialog).")
            return
        }
        val svc = ScreenCaptureService.current
        if (svc == null) { add("ai", "Capture service is not ready yet. Try again in a second."); return }
        busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val answer = svc.analyze(store.apiKey, store.model, prompt)
                add("ai", answer)
                tts.speak(answer.take(400))
            } catch (e: Exception) {
                add("ai", "⚠️ ${e.message}")
            }
            busy.value = false
        }
    }

    fun toggleVoice() {
        if (listening.value) { voice?.stop(); listening.value = false; return }
        voice = VoiceInput(getApplication(), onResult = { text ->
            listening.value = false
            send(text)
        }).apply {
            onError = { e -> listening.value = false; voiceError.value = e }
        }
        voice?.start()
        listening.value = true
    }

    fun testApiKey(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val r = Gemini.generate(store.apiKey, store.model, "Reply with exactly: OK", "Say OK")
                onDone("✅ Key works. Gemini replied: ${r.trim().take(60)}")
            } catch (e: Exception) { onDone("❌ " + (e.message ?: "failed")) }
        }
    }

    fun clearTasks() { tasks.value = emptyList(); store.saveTasks(emptyList()) }
    fun shutdownVoice() { voice?.destroy(); tts.shutdown() }

    private fun extractAction(reply: String): String? {
        val idx = reply.lastIndexOf("ACTION:")
        if (idx < 0) return null
        val line = reply.substring(idx + 7).lineSequence().firstOrNull()?.trim()
        return line?.takeIf { it.isNotEmpty() && it.length < 120 }
    }

    private val sensitiveWords = listOf("pay", "buy", "purchase", "order", "checkout", "send", "delete", "transfer", "payment", "call", "ऑर्डर", "भेज", "मिटा", "भुगतान", "खरीद")
    private fun isSensitive(a: String) = sensitiveWords.any { a.lowercase().contains(it) }
}
