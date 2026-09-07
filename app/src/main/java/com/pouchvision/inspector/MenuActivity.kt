package com.pouchvision.inspector

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityMenuBinding

class MenuActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMenuBinding

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityMenuBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setupButtons()
    }

    private fun setupButtons() {

        /*
         * =====================================================
         * 종합검사
         * =====================================================
         */
        binding.btnTotalInspection.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    TotalInspectionActivity::class.java
                )
            )
        }

        /*
         * =====================================================
         * Bottom Corner 검사
         * =====================================================
         */
        binding.btnBottomCorner.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                )
            )
        }

        binding.btnBottomCornerGuide.setOnClickListener {

            openGuide(
                GuideActivity.TYPE_BOTTOM_CORNER
            )
        }

        /*
         * =====================================================
         * Seal 검사
         * =====================================================
         */
        binding.btnSealInspection.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    SealActivity::class.java
                )
            )
        }

        binding.btnSealGuide.setOnClickListener {

            openGuide(
                GuideActivity.TYPE_SEAL
            )
        }

        /*
         * =====================================================
         * Forming 검사
         * =====================================================
         */
        binding.btnFormingInspection.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    FormingActivity::class.java
                )
            )
        }

        binding.btnFormingGuide.setOnClickListener {

            openGuide(
                GuideActivity.TYPE_FORMING
            )
        }

        /*
         * =====================================================
         * Tab 검사
         * =====================================================
         */
        binding.btnTabInspection.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    TabActivity::class.java
                )
            )
        }

        binding.btnTabGuide.setOnClickListener {

            openGuide(
                GuideActivity.TYPE_TAB
            )
        }

        /*
         * =====================================================
         * 분해검사
         * =====================================================
         */
        binding.btnDisassemblyInspection.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    DisassemblyActivity::class.java
                )
            )
        }

        /*
         * =====================================================
         * 검사 이력
         * =====================================================
         */
        binding.btnHistory.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    HistoryActivity::class.java
                )
            )
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
