import { Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import ProtectedRoute from './auth/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import FileListPage from './pages/FileListPage';
import FileUploadPage from './pages/FileUploadPage';
import FileDetailPage from './pages/FileDetailPage';
import GeneratePage from './pages/GeneratePage';

function Header() {
  const { username, logout } = useAuth();
  const nav = useNavigate();
  const onLogout = () => { logout(); nav('/login'); };
  return (
    <header className="bg-white border-b">
      <div className="max-w-5xl mx-auto flex items-center px-4 py-3 gap-4">
        <Link to="/" className="font-bold text-lg">📂 File + Claude</Link>
        {username && <>
          <Link to="/" className="text-slate-600 hover:text-slate-900">목록</Link>
          <Link to="/upload" className="text-slate-600 hover:text-slate-900">업로드</Link>
          <Link to="/generate" className="text-slate-600 hover:text-slate-900">문서생성</Link>
          <div className="ml-auto flex items-center gap-3 text-sm">
            <span className="text-slate-500">{username}</span>
            <button onClick={onLogout} className="text-slate-600 hover:text-slate-900">로그아웃</button>
          </div>
        </>}
      </div>
    </header>
  );
}

export default function App() {
  return (
    <div className="min-h-full flex flex-col">
      <Header />
      <main className="max-w-5xl mx-auto w-full px-4 py-6 flex-1">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<FileListPage />} />
            <Route path="/upload" element={<FileUploadPage />} />
            <Route path="/files/:id" element={<FileDetailPage />} />
            <Route path="/generate" element={<GeneratePage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
