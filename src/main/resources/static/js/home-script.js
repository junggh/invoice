document.addEventListener('DOMContentLoaded', function() {
    console.log("Dashboard Script Loaded");

    // ============================================================
    // 1. 전체 선택/해제 (Checkbox)
    // ============================================================
    window.toggleAll = function(source) {
        const checkboxes = document.querySelectorAll('.invoice-checkbox');
        checkboxes.forEach(cb => cb.checked = source.checked);
    };

    // ============================================================
    // 2. 검색 로직 (Search Filter)
    // ============================================================
/*    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('keyup', function() {
            const filter = this.value.toLowerCase();
            const rows = document.querySelectorAll('.invoice-item');

            rows.forEach(row => {
                // 1. Invoice Number
                const invoiceNumEl = row.querySelector('.search-invoice-num');
                const invoiceNum = invoiceNumEl ? invoiceNumEl.textContent.toLowerCase() : "";

                // 2. Contact Name
                const contactNameEl = row.querySelector('.search-contact-name');
                const contactName = contactNameEl ? contactNameEl.textContent.toLowerCase() : "";

                // 3. Company Name (data attribute)
                const companyName = (row.getAttribute('data-company') || "").toLowerCase();

                // 4. 필터링 확인
                if (invoiceNum.includes(filter) || contactName.includes(filter) || companyName.includes(filter)) {
                    row.style.display = '';
                } else {
                    row.style.display = 'none';
                }
            });
        });
    }*/

    // ============================================================
    // 3. 기간 변경 (Period Selector)
    // ============================================================
    window.changePeriod = function() {
        const period = document.getElementById('periodSelect').value;
        const urlParams = new URLSearchParams(window.location.search);
        const currentStatus = urlParams.get('status') || 'Overview';
        location.href = `/invoices?status=${currentStatus}&period=${period}`;
    };

    // ============================================================
    // 4. 액션: 제출 (Submit - Draft -> In Review)
    // ============================================================
    window.submitSelected = function() {
        const checkedBoxes = document.querySelectorAll('.invoice-checkbox:checked');
        if (checkedBoxes.length === 0) {
            alert("Please select invoices to submit.");
            return;
        }

        let hasInvalidItem = false;
        checkedBoxes.forEach(cb => {
            if (cb.getAttribute('data-status') !== 'DRAFT') {
                hasInvalidItem = true;
            }
        });

        if (hasInvalidItem) {
            alert("Only 'DRAFT' invoices can be submitted.");
            return;
        }

        if (!confirm("Are you sure you want to SUBMIT " + checkedBoxes.length + " invoices for review?\n(Status will change to IN_REVIEW)")) {
            return;
        }

        submitForm('/api/invoices/submit', checkedBoxes);
    };

    // ============================================================
    // 5. 액션: 승인 (Approve - In Review -> Unpaid/Active)
    // ============================================================
    window.approveSelected = function() {
        const checkedBoxes = document.querySelectorAll('.invoice-checkbox:checked');
        if (checkedBoxes.length === 0) {
            alert("Please select items to approve.");
            return;
        }

        let hasInvalidItem = false;
        checkedBoxes.forEach(cb => {
            if (cb.getAttribute('data-status') !== 'IN_REVIEW') {
                hasInvalidItem = true;
            }
        });

        if (hasInvalidItem) {
            alert("Only items in 'IN_REVIEW' status can be approved.\n(Please deselect items that are not in review.)");
            return;
        }

        if (!confirm("Are you sure you want to approve " + checkedBoxes.length + " items?")) {
            return;
        }

        const urlParams = new URLSearchParams(window.location.search);
        const status = urlParams.get('status');
        const actionUrl = (status === 'Recurring') ? '/api/invoices/recurring/approve' : '/api/invoices/approve';

        submitForm(actionUrl, checkedBoxes);
    };

    // ============================================================
    // 6. 액션: 삭제 (Delete)
    // ============================================================
    window.deleteSelected = function() {
        const checkedBoxes = document.querySelectorAll('.invoice-checkbox:checked');
        if (checkedBoxes.length === 0) {
            alert("Please select invoices to delete.");
            return;
        }

        const allowList = ['DRAFT', 'IN_REVIEW', 'APPROVED'];
        const urlParams = new URLSearchParams(window.location.search);
        const currentTab = urlParams.get('status');

        // Recurring 탭이 아닐 경우 상태 검증
        if (currentTab !== 'Recurring') {
            let hasInvalidItem = false;
            checkedBoxes.forEach(cb => {
                if (!allowList.includes(cb.getAttribute('data-status'))) {
                    hasInvalidItem = true;
                }
            });

            if (hasInvalidItem) {
                alert("Only invoices in 'DRAFT', 'IN_REVIEW', or 'APPROVED' status can be deleted.\n(Published invoices cannot be deleted.)");
                return;
            }
        }

        if (!confirm("Are you sure you want to delete " + checkedBoxes.length + " invoices?")) {
            return;
        }

        let actionUrl = (currentTab === 'Recurring') ? '/api/invoices/recurring/delete' : '/api/invoices/delete';
        const queryString = window.location.search;
        if (queryString) {
            actionUrl += queryString;
        }
        submitForm(actionUrl, checkedBoxes);
    };

    // ============================================================
    // 7. 액션: 복사 (Copy)
    // ============================================================
    window.copySelected = function() {
        const checkedBoxes = document.querySelectorAll('.invoice-checkbox:checked');
        if (checkedBoxes.length === 0) {
            alert("Please select an invoice to copy.");
            return;
        }
        if (checkedBoxes.length > 1) {
            alert("Please select **only one** invoice to copy.");
            return;
        }

        const id = checkedBoxes[0].value;
        const urlParams = new URLSearchParams(window.location.search);
        const status = urlParams.get('status');

        if (status === 'Recurring') {
            location.href = '/invoices/new/recurring?copyId=' + id;
        } else {
            location.href = '/invoices/new?copyId=' + id;
        }
    };

    // ============================================================
    // 8. 액션: PDF 다운로드 (Download PDF)
    // ============================================================
    window.downloadPdf = function() {
        const checkedBoxes = document.querySelectorAll('.invoice-checkbox:checked');
        if (checkedBoxes.length === 0) {
            alert("Please select an invoice to download.");
            return;
        }
        if (checkedBoxes.length > 1) {
            alert("Please select only one invoice to download PDF.");
            return;
        }

        const uuid = checkedBoxes[0].getAttribute('data-uuid');
        if (!uuid) {
            alert("Cannot determine invoice UUID.");
            return;
        }

        window.location.href = '/api/invoices/' + uuid + '/pdf';
    };

    // ============================================================
    // 9. 액션: 정기결제 중단 (Stop Recurring)
    // ============================================================
    window.stopRecurring = function() {
        const checkedBoxes = document.querySelectorAll('.invoice-checkbox:checked');
        if (checkedBoxes.length === 0) {
            alert("Please select recurring templates to stop.");
            return;
        }

        let hasInvalidItem = false;
        checkedBoxes.forEach(cb => {
            if (cb.getAttribute('data-status') !== 'ACTIVE') {
                hasInvalidItem = true;
            }
        });

        if (hasInvalidItem) {
            alert("Only 'ACTIVE' templates can be stopped.\n(DRAFT or already COMPLETED templates cannot be stopped.)");
            return;
        }

        if (!confirm("Are you sure you want to STOP these " + checkedBoxes.length + " recurring templates?\n(Status will change to COMPLETED)")) {
            return;
        }

        submitForm('/api/invoices/recurring/complete', checkedBoxes);
    };

    // ============================================================
    // 9. 필터 드롭다운 토글 기능
    // ============================================================
    window.toggleFilterDropdown = function(event) {
        event.stopPropagation(); // 버튼 클릭 시 window 클릭 이벤트로 전파되지 않게 막음
        const dropdown = document.getElementById("filterDropdown");
        if (dropdown) {
            dropdown.classList.toggle("show");
        }
    };

    // 화면의 아무 곳이나 클릭하면 열려있는 드롭다운 닫기
    window.onclick = function(event) {
        if (!event.target.closest('.dropbtn') && !event.target.closest('.btn-icon')) {
            const dropdowns = document.getElementsByClassName("dropdown-content");
            for (let i = 0; i < dropdowns.length; i++) {
                const openDropdown = dropdowns[i];
                if (openDropdown.classList.contains('show')) {
                    openDropdown.classList.remove('show');
                }
            }
        }
    };

    // ============================================================
    // [Helper] 폼 생성 및 전송 (중복 제거)
    // ============================================================
    function submitForm(actionUrl, checkedBoxes) {
        const form = document.createElement('form');
        form.method = 'POST';
        form.action = actionUrl;

        const csrfMeta = document.querySelector('meta[name="_csrf"]');
        const csrfParamMeta = document.querySelector('meta[name="_csrf_param"]');
        if (csrfMeta) {
            const csrfInput = document.createElement('input');
            csrfInput.type = 'hidden';
            csrfInput.name = csrfParamMeta ? csrfParamMeta.getAttribute('content') : '_csrf';
            csrfInput.value = csrfMeta.getAttribute('content');
            form.appendChild(csrfInput);
        }

        checkedBoxes.forEach(cb => {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'ids';
            input.value = cb.value;
            form.appendChild(input);
        });

        document.body.appendChild(form);
        form.submit();
    }
});