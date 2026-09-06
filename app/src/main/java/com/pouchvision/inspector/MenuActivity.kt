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
                "종합검사는 개별 검사 기능 완성 후 연결합니다.",
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

        binding.btnBottomCornerGuide.setOnClickListener {

            openGuide(
                GuideActivity.TYPE_BOTTOM_CORNER
            )
        }

        binding.btnSealInspection.setOnClickListener {

            val intent =
                Intent(
                    this,
                    SealActivity::class.java
                )

            startActivity(intent)
        }

        binding.btnSealGuide.setOnClickListener {

            openGuide(
                GuideActivity.TYPE_SEAL
            )
        }

        binding.btnFormingInspection.setOnClickListener {

            val intent =
                Intent(
                    this,
                    FormingActivity::class.java
                )

            startActivity(intent)
        }

        binding.btnFormingGuide.setOnClickListener {

            openGuide(
                GuideActivity.TYPE_FORMING
            )
        }

        binding.btnTabInspection.setOnClickListener {

            val intent =
                Intent(
                    this,
                    TabActivity::class.java
                )

            startActivity(intent)
        }

        binding.btnTabGuide.setOnClickListener {

            openGuide(
                GuideActivity.TYPE_TAB
            )
        }

        binding.btnDisassemblyInspection.setOnClickListener {

            val intent =
                Intent(
                    this,
                    DisassemblyActivity::class.java
                )

            startActivity(intent)
        }

        binding.btnHistory.setOnClickListener {

            val intent =
                Intent(
                    this,
                    HistoryActivity::class.java
                )

            startActivity(intent)
        }
    }

    private fun openGuide(
        guideType: String
    ) {

        val intent =
            Intent(
                this,
                GuideActivity::class.java
            )

        intent.putExtra(
            GuideActivity.EXTRA_GUIDE_TYPE,
            guideType
        )

        startActivity(intent)
    }
}
