document.addEventListener('DOMContentLoaded', function() {
    // ============================================================
    // 1. intl-tel-input 초기화 (개인 전화번호)
    // ============================================================
    const personalPhoneInput = document.querySelector("#personalPhone");
    const personalHiddenInput = document.querySelector("#personalCountryCode");

    // [수정] 한국 자동 설정 제거 (기본값 US 또는 라이브러리 기본)
    const itiPersonal = window.intlTelInput(personalPhoneInput, {
        initialCountry: "au",
        separateDialCode: true,
        utilsScript: "https://cdn.jsdelivr.net/npm/intl-tel-input@19.2.16/build/js/utils.js"
    });

    // 국가 변경 시 Hidden Field 업데이트
    personalPhoneInput.addEventListener('countrychange', function() {
        const countryData = itiPersonal.getSelectedCountryData();
        personalHiddenInput.value = "+" + countryData.dialCode;
    });
    // 초기값 세팅
    personalHiddenInput.value = "+" + itiPersonal.getSelectedCountryData().dialCode;


    // ============================================================
    // 2. Country Select 박스 자동 생성 및 연동
    // ============================================================
    const addressDropdown = document.querySelector("#memberCountrySelect");
    const countryData = window.intlTelInputGlobals.getCountryData();

    // (1) 국가 데이터로 <option> 태그 생성
    for (let i = 0; i < countryData.length; i++) {
        const country = countryData[i];
        const optionNode = document.createElement("option");
        optionNode.value = country.iso2;
        const textNode = document.createTextNode(country.name);
        optionNode.appendChild(textNode);
        addressDropdown.appendChild(optionNode);
    }

    // [수정] 기본값 'kr' 설정 코드 삭제됨 (HTML의 'Select Country'가 기본 선택)
    //addressDropdown.value = "kr";

    // (2) Country Select 변경 시 -> Phone Input 국가 변경 연동
    addressDropdown.addEventListener('change', function() {
        const selectedCountryCode = this.value;
        if (selectedCountryCode) {
            itiPersonal.setCountry(selectedCountryCode);
            const newCountryData = itiPersonal.getSelectedCountryData();
            personalHiddenInput.value = "+" + newCountryData.dialCode;
        }
    });

    // (3) Phone Input 변경 시 -> Country Select 연동
    personalPhoneInput.addEventListener('countrychange', function() {
        const newCountryData = itiPersonal.getSelectedCountryData();
        personalHiddenInput.value = "+" + newCountryData.dialCode;
        // 드롭다운에 해당 국가가 있다면 선택 변경
        addressDropdown.value = newCountryData.iso2;
    });


    // ============================================================
    // 3. intl-tel-input 초기화 (회사 전화번호)
    // ============================================================
    const companyPhoneInput = document.querySelector("#companyPhone");
    const companyHiddenInput = document.querySelector("#companyCountryCode");

    // 전역 변수로 노출 (Business Country 변경 시 접근 필요)
    window.itiCompany = window.intlTelInput(companyPhoneInput, {
        initialCountry: "au",
        separateDialCode: true,
        utilsScript: "https://cdn.jsdelivr.net/npm/intl-tel-input@19.2.16/build/js/utils.js"
    });

    companyPhoneInput.addEventListener('countrychange', function() {
        const countryData = window.itiCompany.getSelectedCountryData();
        companyHiddenInput.value = "+" + countryData.dialCode;
    });
    companyHiddenInput.value = "+" + window.itiCompany.getSelectedCountryData().dialCode;


    // ============================================================
    // 4. Business Details 자동 설정 로직
    // ============================================================
    const businessCountrySelect = document.getElementById('businessCountry');
    const companyTimezoneSelect = document.getElementById('companyTimezone');
    const companyCurrencySelect = document.getElementById('companyCurrency');
    const australiaFields = document.getElementById('australiaFields');

    const countryPresets = {
        "Australia": { timezone: "UTC_PLUS_10", currency: "AUD", iso: "au" },
        "United Kingdom": { timezone: "UTC_PLUS_00", currency: "GBP", iso: "gb" }
    };

    businessCountrySelect.addEventListener('change', function() {
        const selectedCountry = this.value;
        const preset = countryPresets[selectedCountry];

        // 선택된 국가가 호주일 때만 필드 노출 (클래스 토글)
        if (selectedCountry === "Australia") {
            australiaFields.classList.add('show');
        } else {
            australiaFields.classList.remove('show');
        }

        if (preset) {
            // Timezone
            if (querySelectorContainsValue(companyTimezoneSelect, preset.timezone)) {
                companyTimezoneSelect.value = preset.timezone;
            }
            // Currency
            companyCurrencySelect.value = preset.currency;
            // Company Phone
            window.itiCompany.setCountry(preset.iso);

            const newCountryData = window.itiCompany.getSelectedCountryData();
            companyHiddenInput.value = "+" + newCountryData.dialCode;
        }
    });

    // ============================================================
    // 5. Business Industry 검색 로직
    // ============================================================

    let industryData = [];

    // 1. 호주 산업 코드 데이터 로드 (경로는 실제 JSON 파일이나 API 위치로 맞춰주세요)
    // 정적 파일 위치 예시: src/main/resources/static/data/australia-bic.json
    fetch('/data/australia-bic.json')
        .then(response => response.json())
        .then(data => {
            industryData = data;
            // [로그 1] 데이터가 정상적으로 로드되었는지, 총 몇 개인지 확인
            console.log("✅ [데이터 로드 완료] 총 항목 수:", industryData.length);
            console.log("✅ [데이터 로드 샘플] 첫 3개 확인:", industryData.slice(0, 3));
        })
        .catch(error => console.error('Error loading industry codes:', error));

    const industrySearch = document.getElementById('industrySearch');
    const industryCode = document.getElementById('industryCode');
    const suggestionsBox = document.getElementById('industrySuggestions');

    if (industrySearch) {
        // 2. 키보드 입력 시 필터링
        industrySearch.addEventListener('input', function() {
            const query = this.value.toLowerCase().trim();
            suggestionsBox.innerHTML = '';

            if (!query) {
                suggestionsBox.style.display = 'none';
                // 입력창을 비우면 hidden 값도 초기화
                industryCode.value = '';
                return;
            }

            // 입력된 키워드가 설명이나 코드에 포함된 것만 필터링 (렌더링 성능을 위해 최대 50개만 표시)
            const filtered = industryData.filter(item =>
                item.description.toLowerCase().includes(query) ||
                item.code.includes(query)
            ).slice(0, 50);

            if (filtered.length > 0) {
                filtered.forEach(item => {
                    const li = document.createElement('li');
                    // 텍스트는 "업종 설명 (코드)" 형태로 표시
                    li.textContent = `${item.description} (${item.code})`;

                    // 항목 클릭 시 입력칸에 값 세팅 후 창 닫기
                    li.onclick = () => {
                        industrySearch.value = item.description; // 화면에는 설명 표시
                        industryCode.value = item.code;          // hidden에는 코드 저장
                        suggestionsBox.style.display = 'none';
                    };
                    suggestionsBox.appendChild(li);
                });
                suggestionsBox.style.display = 'block';
            } else {
                suggestionsBox.style.display = 'none';
            }
        });

        // 3. 외부 영역 클릭 시 자동완성 창 닫기
        document.addEventListener('click', function(e) {
            if (e.target !== industrySearch) {
                suggestionsBox.style.display = 'none';
            }
        });
    }

    // 엔터키 처리
    document.getElementById('signupForm').addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            if (e.target.id === 'abnInput') window.searchAbn();
        }
    });
});

