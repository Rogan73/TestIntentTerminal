package pbank.ua.testintentterminal

import android.content.Context

class SettingsManager(private val context: Context) {

    fun saveCheckboxState(key: String, isChecked: Boolean) {
        val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean(key, isChecked)
        editor.apply()
    }

    fun loadCheckboxState(key: String): Boolean {
        val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(key, false)
    }

    fun saveEditTextValue(key: String, value: String) {
        val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(key, value)
        editor.apply()
    }

    fun loadEditTextValue(key: String): String? {
        val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return sharedPreferences.getString(key, "")
    }
}
