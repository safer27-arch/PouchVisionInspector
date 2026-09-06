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
         * =========================================================
         * 종합 검사
         * =========================================================
         */
        binding.btnTotalInspection.setOnClickListener {

            Toast.makeText(
                this,
                "종합검사는 개별 검사 기능 완성 후 연결합니다.",
                Toast.LENGTH_LONG
            ).show()
        }


        /*
         * =========================================================
         * BOTTOM CORNER 검사
         * =========================================================
         */
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


        /*
         * =========================================================
         * SEAL 검사
         * =========================================================
         */
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


        /*
         * =========================================================
         * FORMING 검사
         * =========================================================
         */
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


        /*
         * =========================================================
         * TAB 검사
         * =========================================================
         */
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


        /*
         * =========================================================
         * 분해 검사
         * =========================================================
         */
        binding.btnDisassemblyInspection.setOnClickListener {

            Toast.makeText(
                this,
                "분해 검사 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }


        /*
         * =========================================================
         * 검사 결과 / 이력
         * =========================================================
         */
        binding.btnHistory.setOnClickListener {

            Toast.makeText(
                this,
                "검사 결과 및 이력관리 기능은 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    /*
     * =========================================================
     * 공용 검사 안내 화면
     * =========================================================
     */
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