// 헬퍼 함수
function querySelectorContainsValue(selectElement, value) {
    for (let i = 0; i < selectElement.options.length; i++) {
        if (selectElement.options[i].value === value) return true;
    }
    return false;
}

// ============================================================
// 5. Wizard Form 및 유효성 검사 (Global Functions)
// ============================================================
let currentStep = 1;

window.showStep = function(step) {
    document.querySelectorAll('.step-section').forEach(el => el.classList.remove('active'));
    document.getElementById('step' + step).classList.add('active');

    const title = document.getElementById('pageTitle');
    if (step === 2) title.innerText = "Verification";
    else if (step === 3) title.innerText = "Business Details";
    else title.innerText = "Sign up";

    currentStep = step;
};

window.nextStep = function(targetStep) {
    window.showStep(targetStep);
};

window.prevStep = function(targetStep) {
    window.showStep(targetStep);
};

// ============================================================
// 6. 유효성 검사 및 AJAX
// ============================================================
window.resetEmailError = function() {
    const emailMsg = document.getElementById('emailErrorMsg');
    const emailInput = document.getElementById('personalEmail');
    emailMsg.style.display = 'none';
    emailInput.style.borderColor = "#E0E0E0";
};

window.resetPasswordError = function() {
    document.getElementById('pwError').style.display = 'none';
};

