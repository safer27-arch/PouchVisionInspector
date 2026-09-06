# Pouch Vision Inspector v1.0

실제 Android 스마트폰에서 촬영하고 결과를 저장할 수 있는 1차 동작 버전입니다.

## 검사 모드
1. FORMING - 포밍/4 Corner
2. BOTTOM - 바텀 좌/우 주름
3. TAB·SEAL - 탭/실링 근접
4. PP FLOW - 박리 실링부

## 포함 기능
- CameraX 고해상도 촬영
- 검사 모드별 촬영 ROI 가이드
- 라인 / 모델 / 금형 ID 저장
- 모드/조건별 Master 이미지 등록
- Edge/Wrinkle 영상 지표 계산
- Master 대비 이미지 차이 참고값
- 자동 참고 등급 OK / WATCH / LIMIT / NG
- 작업자 최종 판정 저장
- 메모 및 이미지 경로 저장
- CSV 검사 이력

## 판정에 대한 중요한 주의
v1.0의 자동점수는 **학습 완료된 AI 판정값이 아닙니다.**
알루미늄 파우치는 반사와 촬영각 영향이 크기 때문에 현재 자동점수는 현장 데이터 수집과 기준 설정을 위한 보조 지표입니다.

사용하면서 실제 판정을 OK / WATCH / LIMIT / NG로 저장하면
다음 버전에서 이 데이터를 이용해 모드별 판정식을 고도화할 수 있습니다.

## GitHub에서 APK 만들기
프로젝트를 GitHub 저장소 루트에 업로드하면 Actions가 자동 실행됩니다.
Actions > Build Android APK > Run workflow 로 수동 실행도 가능합니다.

완료 후:
Actions 실행 결과 > Artifacts > PouchVisionInspector-v1.0-debug > 다운로드
압축을 풀면 app-debug.apk가 있습니다.
