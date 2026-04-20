import { http } from './http';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  username: string;
  role: string;
}

export const authApi = {
  signup: (username: string, password: string, email?: string) =>
    http.post<TokenResponse>('/auth/signup', { username, password, email }).then((r) => r.data),
  login: (username: string, password: string) =>
    http.post<TokenResponse>('/auth/login', { username, password }).then((r) => r.data)
};
