import { http } from './http';

export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM';

export interface ConversationResponse {
  id: number;
  title: string;
  messageCount: number;
  claudeSessionId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MessageResponse {
  id: number;
  role: MessageRole;
  content: string;
  toolEventsJson: string | null;
  attachedFileIds: string | null;
  generatedFileIds: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const chatApi = {
  list: (page = 0, size = 30) =>
    http.get<Page<ConversationResponse>>('/chat/conversations', { params: { page, size } }).then((r) => r.data),
  create: (title?: string) =>
    http.post<ConversationResponse>('/chat/conversations', title ? { title } : {}).then((r) => r.data),
  rename: (id: number, title: string) =>
    http.patch<ConversationResponse>(`/chat/conversations/${id}`, { title }).then((r) => r.data),
  remove: (id: number) => http.delete(`/chat/conversations/${id}`),
  messages: (id: number) =>
    http.get<MessageResponse[]>(`/chat/conversations/${id}/messages`).then((r) => r.data),
};

export interface CreatedFile { id: number; originalName: string; }

export interface StreamCallbacks {
  onDelta: (text: string) => void;
  onTool: (event: Record<string, unknown>) => void;
  onDone: (payload: {
    messageId?: number;
    sessionId?: string;
    error?: string;
    timedOut?: boolean;
    createdFiles?: CreatedFile[];
  }) => void;
  onError: (message: string) => void;
}

export async function sendMessageStream(
  conversationId: number,
  content: string,
  attachedFileIds: number[],
  cbs: StreamCallbacks,
  signal?: AbortSignal,
) {
  const token = sessionStorage.getItem('accessToken');
  const res = await fetch(`/api/chat/conversations/${conversationId}/messages`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ content, attachedFileIds }),
    signal,
  });
  if (!res.ok || !res.body) {
    cbs.onError(`HTTP ${res.status}`);
    return;
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let sep;
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const frame = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      parseFrame(frame, cbs);
    }
  }
  if (buffer.trim().length > 0) parseFrame(buffer, cbs);
}

function parseFrame(frame: string, cbs: StreamCallbacks) {
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim();
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
  }
  const data = dataLines.join('\n');
  if (!data) return;
  let payload: any;
  try { payload = JSON.parse(data); } catch { payload = { raw: data }; }
  if (event === 'delta') cbs.onDelta(payload.text ?? '');
  else if (event === 'tool') cbs.onTool(payload);
  else if (event === 'done') cbs.onDone(payload);
  else if (event === 'error') cbs.onError(payload.message ?? 'stream error');
}
