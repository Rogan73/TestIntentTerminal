package pbank.ua.testintentterminal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.text.InputFilter
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import pbank.ua.testintentterminal.databinding.ActivityMainBinding
import java.net.URLEncoder


interface ClientAPICallback {
    fun onSendToPOST(result: String, cmd:String, success: Boolean)
}

class MainActivity : AppCompatActivity(), ClientAPICallback  {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        initView()




        supportActionBar?.apply {
            setDisplayShowHomeEnabled(true)
            setIcon(R.drawable.privat_logo) // Замените на имя вашей иконки
        }


    }

    private fun initView() {
//        binding.inputJwt.editText?.doOnTextChanged { inputText, _, _, count ->
//            if ( inputText.isNullOrBlank() ) {
//                binding.btnStart.text = "Оплатить"
//            } else {
//                binding.btnStart.text = "Выполнить операцию из JWT"
//            }
//        }



        binding.btnStart.setOnClickListener {

            val settingsManager = SettingsManager(this)
            CALLBACK = binding.edCallback.text.toString()
            settingsManager.saveEditTextValue("edCallback", CALLBACK)

            // TOKEN = binding.edJwt.text.toString();

            binding.edResult.setText("")

            if (TOKEN.isNotBlank()) {
                startTerminalApp(TOKEN,CALLBACK)
            } else {
                Toast.makeText(this,"Введите JWT токен ",  LENGTH_SHORT ).show()
            }
        }


        binding.getTokenButton.setOnClickListener {

             CLID = binding.edClid.text.toString()
             SECRET = binding.edSecret.text.toString()

            if (CLID.isBlank() || SECRET.isBlank()) {
                Toast.makeText(this, "Clid и Secret должны быть заполнены", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            //save options
            val settingsManager = SettingsManager(this)
            settingsManager.saveEditTextValue("edClid", CLID)
            settingsManager.saveEditTextValue("edSecret", SECRET)


            binding.edJwt.setText("")
            val apiClient = ApiClient(this, this)

            val transactionId: String = ""
            val purpose: String = "ТЕСТ 1ГРН"
            val operation: String ="pay"//"purchase" //"pay"
            val amount: Double = 1.00


            apiClient?.mainReq(operation,amount,purpose,transactionId)
            //binding.edJwt.setText(response)
        }

        binding.btnCheckResult.setOnClickListener {
           if (!binding.edJwt.text.isNullOrBlank()){
               val apiClient2 = ApiClient(this, this)
               apiClient2?.mainCheck(binding.edJwt.text.toString())
           }
        }

        //load options
        val settingsManager = SettingsManager(this)
        CLID = settingsManager.loadEditTextValue("edClid").toString()
        binding.edClid.setText(CLID)
        SECRET = settingsManager.loadEditTextValue("edSecret").toString()
        binding.edSecret.setText(SECRET)
        CALLBACK = settingsManager.loadEditTextValue("edCallback").toString()
        binding.edCallback.setText(CALLBACK)

    }


    override fun onSendToPOST(result: String, cmd:String, success: Boolean) {
        Log.d(TAG,"result: $result")
        //binding.edJwt.setText(result)
        var res="OK"
        if (!success){res="NO"}

        if  ((res=="OK") && (cmd=="token")) {
            TOKEN = result

        }

        if ((res=="OK") && (cmd=="check")) {
             // автоматом можно проверить результат
        }

        runOnUiThread {
            if (cmd=="token") {binding.edJwt.setText(result)}
            binding.edResult.append("$cmd: $res\n$result")
        }

    }


    private fun startTerminalApp(jwtToken: String, callback:String) {
        val launchIntent =
            packageManager.getLaunchIntentForPackage("ua.privatbank.pterminal")
        if (launchIntent != null) {
            val intent = Intent(Intent.ACTION_VIEW)
            val jwtTokenEncode = URLEncoder.encode(jwtToken, "utf-8")
            intent.data = Uri.parse(DEEP_LINK_URL+"?token=$jwtTokenEncode&callback=$callback")
            try {
                startTerminalForResult.launch(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        } else {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=ua.privatbank.pterminal")
                    )
                )
            } catch (e: android.content.ActivityNotFoundException) {
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=ua.privatbank.pterminal")
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private val startTerminalForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val intent = result.data
                if (intent != null) {
                    val tresult = intent.getStringExtra("result")
                    Log.d(TAG,"Intent Transaction result = $tresult")
                    binding.edResult.setText(tresult)
                } else {
                    Log.d(TAG,"intent = NULL")
                }
            } else {
                Log.d(TAG, "result != RESULT_OK")
            }
    }

    companion object {
        const val TAG = "MainActivity"
        const val DEEP_LINK_URL = "nfcterminal://executor"
        var CLID :String = ""
        var SECRET :String = ""
        var TOKEN :String = ""
        var CALLBACK :String = ""


    }

}