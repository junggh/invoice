document.addEventListener('DOMContentLoaded', function() {

    // 초기 인덱스 (이미 0번 행이 있으므로 1부터 시작)
    let itemIndex = 1;

    // 1. 행 추가 함수 (HTML 버튼 onclick="addItem()"에서 호출)
    window.addItem = function() {
        console.log("=== add 함수가 실행됨! ===");
        const tbody = document.getElementById('invoiceItems');
        const firstRow = tbody.querySelector('.item-row');

        // 첫 번째 행을 깊은 복사(Clone)
        const newRow = firstRow.cloneNode(true);

        // 복사된 행 내부의 input들을 찾아서 처리
        const inputs = newRow.querySelectorAll('input');
        inputs.forEach(input => {
            // 값 비우기
            input.value = '';

            // name 속성의 인덱스 업데이트 (items[0] -> items[1])
            // 정규식: items[숫자] 를 찾아서 현재 itemIndex로 바꿈
            if (input.name) {
                input.name = input.name.replace(/\[\d+\]/, `[${itemIndex}]`);
            }
        });

        // Amount 텍스트 초기화 (0.00으로)
        const amountDisplay = newRow.querySelector('.row-amount');
        if (amountDisplay) amountDisplay.textContent = '0.00';

        // 삭제 버튼 추가 (첫 줄이 아니면 삭제 가능하게)
        const deleteCell = newRow.lastElementChild;
        deleteCell.innerHTML = `<button type="button" onclick="removeRow(this)" style="color:red; border:none; background:none; cursor:pointer;">&times;</button>`;

        // 테이블에 추가
        tbody.appendChild(newRow);

        // 인덱스 증가
        itemIndex++;
    };

    // 2. 행 삭제 함수
    window.removeRow = function(button) {
        console.log("=== remove 함수가 실행됨! ===");
        const row = button.closest('tr');
        row.remove();
        calculateTotal(); // 삭제 후 총액 다시 계산
    };

    // 3. 계산 로직 (이벤트 위임 방식)
    const itemsBody = document.getElementById('invoiceItems');

    itemsBody.addEventListener('input', function(e) {
        console.log("=== 리스너 함수가 실행됨! ===");
        // 이벤트를 발생시킨 요소가 계산이 필요한 input인지 확인
        if (e.target.classList.contains('calc-input')) {
            calculateRow(e.target.closest('tr'));
        }
    });

    // 개별 행 계산
    function calculateRow(row) {
        console.log("=== calculateRow 함수가 실행됨! ===");
        // name 속성이 items[i].price 형태이므로, name의 끝부분(price, quantity) 등으로 찾지 않고 순서나 클래스로 찾아야 함.
        // 여기서는 간단하게 querySelector로 찾습니다.
        // (주의: name에 인덱스가 있어서 input[name="price"]로는 못 찾음)

        const priceInput = row.querySelector('input[name$=".price"]'); // 이름이 .price로 끝나는 것
        const qtyInput = row.querySelector('input[name$=".quantity"]');
        const discountInput = row.querySelector('input[name$=".discount"]');
        const amountDisplay = row.querySelector('.row-amount');

        const price = parseFloat(priceInput.value) || 0;
        const qty = parseFloat(qtyInput.value) || 0;
        const discount = parseFloat(discountInput.value) || 0;

        let amount = (price * qty) - discount;

        amountDisplay.textContent = amount.toFixed(2);

        // 전체 합계 재계산
        calculateTotal();
    }

    // 전체 합계 계산
    function calculateTotal() {
        let subtotal = 0;

        // 모든 행의 amount를 더함
        document.querySelectorAll('.row-amount').forEach(td => {
            subtotal += parseFloat(td.textContent) || 0;
        });

        const taxRate = 0.10;
        const tax = subtotal * taxRate;
        const total = subtotal + tax;

        // 화면 업데이트
        document.getElementById('subtotal').textContent = subtotal.toFixed(2);
        document.getElementById('tax').textContent = tax.toFixed(2);
        document.getElementById('totalAmount').textContent = total.toFixed(2);

        // 백엔드 전송용 hidden input
        const hiddenTotal = document.getElementById('hiddenTotal');
        if(hiddenTotal) hiddenTotal.value = total;
    }
});