import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../auth/AuthContext';

export default function SignupPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [err, setErr] = useState('');
  const { login } = useAuth();
  const nav = useNavigate();

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErr('');
    try {
      const t = await authApi.signup(username, password, email || undefined);
      login(t);
      nav('/');
    } catch (ex: any) {
      setErr(ex.response?.data?.message ?? '가입 실패');
    }
  };

  return (
    <div className="max-w-sm mx-auto bg-white dark:bg-slate-900 p-6 rounded border border-slate-200 dark:border-slate-800">
      <h1 className="text-xl font-bold mb-4">가입</h1>
      <form onSubmit={onSubmit} className="space-y-3">
        <input className="w-full border border-slate-300 dark:border-slate-600 dark:bg-slate-900 rounded px-3 py-2" placeholder="아이디 (3자 이상)" value={username} onChange={(e) => setUsername(e.target.value)} />
        <input className="w-full border border-slate-300 dark:border-slate-600 dark:bg-slate-900 rounded px-3 py-2" placeholder="비밀번호 (6자 이상)" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <input className="w-full border border-slate-300 dark:border-slate-600 dark:bg-slate-900 rounded px-3 py-2" placeholder="이메일 (선택)" value={email} onChange={(e) => setEmail(e.target.value)} />
        {err && <div className="text-red-600 dark:text-red-400 text-sm">{err}</div>}
        <button className="w-full bg-slate-800 dark:bg-slate-700 text-white rounded py-2" type="submit">가입</button>
      </form>
      <div className="text-sm text-slate-500 dark:text-slate-400 mt-3">이미 있으시면 <Link to="/login" className="text-slate-800 dark:text-slate-200 underline">로그인</Link></div>
    </div>
  );
}
