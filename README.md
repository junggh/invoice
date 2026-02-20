# Invoice Management System (Demo)

이 프로젝트는 Spring Boot와 Thymeleaf를 기반으로 한 **인보이스(청구서) 관리 시스템** 데모입니다.  
사용자는 회원가입을 통해 회사를 등록하고, 인보이스를 생성, 발송, 관리할 수 있으며, 정기 결제(Recurring Invoice) 템플릿 기능도 제공합니다.

---

## 🚀 주요 기능

### 1. 회원가입 및 회사 등록 (Onboarding)
- **회원가입**: 이메일 인증 및 ABN(Australian Business Number) 조회를 통한 회사 정보 자동 입력 지원.
- **회사 정보 관리**: 비즈니스 이름, 주소, 연락처, 로고(미구현), 세금 정보(GST) 등을 관리.

### 2. 인보이스 관리 (Invoicing)
- **인보이스 생성/수정**: 고객 선택, 상품 추가, 할인 및 세금(GST) 적용.
- **상태 관리**: Draft(작성중) -> In Review(검토) -> Approved(승인) -> Unpaid(미납) -> Paid(지불완료) / Overdue(연체).
- **PDF/이메일 발송**: (현재 이메일 발송 로직은 껍데기만 존재, 실제 연동 필요).
- **대시보드**: 기간별 매출(Total), 미수금(Balance Due), 연체금(Overdue) 요약 통계 제공.

### 3. 정기 인보이스 (Recurring Invoices)
- **템플릿 생성**: 매주/매월/매년 반복되는 인보이스 템플릿 설정.
- **자동 생성**: 스케줄러가 매일 실행되어 발송 예정일이 된 템플릿을 실제 인보이스로 자동 변환.

### 4. 관리자 기능 (Admin)
- **Super Admin**: 전체 등록된 회사 목록 및 상태 조회.
- **Company Admin**: 소속 회사의 직원(Member) 관리.

---

## 🛠 기술 스택

- **Backend**: Java 17, Spring Boot 3.x, Spring Security, Spring Data JPA
- **Frontend**: Thymeleaf, HTML5, CSS3, JavaScript (jQuery 일부 사용 가능성 있음)
- **Database**: PostgreSQL (Docker Compose 사용)
- **Build Tool**: Gradle
- **Container**: Docker, Docker Compose

---

## ⚙️ 설치 및 실행 가이드

이 프로젝트를 로컬 환경에서 실행하기 위해 다음 단계를 따라주세요.

### 1. 사전 요구사항 (Prerequisites)
- **Java 17** 이상 설치
- **Docker** 및 **Docker Compose** 설치
- **Git** 설치

### 2. 프로젝트 클론
```bash
git clone <repository-url>
cd demo
```

### 3. 환경 변수 및 설정 파일 수정
`src/main/resources/application.yaml` 파일을 열어 본인의 환경에 맞게 수정해야 합니다.

#### (1) 데이터베이스 설정 (Docker 사용 시 기본값 유지 가능)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/invoicedb
    username: newzen  # docker-compose.yml과 일치해야 함
    password: 1234    # docker-compose.yml과 일치해야 함
```

#### (2) 이메일 발송 설정 (Google SMTP 예시)
실제 이메일 발송 기능을 테스트하려면 본인의 구글 앱 비밀번호가 필요합니다.
```yaml
spring:
  mail:
    username: ${mail.user}  # [변경] 본인 구글 이메일 (예: myemail@gmail.com)
    password: ${mail.pass}  # [변경] 구글 앱 비밀번호 16자리
```
> **Tip**: 보안을 위해 실제 비밀번호를 파일에 직접 적지 말고, 환경 변수나 IDE의 Run Configuration에서 설정하는 것을 권장합니다.

#### (3) ABN Lookup API 설정
호주 사업자 번호 조회를 위해 [ABR GUID](https://abr.business.gov.au/)가 필요합니다.
`src/main/java/com/example/demo/service/AbnLookupService.java` 또는 `application.yaml`에 설정을 확인하세요.
```yaml
# application.yaml 예시 (현재 코드에는 @Value로 주입받도록 되어 있음)
abn:
  guid: "YOUR-GUID-HERE"
```

### 4. 데이터베이스 실행 (Docker)
프로젝트 루트 경로에서 다음 명령어를 실행하여 PostgreSQL 컨테이너를 띄웁니다.
```bash
docker-compose up -d
```
- DB가 정상적으로 떴는지 확인: `docker ps`

### 5. 애플리케이션 실행
```bash
# Windows
./gradlew bootRun

# Mac/Linux
./gradlew bootRun
```

### 6. 접속 확인
브라우저를 열고 `http://localhost:8080`으로 접속합니다.

- **초기 개발자 계정 (Super Admin)**
  - ID: `dev@myerp.com`
  - PW: `1234`
  - (서버 시작 시 `InitDataConfig.java`에 의해 자동 생성됨)

---

## 📂 프로젝트 구조

```
src/main/java/com/example/demo
├── config          # Security, 초기 데이터 등 설정
├── controller      # 웹 요청 처리 (View & API)
├── dto             # 데이터 전송 객체
├── entity          # JPA 엔티티 (DB 테이블 매핑)
├── repository      # DB 접근 계층
├── security        # 인증/인가 관련 클래스
└── service         # 비즈니스 로직
```

## 📝 주요 비즈니스 로직 설명

- **인보이스 번호 생성**: `INV-00001` 형식으로 회사별로 시퀀스를 관리하며, 동시성 문제를 최소화하기 위해 DB에서 마지막 번호를 조회하여 +1 하는 방식을 사용합니다.
- **스케줄러 (`@Scheduled`)**:
  - `RecurringInvoiceService`: 매일 자정(`0 0 0 * * *`)에 실행되어, 활성 템플릿 중 예정일이 도래한 건을 찾아 인보이스를 생성합니다.
  - `InvoiceService`: 승인된 인보이스를 발송 처리하거나, 납기일이 지난 인보이스를 연체(Overdue) 상태로 변경합니다.

---

## ⚠️ 주의사항

- **보안**: 현재 `application.yaml`에 민감한 정보(DB 비번, 메일 비번 등)가 노출될 수 있으므로, 실제 운영 배포 시에는 환경 변수(`System.getenv()`)나 Secret Manager를 사용해야 합니다.
- **이메일**: 구글 SMTP 사용 시 "보안 수준이 낮은 앱 액세스" 설정이 불가능하므로, 반드시 **2단계 인증** 설정 후 **앱 비밀번호**를 발급받아 사용해야 합니다.