window.validateStep1AndNext = async function() {
    const step1 = document.getElementById('step1');
    const inputs = step1.querySelectorAll('input[required], select[required]');
    let valid = true;

    // 1. 기본 필드 체크
    inputs.forEach(input => {
        if (!input.value || (input.type === 'checkbox' && !input.checked)) {
            valid = false;
            input.style.borderColor = "red";
            setTimeout(() => input.style.borderColor = "#E0E0E0", 3000);
        }
    });

    if (!valid) {
        alert("Please fill in all required fields.");
        return;
    }

    // 2. 비밀번호 일치 확인
    const pw = document.getElementById('password').value;
    const checkPw = document.getElementById('checkPassword').value;
    if (pw !== checkPw) {
        document.getElementById('pwError').style.display = 'block';
        document.getElementById('checkPassword').focus();
        return;
    }

    // 3. 이메일 중복 확인 (AJAX)
    const emailInput = document.getElementById('personalEmail');
    const emailVal = emailInput.value.trim();
    const emailMsg = document.getElementById('emailErrorMsg');

    try {
        document.body.style.cursor = 'wait';
        const response = await fetch(`/api/auth/check-email?email=${encodeURIComponent(emailVal)}`);
        const isAvailable = await response.json();

        if (isAvailable) {
            window.nextStep(2);
            window.startTimer();
            sendVerificationEmail();
        } else {
            emailMsg.innerText = "Email is already registered.";
            emailMsg.style.display = 'block';
            emailInput.style.borderColor = "red";
            emailInput.focus();
        }
    } catch (error) {
        console.error(error);
        alert("Error checking email availability.");
    } finally {
        document.body.style.cursor = 'default';
    }
};

// ============================================================
// 7. ABN 검색 및 검증
// ============================================================
let isAbnVerified = false;

