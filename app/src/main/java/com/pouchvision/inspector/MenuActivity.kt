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

        binding =
            ActivityMenuBinding.inflate(layoutInflater)

        setContentView(binding.root)


        /*
         * =====================================
         * 종합검사
         * =====================================
         *
         * Bottom Corner / Seal / Forming /
         * Tab / 분해검사가 완성된 후
         * 하나의 검사 흐름으로 통합할 예정
         */
        binding.btnTotalInspection.setOnClickListener {

            Toast.makeText(
                this,
                "종합검사는 개별 검사 완성 후 연결합니다.",
                Toast.LENGTH_LONG
            ).show()
        }


        /*
         * =====================================
         * Bottom Corner 주름 검사
         * =====================================
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
         * =====================================
         * Seal 검사
         * =====================================
         *
         * 새로 만든 SealActivity 실행
         */
        binding.btnSealInspection.setOnClickListener {

            val intent =
                Intent(
                    this,
                    SealActivity::class.java
                )

            startActivity(intent)
        }


        /*
         * =====================================
         * Forming 검사
         * =====================================
         */
        binding.btnFormingInspection.setOnClickListener {

            Toast.makeText(
                this,
                "Forming 검사 기능은 다음 단계에서 추가합니다.",
                Toast.LENGTH_SHORT
            ).show()
        }


        /*
         * =====================================
         * Tab 검사
         * =====================================
         */
        binding.btnTabInspection.setOnClickListener {

            Toast.makeText(
                this,
                "Tab 검사 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }


        /*
         * =====================================
         * 분해 검사
         * =====================================
         */
        binding.btnDisassemblyInspection.setOnClickListener {

            Toast.makeText(
                this,
                "분해 검사 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }


        /*
         * =====================================
         * 검사 결과 / 이력
         * =====================================
         */
        binding.btnHistory.setOnClickListener {

            Toast.makeText(
                this,
                "검사 결과 및 이력관리 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
