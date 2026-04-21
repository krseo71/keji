import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function HomePage() {
  const { username } = useAuth();
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] text-center px-4">
      <h1 className="text-5xl md:text-6xl font-light tracking-tight text-slate-900 dark:text-slate-100">
        keji
      </h1>
      {username && (
        <p className="mt-4 text-sm text-slate-500 dark:text-slate-400">
          {username} 님, 환영합니다.
        </p>
      )}
      <nav className="mt-10 flex flex-wrap justify-center gap-6 text-sm">
        <Link to="/files" className="text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white">파일</Link>
        <Link to="/generate" className="text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white">문서 생성</Link>
        <Link to="/chat" className="text-slate-600 dark:text-slate-300 hover:text-slate-900 dark:hover:text-white">대화</Link>
      </nav>
    </div>
  );
}