window.searchAbn = function() {
    const abnInput = document.getElementById('abnInput');
    const resultMsg = document.getElementById('abnResultMsg');

    // Hidden Inputs
    const hEntityName = document.getElementById('hiddenEntityName');
    const hEntityTypeName = document.getElementById('hiddenEntityTypeName');
    const hAbnStatus = document.getElementById('hiddenAbnStatus');
    const hPostcode = document.getElementById('hiddenAddressPostcode');
    const hState = document.getElementById('hiddenAddressState');
    const hGst = document.getElementById('hiddenGst');

    const cleanAbn = abnInput.value.replace(/[^0-9]/g, '');
    abnInput.value = cleanAbn;
    resultMsg.style.display = 'none';

    if (cleanAbn.length !== 11) {
        resultMsg.innerText = "Please enter a valid 11-digit ABN.";
        resultMsg.style.color = 'red';
        resultMsg.style.display = 'block';
        isAbnVerified = false;
        return;
    }

    document.body.style.cursor = 'wait';

    fetch(`/api/auth/abn-lookup?abn=${cleanAbn}`)
        .then(response => {
            if (response.status === 409) throw new Error("DUPLICATE_ABN");
            if (!response.ok) throw new Error("Network response was not ok");
            return response.json();
        })
        .then(data => {
            if (data.EntityName) {
                resultMsg.innerText = `ABN Found - ${data.EntityName}`;
                resultMsg.style.color = 'green';
                resultMsg.style.display = 'block';
                isAbnVerified = true;

                hEntityName.value = data.EntityName || '';
                hEntityTypeName.value = data.EntityTypeName || '';
                hAbnStatus.value = data.AbnStatus || '';
                hPostcode.value = data.AddressPostcode || '';
                hState.value = data.AddressState || '';
                hGst.value = data.Gst || '';
            } else {
                throw new Error("No entity found");
            }
        })
        .catch(error => {
            console.error('Error:', error);
            if (error.message === "DUPLICATE_ABN") {
                resultMsg.innerText = "ABN already exists.";
                resultMsg.style.color = 'red';
            } else {
                resultMsg.innerText = "Invalid ABN or Service Unavailable";
                resultMsg.style.color = 'red';
            }
            resultMsg.style.display = 'block';

            // Reset hidden values
            hEntityName.value = '';
            hEntityTypeName.value = '';
            hAbnStatus.value = '';
            hPostcode.value = '';
            hState.value = '';
            hGst.value = '';
            isAbnVerified = false;
        })
        .finally(() => {
            document.body.style.cursor = 'default';
        });
};

window.resetAbnResult = function() {
    isAbnVerified = false;
    const resultMsg = document.getElementById('abnResultMsg');
    resultMsg.style.display = 'none';
    resultMsg.innerText = '';

    document.getElementById('hiddenEntityName').value = '';
    document.getElementById('hiddenEntityTypeName').value = '';
    document.getElementById('hiddenAbnStatus').value = '';
    document.getElementById('hiddenAddressPostcode').value = '';
    document.getElementById('hiddenAddressState').value = '';
    document.getElementById('hiddenGst').value = '';
};

// ============================================================
// [신규] 이메일 인증 및 타이머 로직
// ============================================================
let timerInterval;
let timeLeft = 180; // 3분 (초 단위)

// 1. 타이머 시작 함수
window.startTimer = function() {
    clearInterval(timerInterval); // 기존 타이머 초기화
    timeLeft = 180; // 3분 리셋
    updateTimerDisplay();

    const resendLink = document.getElementById('resendLink');
    const timerDisplay = document.getElementById('timerDisplay');

    // 재발송 버튼 비활성화 스타일 (선택사항)
    resendLink.style.pointerEvents = 'none';
    resendLink.style.opacity = '0.5';
    resendLink.style.cursor = 'default';
    timerDisplay.classList.remove('expired');

    timerInterval = setInterval(() => {
        timeLeft--;
        updateTimerDisplay();

        if (timeLeft <= 170) {
            resendLink.style.pointerEvents = 'auto';
            resendLink.style.opacity = '1';
            resendLink.style.cursor = 'pointer';
        }
        if (timeLeft <= 0) {
            clearInterval(timerInterval);
            timerDisplay.classList.add('expired');
            // alert("Verification code expired."); // 필요 시 주석 해제
        }
    }, 1000);
};

// 2. 타이머 화면 업데이트
function updateTimerDisplay() {
    const minutes = Math.floor(timeLeft / 60);
    const seconds = timeLeft % 60;
    const display = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    document.getElementById('timerDisplay').innerText = display;
}

// 3. 인증번호 발송 요청 (AJAX)
async function sendVerificationEmail() {
    const email = document.getElementById('personalEmail').value;
    document.getElementById('displayEmail').innerText = email; // 안내 문구에 이메일 표시

    try {
        const response = await fetch('/api/auth/send-verification', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `email=${encodeURIComponent(email)}`
        });

        if (!response.ok) {
            // 실패 시 사용자에게만 알림 (타이머는 이미 돌아가고 있음)
            console.error("Email sending failed");
            // alert("Failed to send verification code."); // 필요 시 주석 해제
        }
    } catch (error) {
        console.error("Error sending email:", error);
        alert("An error occurred. Please check your connection.");
    }
}

