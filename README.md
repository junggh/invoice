# ZenyBooks - Invoice Management SaaS

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

### 4. 프로덕션 배포 (Azure Rocky Linux + Nginx)

#### 서버 전제 조건
- nginx/1.20.1 설치 완료
- 인바운드 포트 **80(HTTP)**, **443(HTTPS)** 개방

#### Phase 0: 로컬에서 JAR 빌드 (로컬 머신)

```bash
./gradlew build
```

`build/libs/` 폴더에 생성된 실행 가능한 JAR 파일을 FileZilla 등으로 서버의 `/home/newzen/`에 업로드한다.
(`application-secret.yml`, `docker-compose.yml`은 JAR에 포함되어 있으므로 별도 업로드 불필요)

#### Phase 1: 서버 기초 공사 및 권한 설정 (root 권한)

**1. Rocky Linux용 Docker 설치 및 권한 부여**

Docker 공식 저장소를 연결하여 도커를 설치하고, `newzen` 계정에 도커 권한을 부여한다.

```bash
yum install -y yum-utils
yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

systemctl start docker
systemctl enable docker
usermod -aG docker newzen
```

**2. Java 17을 공식 폴더로 이동 (SELinux 권한 문제 해결)**

SELinux가 systemd 서비스의 홈 디렉토리 접근을 막는 것을 방지하기 위해, Java를 공식 폴더로 옮기고 보안 레이블을 다시 설정한다.

```bash
mv /home/newzen/java /opt/java
restorecon -Rv /opt/java
ln -s /opt/java /home/newzen/java  # 기존 경로 유지를 위한 바로가기
```

#### Phase 2: Nginx 웹 서버 연결 (root 권한)

**1. 리버스 프록시 설정 (80 포트 → 8080 포트)**

```bash
vi /etc/nginx/nginx.conf
```

`server { }` 블록 안에서 다음과 같이 수정한다.

```nginx
# 아래 줄을 주석 처리:
# root /usr/share/nginx/html;

# include /etc/nginx/default.d/*.conf; 바로 아래에 추가:
location / {
    proxy_pass http://localhost:8080;
}
```

**2. SELinux 통신 허용 및 Nginx 재시작**

Nginx가 내부 포트(8080)로 통신하는 것을 SELinux가 차단하지 않도록 허용한다.

```bash
setsebool -P httpd_can_network_connect 1
systemctl restart nginx
systemctl enable nginx
```

#### Phase 3: DB 실행 (newzen 권한)

`docker-compose.yml` 파일이 있는 홈 폴더(`/home/newzen`)에서 PostgreSQL DB를 백그라운드로 실행한다.

```bash
docker compose up -d
```

> DB를 완전히 초기화하려면: `docker compose down -v` 후 다시 `docker compose up -d`

#### Phase 4: systemd 기반 무중단 서비스 등록 (root 권한)

스프링부트 서버가 꺼져도 자동 재시작되고, 부팅 시 DB가 켜진 후 안전하게 켜지도록 시스템 설정 파일을 만든다.

**1. 서비스 파일 생성**

```bash
vi /etc/systemd/system/erp.service
```

```ini
[Unit]
Description=Spring Boot ERP Web Service
After=network.target docker.service

[Service]
User=newzen
WorkingDirectory=/home/newzen
# prod 프로필 활성화 시 SQL 로깅이 비활성화됨
ExecStart=/opt/java/bin/java -jar /home/newzen/erp-app.jar --spring.profiles.active=prod
Restart=always
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

**2. newzen 계정에 서비스 제어 sudo 권한 부여**

`deploy.sh`/`stop.sh` 스크립트에서 `sudo systemctl`을 사용하므로, 이 권한이 없으면 스크립트가 실행되지 않는다.

```bash
echo "newzen ALL=(ALL) NOPASSWD: /bin/systemctl restart erp, /bin/systemctl stop erp" \
  > /etc/sudoers.d/newzen-erp
chmod 440 /etc/sudoers.d/newzen-erp
```

**3. 시스템 적용**

```bash
systemctl daemon-reload
systemctl enable erp
```

#### Phase 5: 자동화 스크립트 구축 (newzen 권한)

매번 긴 명령어를 치지 않도록 배포 스크립트(`deploy.sh`)와 종료 스크립트(`stop.sh`)를 만든다.

**1. 배포 스크립트 생성 (`vi ~/deploy.sh`)**

```bash
#!/bin/bash
echo "=== systemd 기반 무중단 배포를 시작합니다 ==="

NEW_JAR=$(ls -tr *.jar | grep -v "erp-app.jar" | tail -n 1)
echo "> 실행할 최신 파일: $NEW_JAR"

ln -sf /home/newzen/$NEW_JAR /home/newzen/erp-app.jar

