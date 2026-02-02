document.addEventListener('DOMContentLoaded', function() {

    // 초기 인덱스 (이미 0번 행이 있으므로 1부터 시작)
    let itemIndex = (typeof window.itemIndex !== 'undefined') ? window.itemIndex : 1;

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

        itemIndex++;
    };

    // 행 삭제
    window.removeRow = function(button) {
        console.log("=== removeRow 실행 ===");
        const row = button.closest('tr');
        row.remove();
        window.calculateTotal(); // 삭제 후 재계산
    };

    // ============================================================
    // 3. 계산 함수 (행 계산 / 전체 합계)
    // ============================================================

    // 개별 행 계산
    window.calculateRow = function(row) {
        // console.log("=== calculateRow 실행 ===");
        const priceInput = row.querySelector('input[name$=".price"]');
        const qtyInput = row.querySelector('input[name$=".quantity"]');
        const discountInput = row.querySelector('input[name$=".discount"]');
        const amountInput = row.querySelector('input[name$=".amount"]');
        const amountDisplay = row.querySelector('.amount-display');

        const price = parseFloat(priceInput.value) || 0;
        const qty = parseFloat(qtyInput.value) || 0;
        const discount = parseFloat(discountInput.value) || 0;

        let amount = (price * qty) - discount;
        if (amount < 0) amount = 0;

        if (amountInput) amountInput.value = amount.toFixed(2);
        if (amountDisplay) amountDisplay.textContent = amount.toFixed(2);

        window.calculateTotal();
    };

    // 전체 합계 계산
    window.calculateTotal = function() {
        let subtotal = 0;

        document.querySelectorAll('.row-amount').forEach(input => {
            let val = parseFloat(input.value) || 0;
            subtotal += Math.round(val * 100);
        });

        const taxRate = 0.10;
        const tax = Math.round(subtotal * taxRate);
        const total = subtotal + tax;

        // 화면 업데이트
        document.getElementById('subtotal').textContent = (subtotal/100).toFixed(2);
        document.getElementById('tax').textContent = (tax/100).toFixed(2);
        document.getElementById('totalAmount').textContent = (total/100).toFixed(2);

        // Hidden Input 업데이트
        const hiddenTotal = document.getElementById('hiddenTotal');
        if(hiddenTotal) hiddenTotal.value = (total/100).toFixed(2);
    };

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

    // 페이지 로드 시 최초 전체 계산
    window.calculateTotal();
});