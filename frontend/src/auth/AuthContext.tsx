import { createContext, useContext, useState, ReactNode } from 'react';
import type { TokenResponse } from '../api/auth';

// sessionStorage 사용 — 브라우저(탭) 종료 시 자격이 자동으로 소멸.
// 새로고침에서는 탭이 유지되므로 세션이 남아있어야 함.
interface AuthState {
  username: string | null;
  role: string | null;
  login: (t: TokenResponse) => void;
  logout: () => void;
}

const Ctx = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  // 초기 state 를 sessionStorage 에서 lazy-init 으로 읽어 첫 렌더부터 올바른 값이 보이게.
  // 그렇지 않으면 ProtectedRoute 가 초기 null 을 보고 /login 으로 리다이렉트해 버림.
  const [username, setUsername] = useState<string | null>(() => sessionStorage.getItem('username'));
  const [role, setRole] = useState<string | null>(() => sessionStorage.getItem('role'));

  const login = (t: TokenResponse) => {
    sessionStorage.setItem('accessToken', t.accessToken);
    sessionStorage.setItem('refreshToken', t.refreshToken);
    sessionStorage.setItem('username', t.username);
    sessionStorage.setItem('role', t.role);
    setUsername(t.username);
    setRole(t.role);
  };
  const logout = () => {
    sessionStorage.removeItem('accessToken');
    sessionStorage.removeItem('refreshToken');
    sessionStorage.removeItem('username');
    sessionStorage.removeItem('role');
    setUsername(null);
    setRole(null);
  };

  return <Ctx.Provider value={{ username, role, login, logout }}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useAuth must be inside AuthProvider');
  return ctx;
}
