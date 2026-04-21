import { useEffect, useState } from 'react';
import { Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import ProtectedRoute from './auth/ProtectedRoute';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import FileListPage from './pages/FileListPage';
import FileUploadPage from './pages/FileUploadPage';
import FileDetailPage from './pages/FileDetailPage';
import GeneratePage from './pages/GeneratePage';
import ChatPage from './pages/ChatPage';

function ThemeToggle() {
  const [dark, setDark] = useState(() =>
    typeof document !== 'undefined' && document.documentElement.classList.contains('dark')
  );
  useEffect(() => {
    if (dark) document.documentElement.classList.add('dark');
    else document.documentElement.classList.remove('dark');
    localStorage.setItem('theme', dark ? 'dark' : 'light');
  }, [dark]);
  return (
    <button
      onClick={() => setDark((v) => !v)}
      className="text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-100 text-sm"
      title={dark ? '라이트 모드' : '다크 모드'}
    >
      {dark ? '☀︎' : '☾'}
    </button>
  );
}

function Header() {
  const { username, logout } = useAuth();
  const nav = useNavigate();
  const onLogout = () => { logout(); nav('/login'); };
  const linkCls = 'text-sm text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white';
  return (
    <header className="border-b border-slate-200 dark:border-slate-800">
      <div className="max-w-5xl mx-auto flex flex-wrap items-center px-4 py-3 gap-3 md:gap-6">
        <Link to="/" className="text-base font-medium tracking-tight text-slate-900 dark:text-slate-100">Home</Link>
        {username && <>
          <Link to="/files" className={linkCls}>파일</Link>
          <Link to="/generate" className={linkCls}>문서 생성</Link>
          <Link to="/chat" className={linkCls}>대화</Link>
          <div className="ml-auto flex items-center gap-4 text-sm">
            <ThemeToggle />
            <span className="text-slate-500 dark:text-slate-400">{username}</span>
            <button onClick={onLogout} className={linkCls}>로그아웃</button>
          </div>
        </>}
        {!username && <div className="ml-auto"><ThemeToggle /></div>}
      </div>
    </header>
  );
}

export default function App() {
  return (
    <div className="min-h-full flex flex-col">
      <Header />
      <main className="max-w-5xl mx-auto w-full px-4 py-8 flex-1">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/files" element={<FileListPage />} />
            <Route path="/upload" element={<FileUploadPage />} />
            <Route path="/files/:id" element={<FileDetailPage />} />
            <Route path="/generate" element={<GeneratePage />} />
            <Route path="/chat" element={<ChatPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
