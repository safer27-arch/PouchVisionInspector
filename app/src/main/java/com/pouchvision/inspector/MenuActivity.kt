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

        binding.btnTotalInspection.setOnClickListener {
            Toast.makeText(
                this,
                "종합검사는 개별 검사 완성 후 연결합니다.",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnBottomCorner.setOnClickListener {
            val intent =
                Intent(
                    this,
                    MainActivity::class.java
                )

            startActivity(intent)
        }

        binding.btnSealInspection.setOnClickListener {
            val intent =
                Intent(
                    this,
                    SealActivity::class.java
                )

            startActivity(intent)
        }

        binding.btnFormingInspection.setOnClickListener {
            val intent =
                Intent(
                    this,
                    FormingActivity::class.java
                )

            startActivity(intent)
        }

        binding.btnTabInspection.setOnClickListener {
            Toast.makeText(
                this,
                "Tab 검사 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnDisassemblyInspection.setOnClickListener {
            Toast.makeText(
                this,
                "분해 검사 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnHistory.setOnClickListener {
            Toast.makeText(
                this,
                "검사 결과 및 이력관리 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
