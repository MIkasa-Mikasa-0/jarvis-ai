package com.jarvis.ai.ai

object Boss {
    enum class Agent(val label: String) {
        RESEARCH("Research"),
        CODING("Coding"),
        WEB("Web Search"),
        PLANNING("Planning")
    }

    fun route(input: String): Agent {
        val s = input.lowercase()
        return when {
            listOf("code", "kotlin", "java", "python", "debug", "error", "function", "script", "bug", "compile").any { s.contains(it) } -> Agent.CODING
            listOf("plan", "schedule", "steps", "routine", "roadmap", "todo", "योजना").any { s.contains(it) } -> Agent.PLANNING
            listOf("search", "latest", "news", "price", "who is", "what is", "कौन", "खबर", "कीमत").any { s.contains(it) } -> Agent.WEB
            else -> Agent.RESEARCH
        }
    }

    fun systemPrompt(a: Agent): String {
        val base = when (a) {
            Agent.RESEARCH -> "You are JARVIS Research agent. Give accurate, concise answers. If you are not sure, say so honestly. Reply in Hindi/Hinglish when the user writes in Hindi/Hinglish."
            Agent.CODING -> "You are JARVIS Coding agent. Write clean, working code with short explanations. Mention how to run it. Reply in Hindi/Hinglish when the user writes in Hindi/Hinglish."
            Agent.WEB -> "You are JARVIS Web agent. IMPORTANT: live browsing/search-grounding is NOT available on the free API plan, so answer from your built-in knowledge and clearly say when information may be outdated. Never pretend you browsed. Reply in Hindi/Hinglish when the user writes in Hindi/Hinglish."
            Agent.PLANNING -> "You are JARVIS Planning agent. Break goals into clear numbered steps with time estimates. Reply in Hindi/Hinglish when the user writes in Hindi/Hinglish."
        }
        return base + "\n\nIf the user asks you to operate the phone screen (read screen, tap, scroll, type, go back), end your reply with exactly one line: ACTION: READ | SCROLL | BACK | TAPTEXT:<visible label> | TYPE:<text>. Add an ACTION line ONLY when an on-screen action is truly requested."
    }
}
