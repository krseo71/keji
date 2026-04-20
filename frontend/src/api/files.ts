import { http } from './http';

export type FileSource = 'MANUAL' | 'GENERATED';

export interface FileResponse {
  id: number;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  extension: string;
  source: FileSource;
  generationJobId: number | null;
  description: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const filesApi = {
  list: (params: { page?: number; size?: number; source?: FileSource; extension?: string; keyword?: string } = {}) =>
    http.get<Page<FileResponse>>('/files', { params }).then((r) => r.data),
  upload: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return http.post<FileResponse>('/files', fd, { headers: { 'Content-Type': 'multipart/form-data' } }).then((r) => r.data);
  },
  remove: (id: number) => http.delete(`/files/${id}`),
  downloadUrl: (id: number) => `/api/files/${id}/download`
};
