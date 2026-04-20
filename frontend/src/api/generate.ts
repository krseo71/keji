import { http } from './http';
import type { FileResponse } from './files';

export type JobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

export interface JobResponse {
  id: number;
  status: JobStatus;
  prompt: string;
  inputFileId: number | null;
  errorMessage: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  outputs: FileResponse[];
}

export const generateApi = {
  submit: (prompt: string, inputFileId?: number | null) =>
    http.post<JobResponse>('/generate', { prompt, inputFileId: inputFileId ?? null }).then((r) => r.data),
  get: (id: number) => http.get<JobResponse>(`/generate/${id}`).then((r) => r.data)
};
