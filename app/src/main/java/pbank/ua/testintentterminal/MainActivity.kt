package pbank.ua.testintentterminal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.text.InputFilter
import android.util.Log
import android.view.View
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
import org.json.JSONObject
import pbank.ua.testintentterminal.databinding.ActivityMainBinding
import java.net.URLEncoder


// принцип работы
// начальные данные clid , Secret,

// а так же данные по оплате  operation("pay"/"refund" оплата/возврат), amount (сумма), purpose(описание)
// 1. все отправляется на сервер ПриватБанка для получения зашифрованного token  = apiClient.mainReq
// ответ приходит в onSendToPOST

// 2. в данном случае кнопкой полученный token отправляем на приложение Терминал = startTerminalApp(TOKEN,CALLBACK)
// ответ приходит по выполнению в процедуре  startTerminalForResult

// 3. получаем результирующий token в check_result_after_pay
// и отпраялем для расшифровки на сервер ПриватБанка  apiClient2.mainCheck(TOKEN_RESULT)
// ответ приходит в onSendToPOST и распарсиваем резултат  decodeResults()


interface ClientAPICallback {
    fun onSendToPOST(result: String, cmd:String, success: Boolean)
}

class MainActivity : AppCompatActivity(), ClientAPICallback  {

    private lateinit var binding: ActivityMainBinding
    private val settingsManager = SettingsManager(this)


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

        // ОПЛАТИТЬ
        binding.btnStart.setOnClickListener {

            //val settingsManager = SettingsManager(this)
            CALLBACK = binding.edCallback.text.toString()
            settingsManager.saveEditTextValue("edCallback", CALLBACK)

            TOKEN = binding.edJwt.text.toString();

            binding.edResult.setText("")

            if (TOKEN.isNotBlank()) {
                startTerminalApp(TOKEN,CALLBACK)
            } else {
                Toast.makeText(this,"Введите JWT токен ",  LENGTH_SHORT ).show()
            }
        }


        // Получить token
        binding.getTokenButton.setOnClickListener {

             CLID = binding.edClid.text.toString()
             SECRET = binding.edSecret.text.toString()

            if (CLID.isBlank() || SECRET.isBlank()) {
                Toast.makeText(this, "Clid и Secret должны быть заполнены", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            //save options

            settingsManager.saveEditTextValue("edClid", CLID)
            settingsManager.saveEditTextValue("edSecret", SECRET)


            binding.edJwt.setText("")
            val apiClient = ApiClient(this, this)

            var transactionId: String = ""
            val purpose: String = binding.edDescr.text.toString()  //"ТЕСТ 1ГРН"
            settingsManager.saveEditTextValue("edDescr", purpose)

            var operation: String   //"purchase" //"pay"

            if (binding.rbPay.isChecked) {
                operation="pay"
            }else{
                operation="refund"
                transactionId = binding.edTrans.text.toString()
                if (transactionId.isBlank()){
                    Toast.makeText(this,"Введите номер транзакции",  LENGTH_SHORT ).show()
                    return@setOnClickListener
                }
            }

            val amount: Double =  binding.edSumma.text.toString().toDouble() //1.00
            settingsManager.saveEditTextValue("edSumma", amount.toString())


            apiClient.mainReq(operation,amount,purpose,transactionId)
            //binding.edJwt.setText(response)
        }

        // Проверить результат
        binding.btnCheckResult.setOnClickListener {
           if (!binding.edJwt.text.isNullOrBlank()){
               val apiClient2 = ApiClient(this, this)
               apiClient2.mainCheck(TEST_TOKEN_RESULT)  // возврат результата в onSendToPOST
           }
        }




        // выбор Оплата / возврат
        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPay -> {
                    binding.btnStart.text = this.getString(R.string.pay) // "Оплата"
                    binding.btnStart.setBackgroundColor(this.getColor(R.color.green_500))

                    binding.grTranslbl.visibility= View.GONE

                    binding.grDescr.visibility=View.VISIBLE
                }
                R.id.rbRefund -> {

                    binding.grDescr.visibility= View.GONE

                    binding.btnStart.text = this.getString(R.string.refund) // "Возврат"
                    binding.btnStart.setBackgroundColor(this.getColor(R.color.orange_500))
                    binding.grTranslbl.visibility=View.VISIBLE
                }
            }
        }




        //load options

