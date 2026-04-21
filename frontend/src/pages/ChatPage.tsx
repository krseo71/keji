import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { chatApi, ConversationResponse, MessageResponse, sendMessageStream } from '../api/chat';
import { filesApi, FileResponse } from '../api/files';

type ToolEvent = {
  kind: 'tool_use_start' | 'tool_result';
  tool?: string;
  tool_use_id?: string;
  summary?: string;
  input?: unknown;
};

interface StreamingState {
  text: string;
  toolEvents: ToolEvent[];
  error?: string;
}

export default function ChatPage() {
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [input, setInput] = useState('');
  const [attached, setAttached] = useState<number[]>([]);
  const [files, setFiles] = useState<FileResponse[]>([]);
  const [showPicker, setShowPicker] = useState(false);
  const [streaming, setStreaming] = useState<StreamingState | null>(null);
  const [sending, setSending] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  const loadConversations = async (selectFirst = false) => {
    const page = await chatApi.list();
    setConversations(page.content);
    if (selectFirst && page.content.length > 0 && activeId == null) {
      setActiveId(page.content[0].id);
    }
  };

  useEffect(() => { loadConversations(true); }, []);

  useEffect(() => {
    if (activeId == null) { setMessages([]); return; }
    chatApi.messages(activeId).then(setMessages);
  }, [activeId]);

  // Load file list once for both picker and resolving names of chat-generated files.
  useEffect(() => {
    filesApi.list({ page: 0, size: 200 }).then((p) => setFiles(p.content)).catch(() => {});
  }, []);

  const fileLookup = useMemo(() => {
    const m = new Map<number, FileResponse>();
    for (const f of files) m.set(f.id, f);
    return m;
  }, [files]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages, streaming?.text]);

  const onNewConversation = async () => {
    const c = await chatApi.create();
    await loadConversations();
    setActiveId(c.id);
    setMessages([]);
  };

  const onDelete = async (id: number) => {
    if (!confirm('대화방을 삭제할까요?')) return;
    await chatApi.remove(id);
    if (activeId === id) setActiveId(null);
    loadConversations();
  };

  const onRename = async (id: number, current: string) => {
    const name = prompt('새 제목', current);
    if (!name || name === current) return;
    await chatApi.rename(id, name);
    loadConversations();
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!input.trim() || sending) return;

    let convId = activeId;
    if (convId == null) {
      const c = await chatApi.create();
      setActiveId(c.id);
      convId = c.id;
      await loadConversations();
    }

    const userMsg: MessageResponse = {
      id: Math.floor(Math.random() * -1e9),
      role: 'USER',
      content: input,
      toolEventsJson: null,
      attachedFileIds: attached.length ? attached.join(',') : null,
      generatedFileIds: null,
      createdAt: new Date().toISOString(),
    };
    setMessages((m) => [...m, userMsg]);
    setStreaming({ text: '', toolEvents: [] });
    setSending(true);
    const sentContent = input;
    const sentAttached = [...attached];
    setInput('');
    setAttached([]);

    abortRef.current = new AbortController();
    await sendMessageStream(
      convId!, sentContent, sentAttached,
      {
        onDelta: (t) => setStreaming((s) => s ? { ...s, text: s.text + t } : null),
        onTool: (evt) => setStreaming((s) => s ? { ...s, toolEvents: [...s.toolEvents, evt as ToolEvent] } : null),
        onDone: async () => {
          setSending(false);
          setStreaming(null);
          if (convId != null) {
            const latest = await chatApi.messages(convId);
            setMessages(latest);
          }
          loadConversations();
          filesApi.list({ page: 0, size: 200 }).then((p) => setFiles(p.content)).catch(() => {});
        },
        onError: (msg) => {
          setStreaming((s) => s ? { ...s, error: msg } : { text: '', toolEvents: [], error: msg });
          setSending(false);
        },
      },
      abortRef.current.signal,
    );
  };

  const toggleAttach = (id: number) => {
    setAttached((cur) => cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]);
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-[240px_1fr] gap-4 md:h-[calc(100vh-140px)]">
      <aside className="bg-white dark:bg-slate-900 rounded border border-slate-200 dark:border-slate-800 flex flex-col overflow-hidden max-h-56 md:max-h-none">
        <div className="p-3 border-b border-slate-200 dark:border-slate-700">
          <button onClick={onNewConversation} className="w-full bg-blue-600 hover:bg-blue-700 text-white rounded py-2 text-sm">새 대화</button>
        </div>
        <ul className="flex-1 overflow-y-auto">
          {conversations.map((c) => (
            <li key={c.id} className={`group px-3 py-2 border-b border-slate-200 dark:border-slate-700 text-sm cursor-pointer ${activeId === c.id ? 'bg-slate-100 dark:bg-slate-700' : 'hover:bg-slate-50 dark:hover:bg-slate-700/60'}`}>
              <div onClick={() => setActiveId(c.id)} className="flex-1 truncate">{c.title}</div>
              <div className="flex gap-2 text-xs text-slate-400 dark:text-slate-500 mt-1">
                <span>{c.messageCount}건</span>
                <button onClick={() => onRename(c.id, c.title)} className="ml-auto hover:text-slate-800 dark:hover:text-slate-100">이름</button>
                <button onClick={() => onDelete(c.id)} className="hover:text-red-600 dark:hover:text-red-400">삭제</button>
              </div>
            </li>
          ))}
          {conversations.length === 0 && (
            <li className="p-4 text-sm text-slate-400 dark:text-slate-500">대화방이 없습니다. 새로 시작해보세요.</li>
          )}
        </ul>
      </aside>

      <section className="bg-white dark:bg-slate-900 rounded border border-slate-200 dark:border-slate-800 flex flex-col overflow-hidden min-h-[60vh] md:min-h-0">
        <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 md:px-6 py-4 space-y-4">
          {messages.length === 0 && !streaming && (
            <div className="text-center text-slate-400 dark:text-slate-500 mt-20">메시지를 입력해 대화를 시작하세요.</div>
          )}
          {messages.map((m) => <MessageView key={m.id} message={m} fileLookup={fileLookup} />)}
          {streaming && (
            <StreamingView text={streaming.text} events={streaming.toolEvents} error={streaming.error} />
          )}
        </div>

        <form onSubmit={onSubmit} className="border-t border-slate-200 dark:border-slate-700 p-3 space-y-2">
          {attached.length > 0 && (
            <div className="flex flex-wrap gap-1 text-xs">
              {attached.map((id) => {
                const f = files.find((x) => x.id === id);
                return (
                  <span key={id} className="bg-slate-100 dark:bg-slate-700 rounded px-2 py-1">
                    {f ? f.originalName : `#${id}`}
                    <button type="button" onClick={() => toggleAttach(id)} className="ml-1 text-slate-400 dark:text-slate-500 hover:text-red-600 dark:hover:text-red-400">×</button>
                  </span>
                );
              })}
            </div>
          )}
          <div className="flex gap-2 items-end">
            <textarea
              className="flex-1 border border-slate-300 dark:border-slate-600 dark:bg-slate-900 rounded p-2 text-sm resize-none"
              rows={2}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="메시지 (Shift+Enter로 전송, Enter로 줄바꿈)"
              onKeyDown={(e) => {
                if (e.key === 'Enter' && e.shiftKey) { e.preventDefault(); onSubmit(e); }
              }}
              disabled={sending}
            />
            <button type="button" onClick={() => setShowPicker((v) => !v)} className="border border-slate-300 dark:border-slate-700 rounded px-3 py-2 text-sm hover:bg-slate-50 dark:hover:bg-slate-800">첨부</button>
            <button type="submit" disabled={sending || !input.trim()} className="bg-blue-600 hover:bg-blue-700 text-white rounded px-4 py-2 text-sm disabled:opacity-40">
              {sending ? '생성중…' : '전송'}
            </button>
          </div>
          {showPicker && (
            <div className="border border-slate-300 dark:border-slate-600 rounded p-2 max-h-48 overflow-y-auto text-sm">
              <div className="text-slate-500 dark:text-slate-400 mb-1">첨부할 파일을 선택</div>
              {files.map((f) => (
                <label key={f.id} className="flex items-center gap-2 py-0.5">
                  <input type="checkbox" checked={attached.includes(f.id)} onChange={() => toggleAttach(f.id)} />
                  <span className="truncate">{f.originalName}</span>
                  <span className="text-xs text-slate-400 dark:text-slate-500 uppercase">{f.extension}</span>
                </label>
              ))}
              {files.length === 0 && <div className="text-slate-400 dark:text-slate-500">업로드된 파일이 없습니다.</div>}
            </div>
          )}
        </form>
      </section>
    </div>
  );
}

