document.addEventListener('DOMContentLoaded', function() {

    // 초기 인덱스 (이미 0번 행이 있으므로 1부터 시작)
    let itemIndex = (typeof window.itemIndex !== 'undefined') ? window.itemIndex : 1;

    // ============================================================
    // [공통 함수] Product Select2 초기화 및 이벤트 연결
    // ============================================================
    window.initProductSelect2 = function(targetElement) {
        if (typeof $ !== 'undefined') {
            $(targetElement).select2({
                placeholder: "Select Product",
                allowClear: true,
                width: '100%' // 테이블 셀 너비에 맞춤
            }).on('change', function() {
                // 값이 변경되면 계산 로직 호출
                window.updateItemDetails(this);
            });
        }
    };

    // Contact Select2 초기화 코드
    if (typeof $ !== 'undefined') {
        $('#contactSelect').select2({ placeholder: "Select Client", allowClear: true, width: '100%' });
        $('#contactSelect').on('change', function() { window.updateContactDetails(this); });

        // [초기 로딩] 기존에 존재하는 모든 제품 select에 대해 Select2 적용
        $('.product-select').each(function() {
            window.initProductSelect2(this);
        });
    }

    // ============================================================
    // 1. 행 추가 함수
    // ============================================================
/*    window.addItem = function() {
        console.log("=== add 함수 실행 ===");
        const tbody = document.getElementById('invoiceItems');
        const firstRow = tbody.querySelector('.item-row');

        // 첫 번째 행 깊은 복사
        const newRow = firstRow.cloneNode(true);

        // [수정] input 뿐만 아니라 select(상품선택)도 찾아서 처리해야 함
        const inputs = newRow.querySelectorAll('input, select');
        inputs.forEach(input => {
            // 값 비우기
            input.value = '';

            // name 인덱스 업데이트 (items[0] -> items[1])
            if (input.name) {
                input.name = input.name.replace(/\[\d+\]/, `[${itemIndex}]`);
            }
        });

        // Amount 초기화 (readonly input 이므로 value를 0.00으로)
        const amountInput = newRow.querySelector('.row-amount');
        if (amountInput) {
            amountInput.value = '0.00';
        }
        // 수정
        const amountDisplay = newRow.querySelector('.amount-display');
        if (amountDisplay) {
            amountDisplay.textContent = '0.00';
        }

        // 삭제 버튼 추가
        const deleteCell = newRow.lastElementChild;
        deleteCell.innerHTML = '<button type="button" onclick="removeRow(this)" style="color:red; border:none; background:none; cursor:pointer;">&times;</button>';

        // 테이블 추가
        tbody.appendChild(newRow);
        itemIndex++;
    };*/
    window.addItem = function() {
        console.log("=== add 함수 실행 ===");
        const tbody = document.getElementById('invoiceItems');
        const firstRow = tbody.querySelector('.item-row');

        // 1. 행 복제
        const newRow = firstRow.cloneNode(true);

        // 2. [핵심 수정] 복제된 행에서 Select2 관련 잔여물 및 ID 충돌 해결

        // (1) 시각적으로 생성된 Select2 컨테이너(껍데기) 제거
        const select2Container = newRow.querySelector('.select2-container');
        if (select2Container) {
            select2Container.remove();
        }

        // (2) <select> 태그 및 내부 <option>의 Select2 흔적 지우기
        const newSelect = newRow.querySelector('.product-select');
        if (newSelect) {
            // 값 초기화
            newSelect.value = '';

            // Select2가 붙인 클래스 제거
            newSelect.classList.remove('select2-hidden-accessible');

            // Select2가 붙인 속성 제거 (중요!)
            newSelect.removeAttribute('data-select2-id');
            newSelect.removeAttribute('tabindex');
            newSelect.removeAttribute('aria-hidden');

            // [가장 중요] 내부 option들에 붙은 data-select2-id도 모두 제거해야 충돌 방지됨
            const options = newSelect.querySelectorAll('option');
            options.forEach(opt => {
                opt.removeAttribute('data-select2-id');
                opt.selected = false; // 선택 상태 해제
            });
        }

        // 3. Input 및 Select의 name 인덱스 업데이트
        const inputs = newRow.querySelectorAll('input, select');
        inputs.forEach(input => {
            if (input.tagName !== 'SELECT') { // select는 위에서 처리했으므로 제외
                input.value = '';
            }
            if (input.name) {
                input.name = input.name.replace(/\[\d+\]/, `[${itemIndex}]`);
            }
        });

        // 4. 텍스트/표시 값 초기화
        const amountDisplay = newRow.querySelector('.amount-display');
        if (amountDisplay) amountDisplay.textContent = '0.00';

        const amountInput = newRow.querySelector('.row-amount');
        if (amountInput) amountInput.value = '0.00';

        // 5. 삭제 버튼 추가
        const deleteCell = newRow.lastElementChild;
        deleteCell.innerHTML = '<button type="button" onclick="removeRow(this)" style="color:red; border:none; background:none; cursor:pointer;">&times;</button>';

        // 6. DOM에 추가
        tbody.appendChild(newRow);

        // 7. Select2 재적용 (깨끗해진 select 태그에 새로 입히기)
        if (newSelect) {
            window.initProductSelect2(newSelect);
        }

        itemIndex++;
    };

    // ============================================================
    // 2. 행 삭제 함수
    // ============================================================
    window.removeRow = function(button) {
        console.log("=== remove 함수 실행 ===");
        const row = button.closest('tr');
        row.remove();
        window.calculateTotal(); // 삭제 후 재계산
    };

    // ============================================================
    // 3. 개별 행 계산 함수
    // ============================================================
    window.calculateRow = function(row) {
        console.log("=== calculateRow 실행 ===");

        const priceInput = row.querySelector('input[name$=".price"]');
        const qtyInput = row.querySelector('input[name$=".quantity"]');
        const discountInput = row.querySelector('input[name$=".discount"]');
        const amountInput = row.querySelector('input[name$=".amount"]');
        // [추가됨] 텍스트 표시용 span 찾기
        const amountDisplay = row.querySelector('.amount-display');

        const price = parseFloat(priceInput.value) || 0;
        const qty = parseFloat(qtyInput.value) || 0;
        const discount = parseFloat(discountInput.value) || 0;

        let amount = (price * qty) - discount;
        if (amount < 0) amount = 0; // 마이너스 방지 (선택사항)

        // input 태그이므로 .value에 값을 넣습니다.
        if (amountInput) {
            amountInput.value = amount.toFixed(2);
        }
        if (amountDisplay) {
            amountDisplay.textContent = amount.toFixed(2); // 화면 표시용
        }

        // 전체 합계 재계산
        window.calculateTotal();
    };

    // ============================================================
    // 4. 전체 합계 계산 함수
    // ============================================================
    window.calculateTotal = function() {
        let subtotal = 0;

        // [수정] .row-amount 클래스를 가진 요소들은 모두 <input> 태그입니다.
        // 따라서 .textContent가 아니라 .value를 읽어야 합니다.
        document.querySelectorAll('.row-amount').forEach(input => {
            // 소수점 오차 방지
            let val = parseFloat(input.value) || 0;
            subtotal += Math.round(val * 100);
        });

        const taxRate = 0.10;
        const tax = Math.round(subtotal * taxRate); // 세금도 반올림 처리
        const total = subtotal + tax;

        // 화면 하단 업데이트 (이것들은 span이므로 textContent 사용)
        document.getElementById('subtotal').textContent = (subtotal/100).toFixed(2);
        document.getElementById('tax').textContent = (tax/100).toFixed(2);
        document.getElementById('totalAmount').textContent = (total/100).toFixed(2);

        // 백엔드 전송용 hidden input 업데이트
        const hiddenTotal = document.getElementById('hiddenTotal');
        if(hiddenTotal) hiddenTotal.value = (total/100).toFixed(2);
    };

    // ============================================================
    // 5. 상품 선택 시 자동완성 함수
    // ============================================================
    window.updateItemDetails = function(selectElement) {
        console.log("=== updateItemDetails 실행 ===");

        const row = selectElement.closest('tr');

        const descInput = row.querySelector('input[name$=".description"]');
        const priceInput = row.querySelector('input[name$=".price"]');
        const quantityInput = row.querySelector('input[name$=".quantity"]');
        const discountInput = row.querySelector('input[name$=".discount"]');

        if (selectElement.value === "") {
            if (priceInput) priceInput.value = "";
            if (descInput) descInput.value = "";
            if (quantityInput) quantityInput.value = "";
            if (discountInput) discountInput.value = "";

            // 값이 0이 되었으니 합계(Amount)도 0으로 다시 계산
            window.calculateRow(row);
            return; // 여기서 함수 끝!
        }

        const selectedOption = selectElement.options[selectElement.selectedIndex];
        // th:data-price -> data-price 속성 읽기
        const price = selectedOption.getAttribute('data-price');
        const desc = selectedOption.getAttribute('data-desc');


        // 가격 채우기
        if (price) {
            if (priceInput) {
                priceInput.value = price;
                // 가격이 들어갔으니 계산 실행
                window.calculateRow(row);
            }
        }

        // 설명 채우기
        if (desc) {
            if (descInput) descInput.value = desc;
        }
    };

    // ============================================================
    // 6. 상태 변경 및 전송 함수
    // ============================================================
    window.setStatusAndSubmit = function(statusValue) {
        const hiddenStatus = document.getElementById('hiddenStatus');
        if(hiddenStatus) {
            hiddenStatus.value = statusValue;
        }
    };

    // ============================================================
    // 7. 이벤트 리스너 (입력 시 자동 계산)
    // ============================================================
    const itemsBody = document.getElementById('invoiceItems');
    if (itemsBody) {
        itemsBody.addEventListener('input', function(e) {
            // calc-input 클래스를 가진 칸에 입력이 발생하면 계산 수행
            if (e.target.classList.contains('calc-input')) {
                window.calculateRow(e.target.closest('tr'));
            }
        });
    }

    // ============================================================
    // [추가] Contact 선택 시 세부 정보 자동 채우기
    // ============================================================
    window.updateContactDetails = function(selectElement) {
        console.log("=== updateContactDetails 실행 ===");

        // 1. 채워넣을 대상 DOM 요소 찾기
        const nameInput = document.getElementById('hiddenName'); // hidden
        const currencyInput = document.getElementById('customerCurrency');
        const billToInput = document.getElementById('customerBillTo');
        const companyInput = document.getElementById('hiddenCompanyName'); // hidden
        const emailInput = document.getElementById('hiddenEmail'); // hidden

        // 2. 선택 취소(빈 값)일 경우 초기화
        if (selectElement.value === "") {
            if (nameInput) nameInput.value = "";
            if (currencyInput) currencyInput.value = "";
            if (billToInput) billToInput.value = "";
            if (companyInput) companyInput.value = "";
            if (emailInput) emailInput.value = "";
            return;
        }

        // 3. 선택된 옵션에서 데이터 가져오기
        const selectedOption = selectElement.options[selectElement.selectedIndex];

        const name = selectedOption.getAttribute('data-name');
        const currency = selectedOption.getAttribute('data-currency');
        const address = selectedOption.getAttribute('data-address'); // billTo
        const company = selectedOption.getAttribute('data-company');
        const email = selectedOption.getAttribute('data-email');

        // 4. 값 주입
        if (nameInput) nameInput.value = name;
        if (currencyInput) currencyInput.value = currency;
        if (billToInput) billToInput.value = address;
        if (companyInput) companyInput.value = company;
        if (emailInput) emailInput.value = email;

        console.log(`Contact Updated: ${name}, ${company}`);
    };

    if (typeof $ !== 'undefined') {
        $('#contactSelect').select2({placeholder: "Select Client", allowClear: true, width: '100%'});
        // [중요] Select2에서 값이 변경될 때 기존 함수(updateContactDetails) 호출 연결
        $('#contactSelect').on('change', function() {window.updateContactDetails(this);});
    }
    if (typeof $ !== 'undefined') {
        $('#productSelect').select2({placeholder: "Select Product", allowClear: true, width: '100%'});
        // [중요] Select2에서 값이 변경될 때 기존 함수(updateContactDetails) 호출 연결
        $('#productSelect').on('change', function() {window.updateItemDetails(this);});
    }

    // 페이지 로드 시 최초 1회 전체 계산
    window.calculateTotal();

});