# File + Claude 애플리케이션

내부망용 파일 저장 + Claude Code 기반 문서 생성 애플리케이션.

- **앱 호스트 (192.168.31.201)** — Spring Boot 백엔드(+Claude Code CLI 내장) + React 프런트(Nginx)
- **DB 호스트 (192.168.31.203)** — MySQL 8

## 구성

```
.
├── backend/       Spring Boot 3 + JPA + QueryDSL + Security (JWT)
├── frontend/      React + Vite + Tailwind, Nginx 서빙
├── db/            192.168.31.203용 MySQL docker-compose
├── docker-compose.yml   192.168.31.201용 app + frontend
└── .env.example
```

## 실행 순서

### 1) DB 호스트 (192.168.31.203)

```bash
cd db
cp .env.example .env
# MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD 설정
docker compose up -d
```

### 2) 앱 호스트 (192.168.31.201)

```bash
cd /path/to/project
cp .env.example .env
# DB_PASSWORD(=DB의 MYSQL_PASSWORD), JWT_SECRET, ANTHROPIC_API_KEY 설정
docker compose up -d --build
```

- UI: http://192.168.31.201/
- API: http://192.168.31.201/api/...

## 주요 기능

- 회원가입 / 로그인 (JWT)
- hwp, ppt, pptx, xlsx, xls, doc, docx, pdf, csv, md, txt, json 업로드/다운로드
- "문서 생성" 페이지에서 프롬프트만 또는 기존 파일 + 프롬프트로 요청 →
  백엔드 컨테이너 내부의 `claude -p` 가 subprocess로 실행되어 새 파일 생성 →
  자동으로 저장소에 `source=GENERATED` 로 저장
- 목록에서 출처(수동/Claude) 칩으로 구분, 원본 프롬프트 함께 표시

## 검증

계획 문서(`/home/krseo/.claude/plans/tingly-wondering-wren.md`)의 "검증 방법" 섹션 참고.