function MessageView({ message, fileLookup }: { message: MessageResponse; fileLookup: Map<number, FileResponse> }) {
  const mine = message.role === 'USER';
  let toolEvents: ToolEvent[] = [];
  if (message.toolEventsJson) {
    try { toolEvents = JSON.parse(message.toolEventsJson); } catch { /* ignore */ }
  }
  const genIds = (message.generatedFileIds ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map(Number);
  return (
    <div className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
      <div className={`max-w-[80%] rounded px-4 py-2 ${mine ? 'bg-blue-600 text-white' : 'bg-slate-100 dark:bg-slate-700 text-slate-800 dark:text-slate-100'}`}>
        {toolEvents.length > 0 && !mine && <ToolList events={toolEvents} />}
        <div className="whitespace-pre-wrap text-sm">{message.content}</div>
        {!mine && genIds.length > 0 && (
          <div className="mt-3 pt-2 border-t border-slate-200 dark:border-slate-600 text-xs space-y-1">
            <div className="text-slate-500 dark:text-slate-400">생성된 파일</div>
            <ul className="space-y-1">
              {genIds.map((id) => {
                const f = fileLookup.get(id);
                const name = f ? f.originalName : `파일 #${id}`;
                return (
                  <li key={id} className="flex items-center gap-2">
                    <span className="truncate">{name}</span>
                    <button
                      onClick={() => filesApi.download(id, f?.originalName ?? `file-${id}`)}
                      className="text-blue-600 dark:text-blue-400 hover:underline"
                    >
                      다운로드
                    </button>
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

function StreamingView({ text, events, error }: { text: string; events: ToolEvent[]; error?: string }) {
  return (
    <div className="flex justify-start">
      <div className="max-w-[80%] rounded px-4 py-2 bg-slate-100 dark:bg-slate-700 text-slate-800 dark:text-slate-100">
        {events.length > 0 && <ToolList events={events} />}
        <div className="whitespace-pre-wrap text-sm">
          {text || <span className="text-slate-400 dark:text-slate-500">생각 중…</span>}
          <span className="inline-block w-2 h-4 bg-slate-400 dark:bg-slate-500 animate-pulse ml-0.5 align-middle" />
        </div>
        {error && <div className="text-red-600 dark:text-red-400 text-xs mt-1">⚠ {error}</div>}
      </div>
    </div>
  );
}

function ToolList({ events }: { events: ToolEvent[] }) {
  return (
    <details className="mb-2 text-xs text-slate-500 dark:text-slate-400">
      <summary className="cursor-pointer">🔧 도구 사용 {events.filter(e => e.kind === 'tool_use_start').length}회</summary>
      <ul className="mt-1 space-y-1 ml-2">
        {events.map((e, i) => (
          <li key={i}>
            {e.kind === 'tool_use_start' && <span>→ <b>{e.tool}</b> 호출</span>}
            {e.kind === 'tool_result' && <span className="text-slate-400 dark:text-slate-500">← 결과 {e.summary ? `(${e.summary.slice(0, 60)}${e.summary.length > 60 ? '…' : ''})` : ''}</span>}
          </li>
        ))}
      </ul>
    </details>
  );
}
