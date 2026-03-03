# ZeniBooks - Invoice Management SaaS

Spring Boot + Thymeleaf 기반의 호주 사업자용 멀티테넌트 인보이스 관리 시스템.
회사(Company)를 테넌트 루트로 하여, 인보이스 생성/승인/발송/결제 전 과정을 관리한다.

---

## 실행 방법

### 사전 요구사항
- Java 17+
- Docker & Docker Compose
- Gmail 앱 비밀번호 (이메일 발송용)
- ABN Lookup API GUID (호주 사업자 번호 조회용, [ABR](https://abr.business.gov.au/)에서 발급)

### 1. 데이터베이스 시작
```bash
docker-compose up -d
```
PostgreSQL 15 | port 5432 | DB: `invoicedb`

### 2. Secret 설정 파일 생성
`src/main/resources/application-secret.yml` (gitignored):
```yaml
db:
  username: newzen
  password: 1234

init:
  admin:
    email: dev@myerp.com
    password: 1234

mail:
  user: your-gmail@gmail.com
  pass: your-app-password

abn:
  guid: your-abn-api-guid
```

### 3. 빌드 및 실행
```bash
./gradlew bootRun
```
http://localhost:8080 에서 접속.

### 기본 로그인
`InitDataConfig`에 의해 서버 시작 시 자동 생성됨.
이메일과 비밀번호는 `application-secret.yml`의 `init.admin.email`, `init.admin.password`에서 읽어온다.

### 4. 프로덕션 배포
```bash
java -jar demo.jar --spring.profiles.active=prod
```
`prod` 프로필 활성화 시 SQL 로깅이 비활성화된다.

---

## 기술 스택

| 영역 | 기술 |
|---|---|
| Backend | Java 17, Spring Boot 3.5.9, Spring Security, Spring Data JPA |
| Frontend | Thymeleaf, jQuery, Select2, Flatpickr, intl-tel-input |
| Database | PostgreSQL 15 (Docker) |
| Build | Gradle |
| Email | Gmail SMTP (비동기 `@Async`) |
| PDF | openhtmltopdf (HTML → PDF 변환) |
| Scheduling | Spring `@Scheduled` (매일 자정 cron) |

---

## 아키텍처 개요

### 멀티테넌시
**Company**가 테넌트 루트. 모든 데이터(Invoice, Contact, Product, Member)는 Company에 종속되며, 쿼리 시 반드시 Company로 필터링한다.

### 인증/인가
Spring Security form login. `Member.email`을 로그인 ID로 사용.

| 경로 | 접근 권한 |
|---|---|
| `/login`, `/signup`, `/api/auth/**`, `/invitations/accept` | 공개 |
| `/super-admin/**` | SUPER_ADMIN만 |
| 그 외 모든 경로 | 인증 필요 |

로그인 성공 시 역할별 리다이렉트:
- `SUPER_ADMIN` → `/super-admin/companies`
- `ADMIN`, `USER` → `/invoices`

### CSRF
활성화 상태. `/api/auth/**` 경로는 비인증 공개 API이므로 CSRF 검증 제외.
Thymeleaf `th:action` 폼은 자동으로 CSRF 토큰이 주입되며, JS fetch 호출은 메타 태그에서 토큰을 읽어 헤더에 포함한다.

---

## 패키지 구조

```
src/main/java/com/example/demo/
├── config/           # 설정 클래스
│   ├── SecurityConfig          # Spring Security 설정 (URL 권한, 로그인/로그아웃, 성공 핸들러)
│   ├── InitDataConfig          # 서버 시작 시 SUPER_ADMIN 자동 생성
│   └── PasswordEncoderConfig   # BCryptPasswordEncoder 빈
│
├── controller/       # HTTP 요청 처리 (View + API)
│   ├── AuthController              # 회원가입, 로그인, ABN 조회, 이메일 인증
│   ├── InvoiceController           # 인보이스/반복템플릿 CRUD, 대시보드, 상태변경, 결제
│   ├── AdminController             # Super Admin 회사 목록, Company Admin 멤버 관리
│   ├── SubscriptionController      # PayPal 구독 플랜 페이지 및 활성화
│   ├── CompanyInvitationController # 팀 초대 생성/수락
│   ├── TempDataController          # Product/Contact 간편 등록
│   └── GlobalControllerAdvice      # 모든 요청에 회사명/유저 이니셜 주입
│
├── service/          # 비즈니스 로직
│   ├── AuthService                 # 회원가입 처리, 이메일/ABN 중복 확인, 로그인 일시 갱신
│   ├── InvoiceService              # 인보이스 CRUD, 상태변경, 결제, 스케줄러(연체/예약발송)
│   ├── RecurringInvoiceService     # 반복 템플릿 CRUD, 자동 인보이스 생성 스케줄러
│   ├── PdfService                  # Thymeleaf HTML → PDF 변환 (openhtmltopdf)
│   ├── EmailService                # 비동기 이메일 발송 (@Async, PDF 첨부)
│   ├── AbnLookupService            # 호주 ABN API 조회
│   ├── AdminDashboardService       # Super Admin/Company Admin 대시보드 데이터
│   ├── CompanyInvitationService    # 팀 초대 토큰 생성/수락 (7일 만료)
│   └── SubscriptionService         # PayPal 구독 활성화, 플랜 조회
│
├── entity/           # JPA 엔티티 (DB 테이블)
│   ├── Company                 # 테넌트 루트 (사업자 정보, 플랜, 구독)
│   ├── Member                  # 사용자 계정 (email 로그인, 역할)
│   ├── Invoice                 # 인보이스 본체 (상태, 금액, 고객 스냅샷)
│   ├── InvoiceItem             # 인보이스 항목 (상품, 수량, 할인, GST)
│   ├── Contact                 # 거래처/고객 정보
│   ├── Product                 # 상품/서비스 카탈로그
│   ├── RecurringInvoice        # 반복 인보이스 템플릿
│   ├── RecurringInvoiceItem    # 반복 템플릿 항목
│   ├── CompanyInvitation       # 팀 초대 (토큰, 7일 만료)
│   └── Enums                   # InvoiceStatus, RecurringStatus, TaxType, GstCode,
│                               # RecurringFrequency, PlanType, Timezone, DiscountType
│
├── repository/       # Spring Data JPA 리포지토리
│   ├── InvoiceRepository           # 검색+페이징, 대시보드 집계, 스케줄러 조회
│   ├── RecurringInvoiceRepository  # 검색+페이징, 스케줄러 조회
│   ├── CompanyRepository           # ABN 중복 확인
│   ├── MemberRepository            # 이메일 조회, 회사별 멤버 조회
│   ├── ContactRepository           # 회사별 거래처 조회
│   ├── ProductRepository           # 회사별 상품 조회
│   └── CompanyInvitationRepository # 토큰으로 초대 조회
│
├── dto/              # 데이터 전송 객체
│   ├── SignupForm              # 회원가입 폼 (개인정보 + 회사정보 + ABN)
│   ├── AbnApiResponse          # ABN API 응답 매핑
│   ├── CompanyDashboardDto     # Super Admin 회사 목록용
│   ├── MemberManagementDto     # 멤버 관리 목록용
│   └── SubscriptionRequest     # PayPal 구독 요청
│
└── security/         # Spring Security 커스텀
    ├── CustomUserDetails       # Member 래핑 (UserDetails 구현)
    └── CustomUserDetailsService # email로 Member 조회 → UserDetails 반환
```

---

## 엔티티 관계도

```
Company (테넌트 루트)
 ├── Member (1:N)           - 사용자 계정, 역할(ADMIN/USER/SUPER_ADMIN)
 ├── Invoice (1:N)          - 인보이스
 │    └── InvoiceItem (1:N) - 인보이스 항목 (수량, 할인, GST, DiscountType)
 ├── RecurringInvoice (1:N) - 반복 템플릿
 │    └── RecurringInvoiceItem (1:N)
 ├── Contact (1:N)          - 거래처/고객
 ├── Product (1:N)          - 상품/서비스
 └── CompanyInvitation (1:N)- 팀 초대

Contact ←── Invoice.contact (N:1, nullable)
Product ←── InvoiceItem.product (N:1)
```

---

## 인보이스 상태 흐름

```
DRAFT ──[Submit]──> IN_REVIEW ──[Approve]──┬──> UNPAID (발행일 ≤ 오늘, 이메일 발송)
                                           │      ├──[결제 완료]──> PAID
                                           │      └──[납기일 초과, 자정 스케줄러]──> OVERDUE
                                           │
                                           └──> APPROVED (발행일 > 오늘, 예약)
                                                  └──[발행일 도래, 자정 스케줄러]──> UNPAID

모든 상태 ──[Delete]──> DELETED (소프트 삭제)
```

### 반복 인보이스 상태 흐름
```
DRAFT ──[Approve]──> ACTIVE ──[자정 스케줄러]──> 인보이스 자동 생성
                       │                           (autoSend=true → UNPAID, false → DRAFT)
                       ├──[종료일 초과]──> COMPLETED
                       └──[Stop Recurring]──> COMPLETED
```

---

## 주요 비즈니스 로직

### 인보이스 번호 채번
`INV-#####` 형식, 회사별 시퀀셜. `findTopByCompanyAndInvoiceNumberStartingWithOrderByInvoiceNumberDesc`로 마지막 번호 조회 후 +1.
반복 템플릿은 `INVT-#####` 형식.

### 할인 방식 (DiscountType)
항목별로 할인 방식을 선택할 수 있다.
- `AMOUNT`: 고정 금액 할인 (예: $10 할인)
- `PERCENT`: 퍼센트 할인 (예: 10% 할인)

기본값은 `AMOUNT`. DB 레벨에서 `DEFAULT 'AMOUNT'`로 지정되어, 기존 데이터 마이그레이션 시에도 안전하다.

### 고객 정보 스냅샷
Invoice에 `customerName`, `customerEmail`, `customerCompanyName` 등을 별도로 저장.
Contact 정보가 나중에 변경되어도 발행 시점의 정보가 보존된다.

`manualContact` (boolean) 필드로 수동 입력 여부를 구분:
- `false`: Contact 드롭다운에서 선택 (contact 참조 연결됨)
- `true`: 일회성 고객 직접 입력 (contact 참조 없음)

### 스케줄러 (매일 자정 실행)
| 스케줄러 | 위치 | 동작 |
|---|---|---|
| 예약 인보이스 발송 | `InvoiceService.processScheduledInvoices()` | APPROVED → UNPAID (발행일 도래 시) |
| 연체 상태 갱신 | `InvoiceService.updateOverdueInvoices()` | UNPAID → OVERDUE (납기일 초과 시) |
| 반복 인보이스 생성 | `RecurringInvoiceService.generateRecurringInvoices()` | ACTIVE 템플릿 → Invoice 자동 생성 |

### 결제 처리
`InvoiceService.recordPayment(uuid, amount, company)`:
- `newBalance = currentBalance - paymentAmount`
- `newBalance ≤ 0` → `balanceDue = 0`, `status = PAID`
- `newBalance > 0` → `balanceDue = newBalance`, 기존 상태 유지

### 팀 초대 플로우
1. Admin이 이메일로 초대 → UUID 토큰 생성 (7일 만료)
2. 초대 메일에 `/invitations/accept?token=...` 링크 포함
3. 수락 시 Member.company 연결, role = USER 설정
4. 로그인 성공 핸들러에서 토큰 자동 수락 처리

---

## 프론트엔드 구조

### 템플릿 (`src/main/resources/templates/`)

| 파일 | 설명 |
|---|---|
| `home.html` | 대시보드 - 탭 필터, 검색, 페이징, 기간별 통계, 일괄 작업 |
| `new-invoice.html` | 인보이스 생성 (invoice-form 프래그먼트 사용) |
| `edit-invoice.html` | 인보이스 수정 (DRAFT만 가능) |
| `view-invoice.html` | 인보이스 상세 조회 (읽기 전용) |
| `new-template.html` | 반복 템플릿 생성 (template-form 프래그먼트 사용) |
| `edit-template.html` | 반복 템플릿 수정 |
| `view-template.html` | 반복 템플릿 상세 조회 (Start/Stop 버튼 포함) |
| `login.html` | 로그인 폼 |
| `signup.html` | 다단계 회원가입 (개인정보 → 이메일 인증 → 회사 정보) |
| `subscribe.html` | PayPal 구독 플랜 선택 |
| `company-users.html` | Company Admin - 멤버 관리 |
| `super-admin-companies.html` | Super Admin - 전체 회사 목록 |
| `super-admin-company-users.html` | Super Admin - 특정 회사 멤버 조회 |
| `temp-product.html` | Product 간편 등록 |
| `temp-contact.html` | Contact 간편 등록 |

### 프래그먼트 (`templates/fragments/`)

| 파일 | 설명 |
|---|---|
| `nav.html` | 사이드바 + 상단바 (메뉴, 회사명, 유저 이니셜) |
| `super-admin-nav.html` | Super Admin 전용 네비게이션 |
| `invoice-form.html` | 인보이스 입력 폼 (고객 선택/수동입력, 항목 테이블, 금액 계산) |
| `template-form.html` | 반복 템플릿 입력 폼 (빈도, 기간, 자동발송 토글) |

### 정적 파일 (`src/main/resources/static/`)

| 파일 | 설명 |
|---|---|
| `css/layout.css` | 전체 레이아웃 (사이드바, 상단바, 콘텐츠 영역) |
| `css/homestyle.css` | 대시보드 (탭, 테이블, 페이징, 통계 카드) |
| `css/newinvoicestyle.css` | 인보이스 폼 (그리드, 항목 테이블, Select2 오버라이드, 모달) |
| `css/viewinvoicestyle.css` | 인보이스 상세 보기 |
| `css/authstyle.css` | 로그인/회원가입 페이지 |
| `js/newinvoicescript.js` | 인보이스 폼 로직 (항목 추가/삭제, 금액 계산, 수동 연락처 토글, Select2/Flatpickr 초기화) |
| `js/home-script.js` | 대시보드 로직 (일괄 선택, 상태 변경, 복사, 기간 필터, Stop Recurring, PDF 다운로드) |
| `js/signup.js` | 회원가입 다단계 폼 (ABN 조회, 이메일 인증, 유효성 검사) |

---

## 설정 파일

| 파일 | 설명 |
|---|---|
| `application.yaml` | 메인 설정 - DB 연결, JPA(DDL auto=update, SQL 로깅), Gmail SMTP, prod 프로필 |
| `application-secret.yml` | **gitignored** - DB 계정, SUPER_ADMIN 계정, Gmail 비밀번호, ABN API GUID |
| `docker-compose.yml` | PostgreSQL 15 컨테이너 설정 |
| `build.gradle` | 의존성: spring-boot-starter-{data-jpa, thymeleaf, web, security, mail}, postgresql, lombok, openhtmltopdf |

---

## API 엔드포인트 요약

### 인보이스 (`InvoiceController`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/invoices` | 대시보드 (목록 + 통계) |
| GET | `/invoices/new` | 생성 폼 (`?copyId=` 복사) |
| GET | `/invoices/{uuid}` | 상세 조회 |
| GET | `/invoices/{uuid}/edit` | 수정 폼 (DRAFT만) |
| POST | `/api/invoices` | 생성 |
| POST | `/api/invoices/update` | 수정 |
| POST | `/api/invoices/{uuid}/pay` | 결제 기록 |
| POST | `/api/invoices/submit` | 일괄 제출 (DRAFT → IN_REVIEW) |
| POST | `/api/invoices/approve` | 일괄 승인 |
| POST | `/api/invoices/delete` | 일괄 삭제 (소프트) |
| GET | `/api/invoices/{uuid}/pdf` | PDF 다운로드 |

### 반복 템플릿 (`InvoiceController`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/invoices/new/recurring` | 템플릿 생성 폼 |
| GET | `/invoices/recurring/{uuid}` | 템플릿 상세 |
| GET | `/invoices/recurring/{uuid}/edit` | 템플릿 수정 폼 |
| POST | `/api/invoices/recurring` | 템플릿 생성 |
| POST | `/api/invoices/recurring/update` | 템플릿 수정 |
| POST | `/api/invoices/recurring/approve` | 활성화 (IN_REVIEW → ACTIVE) |
| POST | `/api/invoices/recurring/complete` | 완료 처리 |
| POST | `/api/invoices/recurring/delete` | 삭제 (소프트) |

### 인증 (`AuthController`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/signup` | 회원가입 폼 |
| POST | `/signup` | 회원가입 처리 |
| GET | `/login` | 로그인 폼 |
| GET | `/api/auth/check-email` | 이메일 중복 확인 |
| GET | `/api/auth/abn-lookup` | ABN 조회 |
| POST | `/api/auth/send-verification` | 이메일 인증코드 발송 |
| POST | `/api/auth/verify-code` | 인증코드 확인 |

### 관리자 (`AdminController`)

| Method | URL | 설명 |
|---|---|---|
| GET | `/super-admin/companies` | 전체 회사 목록 (SUPER_ADMIN) |
| GET | `/super-admin/companies/{id}/users` | 회사별 멤버 (SUPER_ADMIN) |
| GET | `/admin/users` | 내 회사 멤버 (ADMIN) |

### 기타

| Method | URL | 설명 |
|---|---|---|
| POST | `/api/invitations` | 팀 초대 생성 |
| GET | `/invitations/accept` | 초대 수락 |
| GET | `/subscribe` | 구독 플랜 페이지 |
| POST | `/api/subscription/success` | PayPal 구독 활성화 |
| GET/POST | `/product` | Product 등록 |
| GET/POST | `/contact` | Contact 등록 |

---

## 미구현/진행중 기능

- **PDF Export**: 기본 구현 완료 (view-invoice 화면 기반, 디자인 개선 필요)
- **알림 센터**: 상단바 아이콘 비활성
- **Credit Notes**: 버튼만 존재, 로직 없음
- **Payment Link**: 모달 UI 존재, 실제 연동 없음
- **역할별 세분화 권한**: 기본적인 역할 체크만 존재
- **Audit Logging**: 상세 감사 추적 미구현