// 4. 재발송 버튼 클릭 시
window.resendCode = function() {
    if(!confirm("Resend the verification code?")) return;

    document.getElementById('verificationCode').value = ''; // 입력창 초기화
    document.getElementById('verifyErrorMsg').style.display = 'none';

    // 버튼 누르자마자 타이머 즉시 리셋 & 시작
    window.startTimer();
    // 이메일 재발송 및 타이머 리셋
    sendVerificationEmail();
};

// 6. [신규] 인증번호 확인 및 Step 3 이동
window.verifyAndNext = async function() {
    const code = document.getElementById('verificationCode').value.trim();
    const email = document.getElementById('personalEmail').value;
    const errorMsg = document.getElementById('verifyErrorMsg');

    if (!code) {
        errorMsg.innerText = "Please enter the code.";
        errorMsg.style.display = 'block';
        return;
    }

    // ==========================================
    // [테스트용] 마스터 코드 '000000' 입력 시 무사통과
    // ==========================================
    if (code === "000000") {
        console.log("Test code used. Bypassing email verification.");
        clearInterval(timerInterval); // 타이머 종료
        window.showStep(3);           // Step 3 이동
        return;                       // 함수 종료 (서버 요청 안 함)
    }

    // 시간 초과 체크 (프론트엔드 측 1차 방어)
    if (timeLeft <= 0) {
        errorMsg.innerText = "Code expired. Please resend.";
        errorMsg.style.display = 'block';
        return;
    }

    try {
        const response = await fetch('/api/auth/verify-code', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `email=${encodeURIComponent(email)}&code=${encodeURIComponent(code)}`
        });

        const isValid = await response.json(); // boolean return 가정

        if (isValid) {
            clearInterval(timerInterval); // 타이머 종료
            window.showStep(3); // Step 3 이동 (Back 버튼 없음)
        } else {
            errorMsg.innerText = "Invalid verification code.";
            errorMsg.style.display = 'block';
        }
    } catch (error) {
        console.error("Verification error:", error);
        alert("Verification failed. Please try again.");
    }
};

window.submitSignup = function() {
    const step3 = document.getElementById('step3');
    // 1. 모든 required 필드를 가져옵니다.
    const allRequiredInputs = Array.from(step3.querySelectorAll('input[required], select[required]'));

    // 2. 실제로 검사할 필드만 걸러냅니다.
    const inputsToValidate = allRequiredInputs.filter(input => {
        // 해당 input이 australiaFields 안에 있는지 확인합니다.
        const ausParent = input.closest('#australiaFields');

        if (ausParent) {
            // 호주 필드 안에 있다면, 호주 필드가 열려있을 때(.show)만 검사 대상에 포함합니다.
            return ausParent.classList.contains('show');
        }

        // 특정 국가 필드 안에 있지 않은 공통 필드(Country 등)는 무조건 검사 대상에 포함합니다.
        return true;
    });

    let valid = true;

    // 3. 걸러진 필드들만 유효성 검사를 진행합니다.
    inputsToValidate.forEach(input => {
        if (!input.value) {
            valid = false;
            input.style.borderColor = "red";
        } else {
            input.style.borderColor = "var(--border-color)"; // 통과 시 테두리 색상 복구
        }
    });

    if (!valid) {
        alert("Please fill in all business details.");
        return;
    }

    const businessCountry = document.getElementById('businessCountry').value;
    if (businessCountry === "Australia" && !isAbnVerified) {
        alert("Please search and verify your ABN first.");
        document.getElementById('abnInput').focus();
        return;
    }

    document.getElementById('signupForm').submit();
};