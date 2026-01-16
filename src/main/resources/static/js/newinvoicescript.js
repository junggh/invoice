document.addEventListener('DOMContentLoaded', function() {

    // 초기 인덱스 (이미 0번 행이 있으므로 1부터 시작)
    let itemIndex = 1;

    // ============================================================
    // 1. 행 추가 함수
    // ============================================================
    window.addItem = function() {
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

        // 삭제 버튼 추가
        const deleteCell = newRow.lastElementChild;
        deleteCell.innerHTML = `<button type="button" onclick="removeRow(this)" style="color:red; border:none; background:none; cursor:pointer;">&times;</button>`;

        // 테이블 추가
        tbody.appendChild(newRow);
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

        // [수정] amount는 이제 readonly input입니다.
        const amountInput = row.querySelector('input[name$=".amount"]');

        const price = parseFloat(priceInput.value) || 0;
        const qty = parseFloat(qtyInput.value) || 0;
        const discount = parseFloat(discountInput.value) || 0;

        let amount = (price * qty) - discount;
        if (amount < 0) amount = 0; // 마이너스 방지 (선택사항)

        // [수정] input 태그이므로 .value에 값을 넣습니다.
        if (amountInput) {
            amountInput.value = amount.toFixed(2);
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
            subtotal += parseFloat(input.value) || 0;
        });

        const taxRate = 0.10;
        const tax = subtotal * taxRate;
        const total = subtotal + tax;

        // 화면 하단 업데이트 (이것들은 span이므로 textContent 사용)
        document.getElementById('subtotal').textContent = subtotal.toFixed(2);
        document.getElementById('tax').textContent = tax.toFixed(2);
        document.getElementById('totalAmount').textContent = total.toFixed(2);

        // 백엔드 전송용 hidden input 업데이트
        const hiddenTotal = document.getElementById('hiddenTotal');
        if(hiddenTotal) hiddenTotal.value = total;
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
            if (priceInput) priceInput.value = 0;
            if (descInput) descInput.value = "";
            if (quantityInput) quantityInput.value = 0;
            if (discountInput) discountInput.value = 0;

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

    // 페이지 로드 시 최초 1회 전체 계산
    window.calculateTotal();

});