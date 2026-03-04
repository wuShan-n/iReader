package com.ireader.feature.reader.presentation

object ReaderHardwareKeyBridge {
    @Volatile
    private var volumeKeyListener: ((keyCode: Int, action: Int) -> Boolean)? = null

    fun setVolumeKeyListener(listener: ((keyCode: Int, action: Int) -> Boolean)?) {
        volumeKeyListener = listener
    }

    fun dispatchVolumeKey(keyCode: Int, action: Int): Boolean {
        return volumeKeyListener?.invoke(keyCode, action) == true
    }
}