        CLID = settingsManager.loadEditTextValue("edClid").toString()
        binding.edClid.setText(CLID)
        SECRET = settingsManager.loadEditTextValue("edSecret").toString()
        binding.edSecret.setText(SECRET)
        CALLBACK = settingsManager.loadEditTextValue("edCallback").toString()
        binding.edCallback.setText(CALLBACK)
        val d = settingsManager.loadEditTextValue("edDescr").toString()
         if (d.isNotBlank()){
             DESCR = d
         }
        binding.edDescr.setText(DESCR)
        val ss = settingsManager.loadEditTextValue("edSumma").toString()
         if (ss.isNotBlank()){
             SUMMA = ss.toDouble()
             binding.edSumma.setText(SUMMA.toString())
         }

    }  // end initView()


    // функция проверки результата
    private fun check_result_after_pay(tresult: String){
        TOKEN_RESULT=""

        if(tresult.isBlank()){
            return@check_result_after_pay
        }

        val content = JSONObject(tresult)
             if (content.getBoolean("result")){
                 TOKEN_RESULT=content.getString("token")
             }else{
                 binding.edResult.append("\nresult = false")
             }


       if (TOKEN_RESULT.isNotBlank()){
        val apiClient2 = ApiClient(this, this)
        apiClient2.mainCheck(TOKEN_RESULT) // возврат результата в onSendToPOST
       }else{
           binding.edResult.append("\nTOKEN_RESULT is empty")
       }
    }


    // После POST на сервер или для получения token или для расшифровки оплаты
    override fun onSendToPOST(result: String, cmd:String, success: Boolean) {
        Log.d(TAG,"result: $result")
        //binding.edJwt.setText(result)
        var res="OK"
        if (!success){res="NO"}

        if  ((res=="OK") && (cmd=="token")) {
            TOKEN = result

        }

        if ((res=="OK") && (cmd=="check")) {
            CHECK=result
            decodeResults()
        }

        runOnUiThread {
            if (cmd=="token") {binding.edJwt.setText(result)}
            binding.edResult.append("\n$cmd: $res\n$result")
        }

    }

    // расшифровка резултатов и ответа от банка
    private fun decodeResults(){
        if (CHECK.isBlank()) {
            return@decodeResults
        }

        val content = JSONObject(CHECK)
         if (content.getBoolean("success")){

             val pay = content.getJSONObject("pay")
             val rrn = pay.getString("rrn")
             val approval_code = pay.getString("approval_code")
             val summa = pay.getString("amount_full")
             val payment_system = pay.getString("payment_system")
             val card = pay.getString("masked_pan")
             val nom_termnal = pay.getString("merchant")
             val transaction_id = pay.getString("transaction_id")
             var receipt = pay.getString("receipt")
             receipt = removeHtmlTagsAndAddNewLines(receipt)
             val res= "Terminal: $nom_termnal\n"+
                      "payment_system: $payment_system\n"+
                      "Card: $card\n"+
                      "Summa: $summa\n"+
                      "RRN: $rrn \n" +
                      "approval_code: $approval_code\n" +
                      "transaction_id: $transaction_id\n"

             runOnUiThread {
                 binding.edResult.append("\nResult operation: \n$res\n")
                 binding.edResult.append("\nReceipt: \n$receipt")

             }


         }

    }

    // удаление HTML тегов
    private fun removeHtmlTagsAndAddNewLines(input: String): String {
        // Удаляем все HTML-теги
        val withoutTags = input.replace(Regex("<.*?>"), "\n")


        val formatted = withoutTags
            .replace(Regex("\n\n"), "\n")
            //.replace(Regex("<br>"), "\n")

        return formatted
    }

    // запуск Терминала
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

    // после выполнения оплаты Тераинал
    private val startTerminalForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val intent = result.data
                if (intent != null) {
                    val tresult = intent.getStringExtra("result").toString()
                    Log.d(TAG,"Intent Transaction result = $tresult")
                    binding.edResult.append("\n$tresult")
                    check_result_after_pay(tresult)


                } else {
                    Log.d(TAG,"intent = NULL")
                    binding.edResult.append("\nintent = NULL")
                }
            } else {
                Log.d(TAG, "result != RESULT_OK")
                binding.edResult.append("\nresult != RESULT_OK")
            }
    }

    // глобавльные переменные
    companion object {
        const val TAG = "MainActivity"
        const val DEEP_LINK_URL = "nfcterminal://executor"
        var CLID :String = ""
        var SECRET :String = ""
        var TOKEN :String = ""
        var TOKEN_RESULT :String = ""
        var CALLBACK :String = ""
        var DESCR :String = "Тестовый платеж"
        var SUMMA :Double = 1.00
        var CHECK  :String = ""
        var TEST_TOKEN_RESULT :String = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpbnRlZ3JhdG9yX2lkIjoiMjk2NTIwNDYxNiIsIm9wZXJhdGlvbiI6InBheSIsImV4cCI6MTY5MjQ1MzIzOCwiYW1vdW50IjoxLCJoYXNoIjoiNGIwNTRmOGRiZGUxMzUzNDhmODM1MmMzZjJkYWMwYWZjMDA0OWViYyIsInB1cnBvc2UiOiJcdTA0MjJcdTA0MzVcdTA0NDFcdTA0NDJcdTA0M2VcdTA0MzJcdTA0NGJcdTA0MzkgXHUwNDNmXHUwNDNiXHUwNDMwXHUwNDQyXHUwNDM1XHUwNDM2In0.GFfECks0OVEKTFZgykkHtUkNV8RjkgqZ_izYeF3FmFw"

    }

}