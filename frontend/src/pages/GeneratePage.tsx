import { FormEvent, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { filesApi, FileResponse } from '../api/files';
import { generateApi, JobResponse } from '../api/generate';

export default function GeneratePage() {
  const [prompt, setPrompt] = useState('');
  const [inputs, setInputs] = useState<FileResponse[]>([]);
  const [inputFileId, setInputFileId] = useState<number | ''>('');
  const [job, setJob] = useState<JobResponse | null>(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const pollRef = useRef<number | null>(null);

  useEffect(() => {
    filesApi.list({ page: 0, size: 50 }).then((p) => setInputs(p.content));
  }, []);

  useEffect(() => {
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, []);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true); setErr(''); setJob(null);
    try {
      const j = await generateApi.submit(prompt, inputFileId === '' ? null : inputFileId);
      setJob(j);
      startPolling(j.id);
    } catch (ex: any) {
      setErr(ex.response?.data?.message ?? '요청 실패');
    } finally {
      setBusy(false);
    }
  };

  const startPolling = (id: number) => {
    if (pollRef.current) clearInterval(pollRef.current);
    pollRef.current = window.setInterval(async () => {
      const j = await generateApi.get(id);
      setJob(j);
      if (j.status === 'SUCCEEDED' || j.status === 'FAILED') {
        if (pollRef.current) clearInterval(pollRef.current);
        pollRef.current = null;
      }
    }, 2000);
  };

  return (
    <div className="grid md:grid-cols-2 gap-6">
      <form onSubmit={submit} className="bg-white rounded shadow-sm p-5 space-y-3">
        <h1 className="text-xl font-bold">Claude로 문서 만들기</h1>
        <textarea
          className="w-full border rounded p-3 h-48"
          placeholder={'예) 2024년 분기별 매출 샘플 xlsx로 만들어줘.\n또는: 이 파일을 요약해서 pptx로 만들어줘.'}
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          required
        />
        <label className="block text-sm">
          <span className="text-slate-600">입력 파일 (선택)</span>
          <select className="w-full border rounded px-2 py-1 mt-1" value={inputFileId}
                  onChange={(e) => setInputFileId(e.target.value === '' ? '' : Number(e.target.value))}>
            <option value="">없음</option>
            {inputs.map((f) => (
              <option key={f.id} value={f.id}>{f.originalName}</option>
            ))}
          </select>
        </label>
        {err && <div className="text-red-600 text-sm">{err}</div>}
        <button disabled={busy || !prompt.trim()} className="bg-blue-600 text-white rounded px-4 py-2 disabled:opacity-40">
          {busy ? '요청 중…' : '생성 시작'}
        </button>
      </form>

      <div className="bg-white rounded shadow-sm p-5">
        <h2 className="text-lg font-bold">작업 상태</h2>
        {!job && <div className="text-slate-400 mt-4">아직 제출된 작업이 없습니다.</div>}
        {job && (
          <div className="mt-3 space-y-2 text-sm">
            <div>ID: #{job.id}</div>
            <div>상태: <StatusBadge status={job.status} /></div>
            {job.errorMessage && <div className="text-red-600 whitespace-pre-wrap">{job.errorMessage}</div>}
            {job.outputs.length > 0 && (
              <div>
                <div className="font-semibold mt-3">생성된 파일</div>
                <ul className="list-disc ml-5">
                  {job.outputs.map((o) => (
                    <li key={o.id}>
                      <Link to={`/files/${o.id}`} className="text-slate-800 hover:underline">{o.originalName}</Link>
                      <a href={filesApi.downloadUrl(o.id)} className="ml-2 text-slate-500">[다운로드]</a>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: JobResponse['status'] }) {
  const color = {
    PENDING: 'bg-slate-200 text-slate-700',
    RUNNING: 'bg-amber-100 text-amber-700',
    SUCCEEDED: 'bg-green-100 text-green-700',
    FAILED: 'bg-red-100 text-red-700'
  }[status];
  return <span className={`inline-block text-xs px-2 py-0.5 rounded ${color}`}>{status}</span>;
}
