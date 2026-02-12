document.addEventListener('DOMContentLoaded', function() {

    // 초기 인덱스 (이미 0번 행이 있으므로 1부터 시작)
    let itemIndex = (typeof window.itemIndex !== 'undefined') ? window.itemIndex : 1;

    // ============================================================
    // 0. DatePicker (Flatpickr) 초기화 [추가됨]
    // ============================================================
    if (typeof flatpickr !== 'undefined') {
        flatpickr(".date-picker", {
            dateFormat: "Y-m-d", // 서버로 전송될 실제 값 형식 (Java LocalDate와 일치)
            altInput: true,      // 사용자에게 보여질 별도의 입력창 생성
            altFormat: "d/m/Y",  // 사용자에게 보여질 날짜 형식 (dd/MM/yyyy)
            allowInput: true     // 사용자가 직접 타이핑도 가능하게 설정
        });

        // (2) Due Date: 상단에 단축 버튼 추가
        flatpickr("input[name='dueDate']", {
            dateFormat: "Y-m-d",
            altInput: true,
            altFormat: "d/m/Y",
            allowInput: true,
            onReady: function(selectedDates, dateStr, instance) {
                // [추가] CSS에서 화살표 위치를 조절할 수 있도록 식별 클래스 추가
                instance.calendarContainer.classList.add('has-quick-select');
                // 1. 버튼들을 담을 컨테이너 생성
                const container = document.createElement("div");
                container.className = "flatpickr-quick-select";

                // 2. 버튼 생성 헬퍼 함수
                // days: 추가할 일수 (null이면 '다음 달' 로직 수행)
                const createBtn = (label, days) => {
                    const btn = document.createElement("button");
                    btn.type = "button";
                    btn.innerText = label;
                    btn.className = "quick-btn"; // 스타일용 클래스

                    btn.onclick = function() {
                        // 기준일 가져오기 (Issued Date가 있으면 그것을 기준, 없으면 오늘 기준)
                        const issuedInput = document.querySelector("input[name='issuedDate']");
                        let baseDate = new Date(); // 기본: 오늘

                        if (issuedInput && issuedInput.value) {
                            baseDate = new Date(issuedInput.value);
                        }

                        // 날짜 계산
                        if (days !== null) {
                            // 일수 더하기
                            baseDate.setDate(baseDate.getDate() + days);
                        } else {
                            // 다음 달 (같은 날짜)
                            baseDate.setMonth(baseDate.getMonth() + 1);
                        }

                        // Flatpickr에 값 설정 (두 번째 인자 true는 input 값 업데이트 트리거)
                        instance.setDate(baseDate, true);
                        instance.close(); // 선택 후 달력 닫기
                    };
                    return btn;
                };

                // 3. 버튼 추가 (7일, 14일, 다음 달)
                container.appendChild(createBtn("Next 7 Days", 7));
                container.appendChild(createBtn("Next 14 Days", 14));
                container.appendChild(createBtn("Next Month", null));

                // 4. 달력 컨테이너 최상단에 삽입
                instance.calendarContainer.prepend(container);
            }
        });
    }

    // ============================================================
    // [신규 기능] 컬럼 토글 (Column Visibility)
    // ============================================================

    // 1. 드롭다운 메뉴 열기/닫기
    window.toggleDropdown = function() {
        document.getElementById("columnDropdown").classList.toggle("show");
    };

    // 2. 화면 클릭 시 드롭다운 닫기
    window.onclick = function(event) {
        if (!event.target.matches('.btn-secondary') && !event.target.closest('.dropdown-content')) {
            var dropdowns = document.getElementsByClassName("dropdown-content");
            for (var i = 0; i < dropdowns.length; i++) {
                var openDropdown = dropdowns[i];
                if (openDropdown.classList.contains('show')) {
                    openDropdown.classList.remove('show');
                }
            }
        }
    }

    // 3. 체크박스 변경 시 컬럼 Show/Hide
    window.toggleCol = function(colClass, checkbox) {
        const isVisible = checkbox.checked;
        const elements = document.querySelectorAll('.' + colClass);

        elements.forEach(el => {
            if (isVisible) {
                el.classList.remove('d-none');
            } else {
                el.classList.add('d-none');
            }
        });
    };

    // ============================================================
    // 1. Select2 초기화 및 이벤트 연결
    // ============================================================

    // [공통 함수] 개별 Product Select2 초기화
    window.initProductSelect2 = function(targetElement) {
        if (typeof $ !== 'undefined') {
            $(targetElement).select2({
                placeholder: "Select Product",
                allowClear: true,
                width: '100%'
            }).on('change', function() {
                // 값이 변경되면 계산 및 정보 업데이트 호출
                window.updateItemDetails(this);
            });
        }
    };

    // [페이지 로드 시] jQuery/Select2 일괄 적용
    if (typeof $ !== 'undefined') {
        // (1) Contact Select2
        $('#contactSelect').select2({
            placeholder: "Select Client",
            allowClear: true,
            width: '100%'
        }).on('change', function() {
            window.updateContactDetails(this);
        });

        // (2) Product Select2 (기존 행들)
        $('.product-select').each(function() {
            window.initProductSelect2(this);
        });
    }

    // ============================================================
    // 2. 행 조작 함수 (추가/삭제)
    // ============================================================

    // 행 추가
    window.addItem = function() {
        console.log("=== addItem 실행 ===");
        const tbody = document.getElementById('invoiceItems');
        const firstRow = tbody.querySelector('.item-row');

        // 1. 행 복제
        const newRow = firstRow.cloneNode(true);

        // 2. Select2 관련 잔여물 및 ID 충돌 제거 (껍데기 및 속성 초기화)
        const select2Container = newRow.querySelector('.select2-container');
        if (select2Container) select2Container.remove();

        const newSelect = newRow.querySelector('.product-select');
        if (newSelect) {
            newSelect.value = '';
            newSelect.classList.remove('select2-hidden-accessible');
            newSelect.removeAttribute('data-select2-id');
            newSelect.removeAttribute('tabindex');
            newSelect.removeAttribute('aria-hidden');

            newSelect.querySelectorAll('option').forEach(opt => {
                opt.removeAttribute('data-select2-id');
                opt.selected = false;
            });
        }

        // 3. Input/Select name 인덱스 업데이트
        const inputs = newRow.querySelectorAll('input, select');
        inputs.forEach(input => {
            if (input.tagName !== 'SELECT') input.value = '';
            if (input.name) {
                input.name = input.name.replace(/\[\d+\]/, `[${itemIndex}]`);
            }
        });

        // 4. 금액 표시 초기화
        const amountDisplay = newRow.querySelector('.amount-display');
        if (amountDisplay) amountDisplay.textContent = '0.00';

        const amountInput = newRow.querySelector('.row-amount');
        if (amountInput) amountInput.value = '0.00';

        // 5. 삭제 버튼 추가
        const deleteCell = newRow.lastElementChild;
        deleteCell.innerHTML = '<button type="button" onclick="removeRow(this)" style="color:red; border:none; background:none; cursor:pointer;">&times;</button>';

        // 6. DOM 추가
        tbody.appendChild(newRow);

        // 7. 새 Select2 적용
        if (newSelect) {
            window.initProductSelect2(newSelect);
        }

        window.reindexRows();
    };

    // 행 삭제
    window.removeRow = function(button) {
        console.log("=== removeRow 실행 ===");
        const row = button.closest('tr');
        row.remove();
        window.reindexRows();
        window.calculateTotal(); // 삭제 후 재계산
    };

    // [신규] 인덱스 재정렬 함수 (핵심 로직)
    window.reindexRows = function() {
        const rows = document.querySelectorAll('#invoiceItems .item-row');
        rows.forEach((row, index) => {
            // 해당 행 내부의 모든 input, select 태그 찾기
            const inputs = row.querySelectorAll('input, select');
            inputs.forEach(input => {
                if (input.name) {
                    // items[3].price -> items[0].price 형태로 인덱스 교체
                    input.name = input.name.replace(/items\[\d+\]/, `items[${index}]`);
                }
            });
        });
        // 글로벌 인덱스 변수도 현재 행 개수에 맞춰 업데이트
        // (다음 addItem 클릭 시 번호가 꼬이지 않도록)
        itemIndex = rows.length;
    };

    // ============================================================
    // 3. 계산 함수 (행 계산 / 전체 합계)
    // ============================================================

    window.calculateRow = function(row) {
        const priceInput = row.querySelector('input[name$=".price"]');
        const qtyInput = row.querySelector('input[name$=".quantity"]');
        const discountInput = row.querySelector('input[name$=".discount"]');

        // [추가] GST 요소 가져오기
        const gstSelect = row.querySelector('.gst-select');
        const taxTypeSelect = document.getElementById('taxTypeSelect');

        const amountInput = row.querySelector('input[name$=".amount"]');
        const amountDisplay = row.querySelector('.amount-display');
        const taxInput = row.querySelector('.row-tax');

        const price = parseFloat(priceInput.value) || 0;
        const qty = parseFloat(qtyInput.value) || 0;
        const discount = parseFloat(discountInput.value) || 0;

        // GST 세율 가져오기 (data-rate 속성 활용)
        const selectedGstOption = gstSelect.options[gstSelect.selectedIndex];
        const taxRate = parseFloat(selectedGstOption.getAttribute('data-rate')) || 0;

        const taxType = taxTypeSelect.value; // TAX_INCLUSIVE, TAX_EXCLUSIVE, NO_TAX

        // 1. 기본 라인 합계 (할인 적용 후)
        let lineTotal = (price * qty) - discount;
        if (lineTotal < 0) lineTotal = 0;

        let calculatedTax = 0;
        let finalAmount = lineTotal;

        // 2. 세금 계산 로직
        if (taxType === 'NO_TAX') {
            calculatedTax = 0;
            finalAmount = lineTotal;
        }
        else if (taxType === 'TAX_INCLUSIVE') {
            // 세금 포함: 110원(10%) -> 세금은 110 * (0.1 / 1.1) = 10원
            // Amount 필드는 보통 세전 금액이 아니라 '표시 금액'을 그대로 둡니다.
            calculatedTax = lineTotal * (taxRate / (1 + taxRate));
            finalAmount = lineTotal;
        }
        else { // TAX_EXCLUSIVE (기본)
            // 세금 별도: 100원(10%) -> 세금은 100 * 0.1 = 10원
            calculatedTax = lineTotal * taxRate;
            finalAmount = lineTotal; // 보통 Invoice 라인에는 세전 금액을 표시하고, Total에서 합산
        }

        // 값 업데이트
        if (amountInput) amountInput.value = finalAmount.toFixed(2);
        if (amountDisplay) amountDisplay.textContent = finalAmount.toFixed(2);
        if (taxInput) taxInput.value = calculatedTax.toFixed(2); // 숨겨진 필드에 세금 저장

        window.calculateTotal();
    };

    window.calculateTotal = function() {
        let subtotal = 0;
        let totalTax = 0;
        const taxType = document.getElementById('taxTypeSelect').value;

        // 모든 행을 돌면서 Amount와 Tax를 합산
        const rows = document.querySelectorAll('#invoiceItems .item-row');

        rows.forEach(row => {
            const amtVal = parseFloat(row.querySelector('.row-amount').value) || 0;
            const taxVal = parseFloat(row.querySelector('.row-tax').value) || 0;

            if (taxType === 'TAX_INCLUSIVE') {
                // Inclusive면 Amount에 이미 세금이 포함되어 있음.
                // Subtotal(세전) = Amount - Tax
                subtotal += (amtVal - taxVal);
            } else {
                // Exclusive나 No Tax면 Amount가 곧 세전 금액
                subtotal += amtVal;
            }
            totalTax += taxVal;
        });

        const total = subtotal + totalTax;

        // 화면 업데이트
        document.getElementById('subtotal').textContent = subtotal.toFixed(2);
        document.getElementById('tax').textContent = totalTax.toFixed(2);
        document.getElementById('totalAmount').textContent = total.toFixed(2);

        const hiddenTotal = document.getElementById('hiddenTotal');
        if(hiddenTotal) hiddenTotal.value = total.toFixed(2);

        const hiddenSubtotal = document.getElementById('hiddenSubtotal');
        if(hiddenSubtotal) hiddenSubtotal.value = subtotal.toFixed(2);

        const hiddenTax = document.getElementById('hiddenTax');
        if(hiddenTax) hiddenTax.value = totalTax.toFixed(2);
    };

    // [추가] "Amounts are" 변경 시 모든 행 재계산 필요
    document.getElementById('taxTypeSelect').addEventListener('change', function() {
        const rows = document.querySelectorAll('#invoiceItems .item-row');
        rows.forEach(row => window.calculateRow(row));
    });

    // ============================================================
    // 4. 정보 업데이트 함수 (Product / Contact)
    // ============================================================

    // 상품 선택 시 상세 정보 채우기
    window.updateItemDetails = function(selectElement) {
        // console.log("=== updateItemDetails 실행 ===");
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
            window.calculateRow(row);
            return;
        }

        const selectedOption = selectElement.options[selectElement.selectedIndex];
        const price = selectedOption.getAttribute('data-price');
        const desc = selectedOption.getAttribute('data-desc');

        if (price && priceInput) {
            priceInput.value = price;
            window.calculateRow(row);
        }
        if (desc && descInput) {
            descInput.value = desc;
        }
    };

    // Contact 선택 시 상세 정보 채우기
    window.updateContactDetails = function(selectElement) {
        // console.log("=== updateContactDetails 실행 ===");
        const nameInput = document.getElementById('hiddenName');
        const currencyInput = document.getElementById('customerCurrency');
        const billToInput = document.getElementById('customerBillTo');
        const companyInput = document.getElementById('hiddenCompanyName');
        const emailInput = document.getElementById('hiddenEmail');

        if (selectElement.value === "") {
            if (nameInput) nameInput.value = "";
            if (currencyInput) currencyInput.value = "";
            if (billToInput) billToInput.value = "";
            if (companyInput) companyInput.value = "";
            if (emailInput) emailInput.value = "";
            return;
        }

        const selectedOption = selectElement.options[selectElement.selectedIndex];
        if (nameInput) nameInput.value = selectedOption.getAttribute('data-name');
        if (currencyInput) currencyInput.value = selectedOption.getAttribute('data-currency');
        if (billToInput) billToInput.value = selectedOption.getAttribute('data-address');
        if (companyInput) companyInput.value = selectedOption.getAttribute('data-company');
        if (emailInput) emailInput.value = selectedOption.getAttribute('data-email');
    };

    // ============================================================
    // 5. 기타 유틸리티 (상태값 설정, 이벤트 리스너)
    // ============================================================

    window.setStatusAndSubmit = function(statusValue) {
        const hiddenStatus = document.getElementById('hiddenStatus');
        if(hiddenStatus) {
            hiddenStatus.value = statusValue;
        }
    };

    // 입력 감지 (계산 자동화)
    const itemsBody = document.getElementById('invoiceItems');
    if (itemsBody) {
        itemsBody.addEventListener('input', function(e) {
            if (e.target.classList.contains('calc-input')) {
                window.calculateRow(e.target.closest('tr'));
            }
        });
    }

    // ============================================================
    // [수정] 페이지 로드 시 초기화 로직
    // ============================================================

    // 1. 모든 기존 행에 대해 '개별 계산(calculateRow)'을 강제로 수행
    // 이유: DB에서 불러온 데이터에는 계산된 Tax 값이 히든 필드에 없거나 0일 수 있음.
    // 따라서 화면이 열리자마자 Price, Qty, GST, TaxType을 보고 세금을 다시 계산해서 채워넣어야 함.
    const existingRows = document.querySelectorAll('#invoiceItems .item-row');
    existingRows.forEach(row => {
        // Select2가 적용된 경우 데이터가 늦게 로딩될 수 있으므로 안전하게 처리
        window.calculateRow(row);
    });

    // 2. 각 행의 계산이 끝난 후 전체 합계 계산
    window.calculateTotal();
});