echo "> 구버전 파일 정리를 시작합니다."
for file in *.jar; do
    if [ "$file" != "$NEW_JAR" ] && [ "$file" != "erp-app.jar" ]; then
        rm -f "$file"
    fi
done

echo "> ERP 서비스를 재시작합니다."
sudo systemctl restart erp
echo "=== 배포가 완료되었습니다! ==="
```

**2. 종료 스크립트 생성 (`vi ~/stop.sh`)**

```bash
#!/bin/bash
echo "=== ERP 웹 서비스를 종료합니다 ==="
sudo systemctl stop erp
echo "> 서비스가 안전하게 종료되었습니다."
```

**3. 스크립트 실행 권한 부여**

```bash
chmod +x ~/deploy.sh ~/stop.sh
```

#### 운영 팁

| 작업 | 명령 |
|---|---|
| 새 버전 배포 | FileZilla로 새 `.jar` 업로드 후 `./deploy.sh` |
| 실시간 로그 확인 | `sudo journalctl -u erp -f` |
| 로컬 DB 접속 (HeidiSQL) | `ssh -L 15432:localhost:5432 newzen@서버IP` 후 `localhost:15432`로 접속 |

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
| Scheduling | Spring `@Scheduled` (매 정각 cron, 회사별 timezone 자정 처리) |

---

## 아키텍처 개요

### 멀티테넌시
**Company**가 테넌트 루트. 모든 데이터(Invoice, Contact, Product, Member)는 Company에 종속되며, 쿼리 시 반드시 Company로 필터링한다.

### 인증/인가
Spring Security form login. `Member.email`을 로그인 ID로 사용.

| 경로 | 접근 권한 |
|---|---|
| `/login`, `/signup`, `/api/auth/**`, `/invitations/accept`, `/public/**` | 공개 |
| `/super-admin/**` | SUPER_ADMIN만 |
| `/admin/**` | ADMIN 또는 SUPER_ADMIN |
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
│   ├── PublicController               # 비회원용 공개 인보이스 조회
│   ├── TempDataController          # Product/Contact 목록 조회 및 등록
│   └── GlobalControllerAdvice      # 모든 요청에 회사명/유저 이니셜/통화 기호 주입
│
├── service/          # 비즈니스 로직
│   ├── AuthService                 # 회원가입 처리, 이메일/ABN 중복 확인, 로그인 일시 갱신
│   ├── InvoiceService              # 인보이스 CRUD, 상태변경, 결제, 이메일 발송, 스케줄러(연체)
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
│                               # RecurringFrequency, PlanType, Timezone(ZoneId 매핑), DiscountType
│
├── repository/       # Spring Data JPA 리포지토리
│   ├── InvoiceRepository           # 검색+페이징, 대시보드 집계, 스케줄러 조회, PDF용 JOIN FETCH
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
[USER]  DRAFT ──[Save & Submit]──> IN_REVIEW ──[ADMIN Approve + 이메일 모달]──> UNPAID
[ADMIN] DRAFT ──[Save & Send + 이메일 모달]──> UNPAID

UNPAID ──[결제 완료]──> PAID
UNPAID ──[납기일 초과, 자정 스케줄러]──> OVERDUE

DRAFT, IN_REVIEW ──[Delete]──> DELETED (소프트 삭제, 모든 유저)
UNPAID, OVERDUE  ──[Delete]──> DELETED (소프트 삭제, ADMIN만)
```

**이메일 발송**: 자동 발송 없음. ADMIN이 Save & Send 또는 Approve 시 모달창에서 이메일 주소를 확인하고 **Send**(발송) 또는 **Send Later**(발송 없이 UNPAID 저장)를 선택한다.
인보이스, 초대, 인증코드 이메일은 모두 동일한 브랜드 스타일(파란 헤더, 카드형 레이아웃)로 통일되어 있다.
PDF 첨부 시 `entityManager.clear()` + JOIN FETCH로 Hibernate 1st-level 캐시의 stub Product 문제를 방지한다 (Save & Send 시 form binding이 만든 불완전한 Product 엔티티가 캐시에 잔존하는 문제).

### 반복 인보이스 상태 흐름
```
DRAFT ──[Submit]──> IN_REVIEW ──[Approve]──> ACTIVE ──[자정 스케줄러]──> 인보이스 자동 생성
                                               │        (autoSend=true → UNPAID + 이메일 발송, false → DRAFT)
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

### 통화 (Currency)
`customerCurrency`는 항상 **발행 회사의 통화**로 고정된다. Contact의 통화와 무관하게 인보이스 폼에서 readonly로 표시되며, 생성/자동 발행 시에도 서버에서 회사 통화를 강제 설정한다.

`GlobalControllerAdvice`에서 통화 코드(`globalCompanyCurrency`)와 통화 기호(`globalCurrencySymbol`)를 모든 뷰에 주입한다. 통화 기호는 주요 16개 통화의 정적 매핑 + `java.util.Currency` 폴백으로 변환된다.

### 숫자 포맷 (세 자리 콤마)
인보이스/템플릿 작성 폼의 Price, Quantity, Discount 입력 시 실시간으로 세 자리마다 콤마가 자동 삽입된다 (`type="text" inputmode="decimal"`). 폼 submit 전 콤마를 제거하여 서버 파싱 오류를 방지한다. 조회 화면(view-invoice, view-template)과 PDF, 공개 인보이스에서도 Quantity에 콤마 포맷이 적용된다.

### 고객 정보 스냅샷
Invoice에 `customerName`, `customerEmail`, `customerCompanyName` 등을 별도로 저장.
Contact 정보가 나중에 변경되어도 발행 시점의 정보가 보존된다.

`manualContact` (boolean) 필드로 수동 입력 여부를 구분:
- `false`: Contact 드롭다운에서 선택 (contact 참조 연결됨)
- `true`: 일회성 고객 직접 입력 (contact 참조 없음)

### Timezone 기반 날짜 처리
`Timezone` enum은 UTC-12 ~ UTC+12의 25개 timezone을 정의하며, 각각 `ZoneOffset`을 매핑하여 `toZoneId()`로 `java.time.ZoneId`를 반환한다.

로그인 기록(`lastLoginDate`)과 가입일(`joinedDate`)을 제외한 **모든 `LocalDate.now()` 호출**은 해당 회사의 timezone 기준으로 계산된다.
- 인보이스/템플릿 생성·수정·승인·복사 시 발행일, OVERDUE 판정, 기간별 통계 등
- timezone이 null인 경우 UTC로 폴백

`Member.company`는 `FetchType.EAGER`로 설정되어 있어, 컨트롤러에서 `company.getTimezone()`에 안전하게 접근할 수 있다.

### OVERDUE 처리
납기일 초과 시 OVERDUE 상태 전환은 두 단계에서 이루어진다. 모든 날짜 비교는 회사 timezone 기준.

- **즉시 판정**: 인보이스 생성(`createInvoice`) 또는 수정(`updateInvoice`), 승인(`approveInvoices`, `approveSingleInvoice`) 시 UNPAID로 설정되는 순간 dueDate가 이미 지났다면 즉시 OVERDUE로 저장.
- **정각 스케줄러**: 매 정각 실행, 해당 시각에 자정인 timezone의 회사 인보이스만 처리.

### 스케줄러 (매 정각 실행, timezone별 자정 처리)
매 정각(`0 0 * * * *`)마다 실행되며, 25개 timezone 중 현재 시각이 자정(hour == 0)인 timezone의 회사만 대상으로 처리한다. 서버 시작 시에는 모든 timezone에 대해 일괄 보정(다운타임 보상)을 수행한다.

| 스케줄러 | 위치 | 동작 |
|---|---|---|
| 연체 상태 갱신 | `InvoiceService.updateOverdueInvoices()` | UNPAID → OVERDUE (납기일 초과 시) |
| 반복 인보이스 생성 | `RecurringInvoiceService.generateRecurringInvoices()` | ACTIVE 템플릿 → Invoice 자동 생성 |
| 서버 시작 보정 | `onStartupUpdateOverdue()` / `onStartupGenerateRecurring()` | 모든 timezone 일괄 처리 |

### 결제 처리
`InvoiceService.recordPayment(uuid, amount, company)`:
- `newBalance = currentBalance - paymentAmount`
- `newBalance ≤ 0` → `balanceDue = 0`, `status = PAID`
- `newBalance > 0` → `balanceDue = newBalance`, 기존 상태 유지

### 팀 초대 플로우
1. Admin이 이메일로 초대 → 이미 같은 회사 소속 이메일이면 초대 차단
2. UUID 토큰 생성 (7일 만료), 브랜드 스타일 초대 메일에 `/invitations/accept?token=...` 링크 포함
3. 수락 시 Member.company 연결, role = USER 설정
4. 로그인 성공 핸들러에서 토큰 자동 수락 처리

### 공개 인보이스 조회
`/public/invoice/{uuid}` 경로로 비회원도 인보이스를 열람할 수 있다. DRAFT/DELETED 상태의 인보이스는 접근 차단.
인보이스 이메일 발송 시 이 공개 링크가 포함된다.

---

## 프론트엔드 구조

### 템플릿 (`src/main/resources/templates/`)

| 파일 | 설명 |
|---|---|
| `home.html` | 대시보드 - 탭 필터, 검색, 페이징, 기간별 통계, 일괄 작업 |
| `new-invoice.html` | 인보이스 생성 (invoice-form 프래그먼트 사용) |
| `edit-invoice.html` | 인보이스 수정 (DRAFT만 가능) |
| `view-invoice.html` | 인보이스 상세 조회 (상태 배지, ADMIN용 Approve 버튼 + 이메일 모달) |
| `new-template.html` | 반복 템플릿 생성 (template-form 프래그먼트 사용) |
| `edit-template.html` | 반복 템플릿 수정 |
| `view-template.html` | 반복 템플릿 상세 조회 (상태 배지, Start/Stop 버튼 포함) |
| `public-invoice.html` | 비회원용 인보이스 공개 조회 (nav 없음, 읽기 전용) |
| `login.html` | 로그인 폼 |
| `signup.html` | 다단계 회원가입 (개인정보 → 이메일 인증 → 회사 정보) |
| `subscribe.html` | PayPal 구독 플랜 선택 (BASIC 플랜만 표시) |
| `company-users.html` | Company Admin - 멤버 관리 |
| `super-admin-companies.html` | Super Admin - 전체 회사 목록 |
| `super-admin-company-users.html` | Super Admin - 특정 회사 멤버 조회 |
| `contact-list.html` | Contact 목록 조회 |
| `product-list.html` | Product 목록 조회 |
| `temp-contact.html` | Contact 등록 폼 |
| `temp-product.html` | Product 등록 폼 |

### 프래그먼트 (`templates/fragments/`)

| 파일 | 설명 |
|---|---|
| `nav.html` | 사이드바 + 상단바 (메뉴, 회사명, 유저 이니셜) |
| `super-admin-nav.html` | Super Admin 전용 네비게이션 |
| `invoice-form.html` | 인보이스 입력 폼 (고객 선택/수동입력, 항목 테이블, 금액 계산, 역할별 버튼, 이메일 발송 모달) |
| `template-form.html` | 반복 템플릿 입력 폼 (빈도, 기간, 자동발송 토글) |

### 정적 파일 (`src/main/resources/static/`)

| 파일 | 설명 |
|---|---|
| `css/layout.css` | 전체 레이아웃 (사이드바, 상단바, 콘텐츠 영역) |
| `css/homestyle.css` | 대시보드 (탭, 테이블, 페이징, 통계 카드) |
| `css/newinvoicestyle.css` | 인보이스 폼 (그리드, 항목 테이블, Select2 오버라이드, 모달) |
| `css/viewinvoicestyle.css` | 인보이스 상세 보기 |
| `css/authstyle.css` | 로그인/회원가입 페이지 |
| `js/newinvoicescript.js` | 인보이스 폼 로직 (항목 추가/삭제, 금액 계산, 세 자리 콤마 포맷, 수동 연락처 토글, Select2/Flatpickr 초기화, 이메일 발송 모달) |
| `js/home-script.js` | 대시보드 로직 (일괄 선택, 상태 변경, 복사, 기간 필터, Stop Recurring, PDF 다운로드) |
| `js/signup.js` | 회원가입 다단계 폼 (ABN 조회, 이메일 인증, 유효성 검사) |

---

## 설정 파일

| 파일 | 설명 |
|---|---|
| `application.yaml` | 메인 설정 - DB 연결, JPA(DDL auto=update, SQL 로깅), Gmail SMTP, `app.base-url`(이메일 링크용), prod 프로필 |
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
| POST | `/api/invoices/approve` | 일괄 승인 (IN_REVIEW → UNPAID) |
| POST | `/api/invoices/{uuid}/approve` | 단건 승인 + 선택적 이메일 발송 (view-invoice에서 사용) |
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
| POST | `/api/invitations` | 팀 초대 생성 (기존 멤버 중복 체크) |
| GET | `/invitations/accept` | 초대 수락 |
| GET | `/public/invoice/{uuid}` | 공개 인보이스 조회 (비회원 접근 가능) |
| GET | `/subscribe` | 구독 플랜 페이지 |
| POST | `/api/subscription/success` | PayPal 구독 활성화 |
| GET | `/product` | Product 목록 조회 |
| GET | `/product/new` | Product 등록 폼 |
| POST | `/product` | Product 등록 |
| GET | `/contact` | Contact 목록 조회 |
| GET | `/contact/new` | Contact 등록 폼 |
| POST | `/contact` | Contact 등록 |

---

## 미구현/진행중 기능

- **PDF Export**: 기본 구현 완료 (view-invoice 화면 기반, 디자인 개선 필요)
- **알림 센터**: 상단바 아이콘 비활성
- **Credit Notes**: 버튼만 존재, 로직 없음
- **Payment Link**: 모달 UI 존재, 실제 연동 없음
- **역할별 세분화 권한**: USER/ADMIN별 인보이스 생성 플로우 분리 완료, 추가 세분화 가능
- **Audit Logging**: 상세 감사 추적 미구현
- **Invoice Item 필드**: Invoice 내에 Item들의 필드를 추가할 수 있는 기능은 구현했지만 DB에 필드 구현과 실제로 저장되는건 아직 기본 필드들만
