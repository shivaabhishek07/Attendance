package com.example.attendence_1

// Logs message without accessing logTextView directly
class Logger {

    companion object {

        private var logCallback: ((String) -> Unit)? = null

        fun setLogCallback(callback: (String) -> Unit) {
            logCallback = callback
        }

        fun log(message: String) {
            logCallback?.invoke(">> $message")
        }
    }
}
