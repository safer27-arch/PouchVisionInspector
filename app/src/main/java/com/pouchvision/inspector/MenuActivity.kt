package com.pouchvision.inspector

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMenuBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /*
         * 종합검사
         * 아직 미구현
         */
        binding.btnTotalInspection.setOnClickListener {

            Toast.makeText(
                this,
                "종합검사는 각 검사 기능 완성 후 연결할 예정입니다.",
                Toast.LENGTH_LONG
            ).show()
        }


        /*
         * Bottom Corner
         *
         * 현재 잘 작동하는
         * MainActivity 검사 화면으로 이동
         */
        binding.btnBottomCorner.setOnClickListener {

            val intent =
                Intent(
                    this,
                    MainActivity::class.java
                )

            startActivity(intent)
        }


        /*
         * Seal 검사
         */
        binding.btnSealInspection.setOnClickListener {

            Toast.makeText(
                this,
                "Seal 검사 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }


        /*
         * Forming 검사
         */
        binding.btnFormingInspection.setOnClickListener {

            Toast.makeText(
                this,
                "Forming 검사 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }


        /*
         * Tab 검사
         */
        binding.btnTabInspection.setOnClickListener {

            Toast.makeText(
                this,
                "Tab 검사 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }


        /*
         * 분해 검사
         */
        binding.btnDisassemblyInspection.setOnClickListener {

            Toast.makeText(
                this,
                "분해 검사 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }


        /*
         * 검사 결과 / 이력
         */
        binding.btnHistory.setOnClickListener {

            Toast.makeText(
                this,
                "검사 이력 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
